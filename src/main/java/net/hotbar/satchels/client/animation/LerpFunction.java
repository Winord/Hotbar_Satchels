package net.hotbar.satchels.client.animation;
@FunctionalInterface
public interface LerpFunction {
    float lerp(float progress, float start, float end);
}
