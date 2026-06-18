package com.artmapcolorassistant;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

final class PaintingImagePickerScreen extends Screen {
    private static final int SCREEN_DIM = 0x66000000;
    private static final int PANEL_BACKGROUND = 0x52000000;
    private static final int ROW_BACKGROUND = 0x28000000;
    private static final int ROW_HEIGHT = 24;

    private final HashCommandHandler commandHandler;
    private final AutoPainter autoPainter;
    private final ConfigManager configManager;
    private final BatchManager batchManager;
    private final Screen parent;
    private final List<ImageRow> visibleRows = new ArrayList<>();
    private List<String> filenames = List.of();
    private TextFieldWidget firstField;
    private TextFieldWidget lastField;
    private TextFieldWidget suffixField;
    private String firstText = "";
    private String lastText = "";
    private String suffixText = "";
    private String feedback;
    private boolean feedbackError;
    private int offset;
    private int visibleCount;
    private int panelLeft;
    private int panelRight;
    private int listTop;
    private int listBottom;

    PaintingImagePickerScreen(HashCommandHandler commandHandler, AutoPainter autoPainter,
                              ConfigManager configManager, BatchManager batchManager, Screen parent) {
        super(Text.literal("Paint Imported Image"));
        this.commandHandler = commandHandler;
        this.autoPainter = autoPainter;
        this.configManager = configManager;
        this.batchManager = batchManager;
        this.parent = parent;
    }

