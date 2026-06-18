package com.artmapcolorassistant.mixin;

import com.artmapcolorassistant.ArtMapColorAssistantClient;
import com.artmapcolorassistant.CommandGuide;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.List;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    protected TextFieldWidget chatField;

    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void artmapColorAssistant$cancelHashMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (ArtMapColorAssistantClient.handleLocalHashMessage(message)) {
            ci.cancel();
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void artmapColorAssistant$completePaintingCommand(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (keyCode != GLFW.GLFW_KEY_TAB || chatField == null) {
            return;
        }
        String completion = CommandGuide.firstCompletion(chatField.getText());
        if (completion == null || completion.equals(chatField.getText())) {
            return;
        }
        chatField.setText(completion);
        cir.setReturnValue(true);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void artmapColorAssistant$renderCommandGuide(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        String input = artmapColorAssistant$currentInput();
        List<CommandGuide.Entry> suggestions = CommandGuide.chatSuggestions(input);
        if (suggestions.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        int visible = Math.min(8, suggestions.size());
        int lineHeight = 11;
        int panelHeight = visible * lineHeight + 8;
        int commandWidth = 0;
        int descriptionWidth = 0;
        for (int i = 0; i < visible; i++) {
            CommandGuide.Entry entry = suggestions.get(i);
            commandWidth = Math.max(commandWidth, textRenderer.getWidth(entry.command()));
            descriptionWidth = Math.max(descriptionWidth, textRenderer.getWidth(entry.description()));
        }
        int panelWidth = Math.min(client.getWindow().getScaledWidth() - 8,
                Math.max(180, commandWidth + descriptionWidth + 26));
        int x = 4;
        int y = Math.max(4, client.getWindow().getScaledHeight() - 46 - panelHeight);
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 400.0F);
        try {
            context.fill(x, y, x + panelWidth, y + panelHeight, 0x4D000000);
            int descriptionX = x + 10 + commandWidth;
            int descriptionAvailable = Math.max(0, x + panelWidth - descriptionX - 6);
            for (int i = 0; i < visible; i++) {
                CommandGuide.Entry entry = suggestions.get(i);
                int lineY = y + 4 + i * lineHeight;
                context.drawTextWithShadow(textRenderer, entry.command(), x + 6, lineY,
                        Formatting.YELLOW.getColorValue());
                if (descriptionAvailable > 24) {
                    String description = textRenderer.trimToWidth(entry.description(), descriptionAvailable);
                    context.drawTextWithShadow(textRenderer, description, descriptionX, lineY, 0xEEEEEE);
                }
            }
        } finally {
            context.getMatrices().pop();
        }
    }

    @Unique
    private String artmapColorAssistant$currentInput() {
        if (chatField != null) {
            return chatField.getText();
        }
        try {
            for (Field field : ChatScreen.class.getDeclaredFields()) {
                if (TextFieldWidget.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(this);
                    if (value instanceof TextFieldWidget textField) {
                        return textField.getText();
                    }
                }
            }
        } catch (IllegalAccessException ignored) {
        }
        return "";
    }
}
