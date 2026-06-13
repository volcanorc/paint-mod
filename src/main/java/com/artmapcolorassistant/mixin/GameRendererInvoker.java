package com.artmapcolorassistant.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
    @Invoker("updateCrosshairTarget")
    void artmapColorAssistant$updateCrosshairTarget(float tickDelta);
}
