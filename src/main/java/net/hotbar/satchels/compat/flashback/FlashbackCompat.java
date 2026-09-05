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
 *
 * <h3>The problem</h3>
 * Flashback records all {@code ClientboundCustomPayloadPacket}s (including our
 * {@code SatchelSlotUpdatePacketS2C}) and re-delivers them on playback via its
 * {@code FlashbackRawCustomPayload} pipeline, which eventually calls our registered
 * {@code ClientPlayNetworking} receiver normally.
 *
 * The receiver calls {@code SatchelSlotUpdatePacketS2C.handle(packet, mc.level)}, which does:
 * <pre>{@code
 *   Entity entity = clientLevel.getEntity(packet.entityId());
 *   if (entity instanceof Player player && player instanceof IHaveSatchelData data)
 *       data.satchels$getSatchelData().setSatchelSlotStack(packet.stack());
 * }</pre>
 *
 * This silently no-ops when the entity isn't in the client world yet — which happens in two
 * replay scenarios:
 * <ol>
 *   <li><b>Initial load:</b> the {@code SatchelSlotUpdatePacketS2C} packet arrives early in
 *       the replay packet stream, before Flashback has had a chance to spawn the player entity
 *       into the client world. {@code mc.level.getEntity()} returns {@code null} and the
 *       satchel state is silently discarded. The satchel render layer then has nothing to
 *       render.</li>
 *   <li><b>Seeking:</b> Flashback uses snapshots to support seeking. When the viewer scrubs
 *       to a new position, Flashback sends a snapshot plus any custom payloads it collected
 *       during snapshot processing ({@code customPacketsInSnapshot}). The player entity is
 *       re-spawned as part of the snapshot, but the {@code SatchelSlotUpdatePacketS2C} for
 *       the current state may have been emitted well before the snapshot boundary — it does
 *       not automatically re-fire for the current equipped state unless something else
 *       triggers a new equip/unequip event, which doesn't happen during seeking.</li>
 *   <li><b>Recording start:</b> {@code SatchelSlotUpdatePacketS2C} is a one-shot delta —
 *       only ever sent when the equipped stack actually changes, or via
 *       {@code SatchelData#resyncToClient()} on join/respawn/dimension change. Flashback
 *       records the raw packets that cross the connection <i>after</i> recording starts; it
 *       has no way to retroactively capture a packet that already arrived earlier in the
 *       session. So if a satchel was equipped before recording began, that one packet is
 *       simply never in the recording, and playback has nothing to render — until the player
 *       manually unequips/re-equips, which fires a brand new packet that recording does
 *       capture.</li>
 * </ol>
 *
 * <h3>The fix</h3>
 * For the first two (replay-only) scenarios: we intercept the
 * {@code SatchelSlotUpdatePacketS2C} receiver when Flashback is active and park pending
 * updates in a deque. Each client tick we retry: once the entity appears in {@code mc.level},
 * we apply the stack and remove the entry from the deque. Entries time out after
 * {@link #MAX_RETRY_TICKS} ticks to avoid accumulating stale entries if a replay player is
 * removed before the update can be applied.
 * <p>
 * For recording start, there's no historical packet to recover — we need the server to send a
 * fresh one. Each client tick we also poll for the {@code RECORDER} null → non-null transition
 * (see {@link #isRecordingActive()}); the instant it happens (recording just started, and we're
 * connected to a live server, unlike pure replay) we send
 * {@link RequestSatchelResyncPacketC2S}, which asks the server to re-send the requester's own
 * equipped-satchel state plus a fresh packet for every other online player's. Those new packets
 * are sent live, after recording started, so Flashback captures them normally — no retry queue
 * needed for this case.
 *
 * This class is {@code @Environment(EnvType.CLIENT)} — it must only be loaded on the client.
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