    @Override
    protected void init() {
        PaintingImageCatalog.Result catalog = PaintingImageCatalog.scan(configManager.importsPath());
        filenames = catalog.filenames();
        if (!catalog.available()) {
            setFeedback(catalog.error(), true);
        }

        int panelWidth = Math.min(520, Math.max(310, width - 16));
        panelLeft = (width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        int innerLeft = panelLeft + 10;
        int innerRight = panelRight - 10;
        int fieldGap = 4;
        int runWidth = Math.min(142, Math.max(112, panelWidth / 4));
        int availableFields = innerRight - innerLeft - runWidth - fieldGap * 3;
        int numberWidth = Math.max(48, availableFields / 5);
        int suffixWidth = availableFields - numberWidth * 2;
        int batchY = 32;

        firstField = textField(innerLeft, batchY, numberWidth, "First", firstText, 8);
        lastField = textField(innerLeft + numberWidth + fieldGap, batchY, numberWidth, "Last", lastText, 8);
        suffixField = textField(innerLeft + (numberWidth + fieldGap) * 2, batchY, suffixWidth,
                "Name suffix", suffixText, 64);
        addButton(innerRight - runWidth, batchY, runWidth, 20,
                Text.literal("Run Batch Painting").formatted(Formatting.GREEN),
                "Start the numbered PNG batch using the selected Auto or Smart mode.", this::runBatch);

        listTop = 68;
        listBottom = Math.max(listTop + ROW_HEIGHT * 3, height - 34);
        visibleCount = Math.max(3, (listBottom - listTop) / ROW_HEIGHT);
        clampOffset();
        visibleRows.clear();
        int end = Math.min(filenames.size(), offset + visibleCount);
        for (int i = offset; i < end; i++) {
            String filename = filenames.get(i);
            int rowY = listTop + (i - offset) * ROW_HEIGHT;
            visibleRows.add(new ImageRow(filename, rowY));
            addButton(innerRight - 72, rowY + 2, 72, 20,
                    Text.literal("Paint").formatted(Formatting.GREEN),
                    "Load " + filename + " and start " + configManager.config().paintingMode().commandName() + " painting.",
                    () -> paint(filename));
        }

        addButton(innerLeft, height - 28, 82, 20, "Refresh", "Reload PNG files from the imports folder.",
                this::refreshCatalog);
        addButton(innerRight - 82, height - 28, 82, 20, "Back", "Return to painting controls.",
                () -> MinecraftClient.getInstance().setScreen(parent));
    }

    private TextFieldWidget textField(int x, int y, int fieldWidth, String placeholder, String value, int maxLength) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, fieldWidth, 20, Text.literal(placeholder));
        field.setMaxLength(maxLength);
        field.setPlaceholder(Text.literal(placeholder).formatted(Formatting.DARK_GRAY));
        field.setText(value);
        addDrawableChild(field);
        return field;
    }

    private void paint(String filename) {
        boolean started = commandHandler.paintNow(filename, sink());
        if (started) {
            close();
            return;
        }
        setFeedback("Could not start " + filename + ". Fix the reported setup issue and try again.", true);
    }

    private void runBatch() {
        captureFieldValues();
        PaintingBatchInput.Validation validation = PaintingBatchInput.validate(
                firstText, lastText, suffixText, configManager.config().paintingMode());
        if (!validation.valid()) {
            setFeedback(validation.error(), true);
            sink().error(validation.error());
            return;
        }
        commandHandler.runLocal(validation.command(), sink());
        if (batchManager.paintingActive()) {
            close();
            return;
        }
        setFeedback("Batch did not begin painting. Fix the reported setup issue and try again.", true);
    }

    private void refreshCatalog() {
        captureFieldValues();
        offset = 0;
        feedback = "Image list refreshed.";
        feedbackError = false;
        rebuild();
    }

    private void captureFieldValues() {
        firstText = firstField == null ? firstText : firstField.getText();
        lastText = lastField == null ? lastText : lastField.getText();
        suffixText = suffixField == null ? suffixText : suffixField.getText();
    }

    private void setFeedback(String message, boolean error) {
        feedback = message;
        feedbackError = error;
    }

    private void rebuild() {
        clearChildren();
        init();
    }

    private void clampOffset() {
        int maxOffset = Math.max(0, filenames.size() - visibleCount);
        offset = Math.max(0, Math.min(offset, maxOffset));
    }

    private void addButton(int x, int y, int buttonWidth, int buttonHeight, String label,
                           String tooltip, Runnable action) {
        addButton(x, y, buttonWidth, buttonHeight, Text.literal(label), tooltip, action);
    }

    private void addButton(int x, int y, int buttonWidth, int buttonHeight, Text label,
                           String tooltip, Runnable action) {
        addDrawableChild(ButtonWidget.builder(label, button -> action.run())
                .dimensions(x, y, buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal(tooltip)))
                .build());
    }

    private SessionController.MessageSink sink() {
        return PaintingScreenMessages.chatSink();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= listTop && mouseY <= listBottom && filenames.size() > visibleCount) {
            captureFieldValues();
            int previous = offset;
            offset += verticalAmount < 0 ? 1 : verticalAmount > 0 ? -1 : 0;
            clampOffset();
            if (offset != previous) {
                rebuild();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, SCREEN_DIM);
        context.fill(panelLeft, 4, panelRight, height - 4, PANEL_BACKGROUND);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFF55);
        context.drawCenteredTextWithShadow(textRenderer,
                "Batch: First | Last | Name suffix", width / 2, 22, 0xCCCCCC);
        int innerLeft = panelLeft + 10;
        int innerRight = panelRight - 10;
        context.drawTextWithShadow(textRenderer,
                "Imported PNGs (" + filenames.size() + ")", innerLeft, 56, 0xFFFF55);
        if (filenames.isEmpty() && feedback == null) {
            context.drawTextWithShadow(textRenderer, "No PNG files found in the imports folder.",
                    innerLeft + 4, listTop + 8, 0xDDDDDD);
        }
        for (ImageRow row : visibleRows) {
            context.fill(innerLeft, row.y(), innerRight, row.y() + 22, ROW_BACKGROUND);
            String displayName = textRenderer.trimToWidth(row.filename(), Math.max(80, innerRight - innerLeft - 88));
            context.drawTextWithShadow(textRenderer, displayName, innerLeft + 6, row.y() + 7, 0xEEEEEE);
        }
        drawScrollbar(context);
        if (feedback != null) {
            int color = feedbackError ? 0xFF7777 : 0x77FF77;
            String display = textRenderer.trimToWidth(feedback, Math.max(120, innerRight - innerLeft - 176));
            context.drawTextWithShadow(textRenderer, display, innerLeft + 88, height - 22, color);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawScrollbar(DrawContext context) {
        if (filenames.size() <= visibleCount) {
            return;
        }
        int trackTop = listTop;
        int trackBottom = Math.min(listBottom, listTop + visibleCount * ROW_HEIGHT - 2);
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(12, trackHeight * visibleCount / filenames.size());
        int maxOffset = filenames.size() - visibleCount;
        int thumbTravel = Math.max(0, trackHeight - thumbHeight);
        int thumbTop = trackTop + (maxOffset == 0 ? 0 : thumbTravel * offset / maxOffset);
        int x = panelRight - 6;
        context.fill(x, trackTop, x + 3, trackBottom, 0x44000000);
        context.fill(x, thumbTop, x + 3, thumbTop + thumbHeight, 0x99CCCCCC);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void blur() {
    }

    private record ImageRow(String filename, int y) {
    }
}
