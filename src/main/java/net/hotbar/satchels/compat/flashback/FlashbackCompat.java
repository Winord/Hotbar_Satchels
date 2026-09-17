package net.hotbar.satchels.compat.flashback;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.compat.CompatEntrypoint;
import net.hotbar.satchels.content.satchel.IHaveSatchelData;
import net.hotbar.satchels.network.packets.RequestSatchelResyncPacketC2S;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Compat for the Flashback replay mod (https://modrinth.com/mod/flashback).
 * <p>
 * Flashback records/replays raw {@code ClientboundCustomPayloadPacket}s, including our
 * {@code SatchelSlotUpdatePacketS2C}. That packet's normal handler silently no-ops when the
 * target entity isn't in {@code mc.level} yet, which happens in three replay scenarios:
 * <ol>
 *   <li><b>Initial load:</b> the packet arrives before Flashback has spawned the player entity.</li>
 *   <li><b>Seeking:</b> a scrubbed-to snapshot re-spawns the player, but the satchel packet for
 *       the current state may have been emitted well before the snapshot boundary and doesn't
 *       automatically re-fire.</li>
 *   <li><b>Recording start:</b> the satchel packet is a one-shot delta, sent only on
 *       equip/unequip or join/respawn — if it already fired before recording began, Flashback
 *       never captured it at all.</li>
 * </ol>
 * <p>
 * <b>Fix, scenarios 1–2:</b> intercept the receiver while Flashback is active and park pending
 * updates in a deque; retried each client tick until the entity appears in {@code mc.level} or
 * {@link #MAX_RETRY_TICKS} elapses.
 * <p>
 * <b>Fix, scenario 3:</b> poll for the {@code RECORDER} null → non-null transition; the instant
 * recording starts (while still connected to a live server), send
 * {@link RequestSatchelResyncPacketC2S} to ask the server to re-send everyone's current satchel
 * state as fresh live packets, which Flashback then captures normally.
 */
@Environment(EnvType.CLIENT)
public class FlashbackCompat implements CompatEntrypoint {

    /** How many client ticks to keep retrying a pending slot update before giving up. */
    private static final int MAX_RETRY_TICKS = 60;

    /**
     * Queue of slot updates that arrived while the target entity was not yet in
     * {@code mc.level}. Entries are retried each tick and removed once successfully applied
     * or after {@link #MAX_RETRY_TICKS} attempts.
     */
    private static final Deque<PendingSlotUpdate> PENDING = new ArrayDeque<>();

    /**
     * Whether Flashback is currently active (recording or replaying). We check this once per
     * packet — a simple guard so the queue stays empty during normal gameplay.
     * <p>
     * Uses reflection because Flashback is not a compile-time dependency: it lives only in
     * {@code modImplementation} at best, and we deliberately avoid adding it as one to keep
     * the build self-contained. A direct reference would cause a compile error when Flashback
     * is absent from the classpath.
     */
    public static boolean isFlashbackActive() {
        try {
            Class<?> cls = Class.forName("com.moulberry.flashback.Flashback");
            boolean inReplay = (boolean) cls.getMethod("isInReplay").invoke(null);
            if (inReplay) return true;
            return cls.getField("RECORDER").get(null) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Whether Flashback is currently recording (as opposed to replaying). Checked separately
     * from {@link #isFlashbackActive()} because only recording has a live server connection to
     * ask for a resync — during pure replay there's nothing to send {@link
     * RequestSatchelResyncPacketC2S} to.
     */
    private static boolean isRecordingActive() {
        try {
            Class<?> cls = Class.forName("com.moulberry.flashback.Flashback");
            return cls.getField("RECORDER").get(null) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Tracks the previous tick's recording state so we can detect the start of a new recording. */
    private static boolean wasRecording = false;

    /**
     * Enqueues a slot update for deferred application. Called from the
     * {@link SatchelSlotUpdatePacketS2C} receiver when the entity is not yet available.
     */
    public static void enqueueSlotUpdate(int entityId, ItemStack stack) {
        PENDING.add(new PendingSlotUpdate(entityId, stack.copy(), MAX_RETRY_TICKS));
    }

    @Override
    public void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(FlashbackCompat::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        boolean recording = isRecordingActive();
        if (recording && !wasRecording && ClientPlayNetworking.canSend(RequestSatchelResyncPacketC2S.TYPE)) {
            // Recording just started: ask the server for a fresh satchel-state packet (ours,
            // and everyone else's) so it lands inside the recording window instead of having
            // been missed entirely — see the "Recording start" scenario in the class javadoc.
            ClientPlayNetworking.send(new RequestSatchelResyncPacketC2S());
        }
        wasRecording = recording;

        if (PENDING.isEmpty()) return;
        if (client.level == null) return;

        Iterator<PendingSlotUpdate> it = PENDING.iterator();
        while (it.hasNext()) {
            PendingSlotUpdate pending = it.next();

            if (pending.ticksLeft <= 0) {
                it.remove();
                continue;
            }
            pending.ticksLeft--;

            Entity entity = client.level.getEntity(pending.entityId);
            if (entity instanceof Player player && player instanceof IHaveSatchelData data) {
                data.satchels$getSatchelData().setSatchelSlotStack(pending.stack);
                it.remove();
            }
        }
    }

    private static final class PendingSlotUpdate {
        final int entityId;
        final ItemStack stack;
        int ticksLeft;

        PendingSlotUpdate(int entityId, ItemStack stack, int ticksLeft) {
            this.entityId = entityId;
            this.stack = stack;
            this.ticksLeft = ticksLeft;
        }
    }
}