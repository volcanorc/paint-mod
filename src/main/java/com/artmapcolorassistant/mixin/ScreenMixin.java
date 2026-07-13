package com.artmapcolorassistant.mixin;

import com.artmapcolorassistant.ArtMapColorAssistantClient;
import com.artmapcolorassistant.LocalSuggestionMouseHandler;
import net.minecraft.client.gui.Element;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Element.class)
public interface ScreenMixin {
    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void artmapColorAssistant$recordGuiClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LocalSuggestionMouseHandler handler
                && handler.artmapColorAssistant$selectLocalSuggestion(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
        ArtMapColorAssistantClient.recordGuiScreenClick(mouseX, mouseY, button);
    }
}
