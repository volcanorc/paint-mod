package com.artmapcolorassistant.mixin;

import com.artmapcolorassistant.ArtMapColorAssistantClient;
import com.artmapcolorassistant.CommandGuide;
import com.artmapcolorassistant.LocalSuggestionSession;
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
public abstract class ChatScreenMixin implements LocalSuggestionMouseHandler {
    @Unique
    private static final int ARTMAP_VISIBLE_ROWS = 8;
    @Unique
    private static final int ARTMAP_PANEL_BACKGROUND = 0xCC000000;

    @Shadow
    protected TextFieldWidget chatField;

    @Unique
    private final LocalSuggestionSession artmapColorAssistant$suggestionSession = new LocalSuggestionSession();
    @Unique
    private int artmapColorAssistant$panelX;
    @Unique
    private int artmapColorAssistant$panelY;
    @Unique
    private int artmapColorAssistant$panelWidth;
    @Unique
    private int artmapColorAssistant$lineHeight;
    @Unique
    private int artmapColorAssistant$visibleCount;

    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void artmapColorAssistant$cancelHashMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (ArtMapColorAssistantClient.handleLocalHashMessage(message)) {
            ci.cancel();
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void artmapColorAssistant$completePaintingCommand(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (chatField == null || (keyCode != GLFW.GLFW_KEY_TAB
                && keyCode != GLFW.GLFW_KEY_UP && keyCode != GLFW.GLFW_KEY_DOWN)) {
            return;
        }
        String input = chatField.getText();
        artmapColorAssistant$suggestionSession.sync(input, CommandGuide.chatSuggestions(input));
        if (artmapColorAssistant$suggestionSession.candidates().isEmpty()) {
            return;
        }
        int direction = keyCode == GLFW.GLFW_KEY_UP
                || (keyCode == GLFW.GLFW_KEY_TAB && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) ? -1 : 1;
        CommandGuide.Completion completion = artmapColorAssistant$suggestionSession.cycle(direction, ARTMAP_VISIBLE_ROWS);
        if (completion != null) {
            artmapColorAssistant$applyCompletion(completion);
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void artmapColorAssistant$renderCommandGuide(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        String input = artmapColorAssistant$currentInput();
        artmapColorAssistant$suggestionSession.sync(input, CommandGuide.chatSuggestions(input));
        List<CommandGuide.Completion> suggestions = artmapColorAssistant$suggestionSession.visible(ARTMAP_VISIBLE_ROWS);
        if (suggestions.isEmpty()) {
            artmapColorAssistant$visibleCount = 0;
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        int visible = suggestions.size();
        int lineHeight = 11;
        int panelHeight = visible * lineHeight + 8;
        int commandWidth = 0;
        int descriptionWidth = 0;
        for (int i = 0; i < visible; i++) {
            CommandGuide.Completion entry = suggestions.get(i);
            commandWidth = Math.max(commandWidth, textRenderer.getWidth(entry.display()));
            descriptionWidth = Math.max(descriptionWidth, textRenderer.getWidth(entry.description()));
        }
        int panelWidth = Math.min(client.getWindow().getScaledWidth() - 8,
                Math.max(180, commandWidth + descriptionWidth + 26));
        int x = 4;
        int y = Math.max(4, client.getWindow().getScaledHeight() - 46 - panelHeight);
        artmapColorAssistant$panelX = x;
        artmapColorAssistant$panelY = y;
        artmapColorAssistant$panelWidth = panelWidth;
        artmapColorAssistant$lineHeight = lineHeight;
        artmapColorAssistant$visibleCount = visible;
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 400.0F);
        try {
            context.fill(x, y, x + panelWidth, y + panelHeight, ARTMAP_PANEL_BACKGROUND);
            int descriptionX = x + 10 + commandWidth;
            int descriptionAvailable = Math.max(0, x + panelWidth - descriptionX - 6);
            for (int i = 0; i < visible; i++) {
                CommandGuide.Completion entry = suggestions.get(i);
                int lineY = y + 4 + i * lineHeight;
                int absoluteIndex = artmapColorAssistant$suggestionSession.viewportStart() + i;
                boolean selected = absoluteIndex == artmapColorAssistant$suggestionSession.selectedIndex();
                boolean hovered = mouseX >= x && mouseX < x + panelWidth
                        && mouseY >= lineY - 2 && mouseY < lineY + lineHeight - 2;
                if (selected || hovered) {
                    context.fill(x + 2, lineY - 2, x + panelWidth - 2, lineY + lineHeight - 2,
                            selected ? 0x80555555 : 0x50444444);
                }
                context.drawTextWithShadow(textRenderer, entry.display(), x + 6, lineY,
                        entry.insertable() ? Formatting.YELLOW.getColorValue() : Formatting.GRAY.getColorValue());
                if (descriptionAvailable > 24) {
                    String description = textRenderer.trimToWidth(entry.description(), descriptionAvailable);
                    context.drawTextWithShadow(textRenderer, description, descriptionX, lineY, 0xEEEEEE);
                }
            }
        } finally {
            context.getMatrices().pop();
        }
    }

    @Override
    public boolean artmapColorAssistant$selectLocalSuggestion(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || chatField == null || artmapColorAssistant$visibleCount <= 0
                || mouseX < artmapColorAssistant$panelX
                || mouseX >= artmapColorAssistant$panelX + artmapColorAssistant$panelWidth
                || mouseY < artmapColorAssistant$panelY + 4) {
            return false;
        }
        int row = (int) ((mouseY - artmapColorAssistant$panelY - 4) / artmapColorAssistant$lineHeight);
        if (row < 0 || row >= artmapColorAssistant$visibleCount) {
            return false;
        }
        int absoluteIndex = artmapColorAssistant$suggestionSession.viewportStart() + row;
        CommandGuide.Completion completion = artmapColorAssistant$suggestionSession.select(
                absoluteIndex, ARTMAP_VISIBLE_ROWS);
        if (completion == null) {
            return false;
        }
        artmapColorAssistant$applyCompletion(completion);
        return true;
    }

    @Unique
    private void artmapColorAssistant$applyCompletion(CommandGuide.Completion completion) {
        chatField.setText(completion.insertion());
        artmapColorAssistant$suggestionSession.markApplied(completion.insertion());
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
