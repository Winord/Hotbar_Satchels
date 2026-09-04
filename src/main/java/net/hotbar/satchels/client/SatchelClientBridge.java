package net.hotbar.satchels.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.hotbar.satchels.network.packets.ToggleSatchelPacketC2S;

public final class SatchelClientBridge {
    private SatchelClientBridge() {}

    public static void sendToggleOff() {
        ClientPlayNetworking.send(new ToggleSatchelPacketC2S(false));
    }
}