package com.artmapcolorassistant.mixin;

import com.artmapcolorassistant.ArtMapColorAssistantClient;
import net.minecraft.client.gui.Element;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Element.class)
public abstract class ScreenMixin {
    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"))
    private void artmapColorAssistant$recordGuiClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        ArtMapColorAssistantClient.recordGuiScreenClick(mouseX, mouseY, button);
    }
}
