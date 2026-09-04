package net.hotbar.satchels.client.animation;
public class LerpHelper {
    public static float getProgress(long currentTime, long startTime, long endTime) {
        return Math.clamp((float) (currentTime - startTime) / (endTime - startTime), 0, 1);
    }
}
