package com.artmapcolorassistant.mixin;

import com.artmapcolorassistant.ArtMapColorAssistantClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void artmapColorAssistant$cancelPaintingCommand(String message, boolean addToHistory, CallbackInfo ci) {
        if (ArtMapColorAssistantClient.handleLocalPaintingCommand(message)) {
            ci.cancel();
        }
    }
}
