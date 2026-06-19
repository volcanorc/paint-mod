package com.artmapcolorassistant;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class PaintingControlScreen extends Screen {
    private static final int SCREEN_DIM = 0x66000000;
    private static final int PANEL_BACKGROUND = 0x52000000;
    private static final int ROW_BACKGROUND = 0x24000000;
    private static final int ACTIVE_GLOW = 0x3055FF55;
    private static final int ENABLED_COLOR = 0x55FF55;
    private static final int DISABLED_COLOR = 0xFF5555;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_STEP = 19;
    private static final int CONTROL_HEIGHT = 16;

    private final HashCommandHandler commandHandler;
    private final AutoPainter autoPainter;
    private final ConfigManager configManager;
    private final BatchManager batchManager;
    private final List<Label> labels = new ArrayList<>();
    private final List<Rectangle> rowBackgrounds = new ArrayList<>();
    private final List<Rectangle> glows = new ArrayList<>();
    private boolean needsRefresh;
    private int panelLeft;
    private int panelRight;
    private int panelBottom;

    public PaintingControlScreen(HashCommandHandler commandHandler, AutoPainter autoPainter,
                                 CalibrationManager calibrationManager, ConfigManager configManager,
                                 BatchManager batchManager) {
        super(Text.literal("ArtMap Painting Controls"));
        this.commandHandler = commandHandler;
        this.autoPainter = autoPainter;
        this.configManager = configManager;
        this.batchManager = batchManager;
    }

    @Override
    protected void init() {
        labels.clear();
        rowBackgrounds.clear();
        glows.clear();

        int panelWidth = Math.min(430, Math.max(300, width - 16));
        panelLeft = (width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        int innerLeft = panelLeft + 10;
        int innerRight = panelRight - 10;
        int y = 22;

        labels.add(new Label("Painting Type", innerLeft, y, 0xFFFF55));
        y += 10;
        addModeButtons(innerLeft, innerRight, y);
        y += 20;

        labels.add(new Label("Features", innerLeft, y, 0xFFFF55));
        y += 10;
        y = addSpeedRow(innerLeft, innerRight, y);
        y = addToggleRow("Painting Auto Drag", autoPainter.dragEnabled(), innerLeft, innerRight, y,
                "Toggle same-color row dragging.",
                "#painting auto drag " + (autoPainter.dragEnabled() ? "off" : "on"));

        ConfigManager.Config config = configManager.config();
        y = addToggleRow("Smart Basecoat", config.smartBaseCoatEnabled(), innerLeft, innerRight, y,
                "Toggle the smart dominant-color base coat.",
                "#painting smart basecoat " + (config.smartBaseCoatEnabled() ? "off" : "on"));
        y = addToggleRow("Painting Bucket", config.bucketEnabled(), innerLeft, innerRight, y,
                "Toggle the guarded initial smart bucket action.",
                "#painting bucket " + (config.bucketEnabled() ? "off" : "on"));
        y = addToggleRow("Painting Post-paint", config.postPaintAutomationEnabled(), innerLeft, innerRight, y,
                "Toggle guarded save, vault, and next-canvas automation.",
                "#painting postpaint " + (config.postPaintAutomationEnabled() ? "off" : "on"));
        y = addStorageRow(innerLeft, innerRight, y, config.postPaintVaultCommand());
        y = addRenameRow(innerLeft, innerRight, y, config.postPaintRenameClickPoint() != null);

        y += 3;
        int gap = 8;
        int buttonWidth = (innerRight - innerLeft - gap) / 2;
        addButton(innerLeft, y, buttonWidth, 18, Text.literal("Paint Now").formatted(Formatting.GREEN),
                "Choose an imported PNG and start the selected painting type.", this::openImagePicker);
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                .dimensions(innerLeft + buttonWidth + gap, y, buttonWidth, 18)
                .tooltip(Tooltip.of(Text.literal("Close this client-only screen.")))
                .build());
        panelBottom = y + 24;
    }

    private void addModeButtons(int left, int right, int y) {
        int gap = 4;
        int buttonWidth = (right - left - gap * 2) / 3;
        PaintingMode selected = configManager.config().paintingMode();
        PaintingMode[] modes = {PaintingMode.MANUAL, PaintingMode.AUTO, PaintingMode.SMART};
        for (int i = 0; i < modes.length; i++) {
            PaintingMode mode = modes[i];
            int x = left + i * (buttonWidth + gap);
            boolean active = selected == mode;
            Text label = Text.literal(mode.commandName().toUpperCase())
                    .formatted(active ? Formatting.GREEN : Formatting.GRAY);
            if (active) {
                glows.add(new Rectangle(x - 1, y - 1, x + buttonWidth + 1, y + 19));
            }
            addButton(x, y, buttonWidth, 18, label,
                    "Use " + mode.commandName() + " painting.",
                    () -> runAndRefresh("#painting set " + mode.commandName()));
        }
    }

    private int addSpeedRow(int left, int right, int y) {
        addRowFrame(left, right, y, "Painting Tick Speed");
        int controlWidth = 30;
        int valueWidth = 52;
        int controlsLeft = right - controlWidth * 2 - valueWidth - 4;
        addButton(controlsLeft, y + 1, controlWidth, CONTROL_HEIGHT, "-", "Decrease delay by one tick.",
                () -> changeSpeed(-1));
        ButtonWidget value = ButtonWidget.builder(
                        Text.literal(Integer.toString(autoPainter.delayTicks())).formatted(Formatting.YELLOW),
                        button -> { })
                .dimensions(controlsLeft + controlWidth + 2, y + 1, valueWidth, CONTROL_HEIGHT)
                .tooltip(Tooltip.of(Text.literal("Current delay in ticks between painted pixels.")))
                .build();
        value.active = false;
        addDrawableChild(value);
        addButton(controlsLeft + controlWidth + valueWidth + 4, y + 1, controlWidth, CONTROL_HEIGHT, "+",
                "Increase delay by one tick.", () -> changeSpeed(1));
        return y + ROW_STEP;
    }

    private int addToggleRow(String label, boolean enabled, int left, int right, int y,
                             String tooltip, String command) {
        addRowFrame(left, right, y, label);
        int buttonWidth = 94;
        int x = right - buttonWidth;
        if (enabled) {
            glows.add(new Rectangle(x - 1, y, right + 1, y + ROW_HEIGHT));
        }
        Text state = Text.literal(enabled ? "ON" : "OFF")
                .formatted(enabled ? Formatting.GREEN : Formatting.RED);
        addButton(x, y + 1, buttonWidth, CONTROL_HEIGHT, state, tooltip, () -> runAndRefresh(command));
        return y + ROW_STEP;
    }

    private int addStorageRow(int left, int right, int y, String configuredCommand) {
        addRowFrame(left, right, y, "Storage Used");
        PlayerVaultSelection selection = PlayerVaultSelection.fromStoredCommand(configuredCommand);
        String displayName = selection == null ? "Custom storage" : selection.displayName();
        String command = selection == null ? configuredCommand : selection.command();
        int buttonWidth = 158;
        ButtonWidget status = ButtonWidget.builder(
                        Text.literal(displayName).formatted(Formatting.AQUA), button -> { })
                .dimensions(right - buttonWidth, y + 1, buttonWidth, CONTROL_HEIGHT)
                .tooltip(Tooltip.of(Text.literal("Change with #painting pv <1-40>. Current command: " + command)))
                .build();
        status.active = false;
        addDrawableChild(status);
        return y + ROW_STEP;
    }

    private int addRenameRow(int left, int right, int y, boolean recorded) {
        addRowFrame(left, right, y, "Painting Rename");
        int buttonWidth = 158;
        int x = right - buttonWidth;
        Text status = Text.literal((recorded ? "Recorded" : "Not recorded") + " - Record New")
                .formatted(recorded ? Formatting.GREEN : Formatting.RED);
        if (recorded) {
            glows.add(new Rectangle(x - 1, y, right + 1, y + ROW_HEIGHT));
        }
        addButton(x, y + 1, buttonWidth, CONTROL_HEIGHT, status,
                "Arm rename click recording, close this screen, then click the target in the ArtMap save GUI.",
                () -> {
                    run("#painting rename click");
                    close();
                });
        return y + ROW_STEP;
    }

    private void addRowFrame(int left, int right, int y, String label) {
        rowBackgrounds.add(new Rectangle(left, y, right, y + ROW_HEIGHT));
        labels.add(new Label(label, left + 6, y + 5, 0xEEEEEE));
    }

    private void changeSpeed(int delta) {
        int minimum = configManager.config().autoPaintMinDelayTicks();
        long candidate = (long) autoPainter.delayTicks() + delta;
        int next = (int) Math.max(minimum, Math.min(Integer.MAX_VALUE, candidate));
        run("#painting auto speed " + next);
        refreshNextRender();
    }

    private void openImagePicker() {
        MinecraftClient.getInstance().setScreen(new PaintingImagePickerScreen(
                commandHandler, autoPainter, configManager, batchManager, this));
    }

    private void addButton(int x, int y, int width, int height, String label, String tooltip, Runnable action) {
        addButton(x, y, width, height, Text.literal(label), tooltip, action);
    }

    private void addButton(int x, int y, int width, int height, Text label, String tooltip, Runnable action) {
        addDrawableChild(ButtonWidget.builder(label, button -> action.run())
                .dimensions(x, y, width, height)
                .tooltip(Tooltip.of(Text.literal(tooltip)))
                .build());
    }

    private void runAndRefresh(String command) {
        run(command);
        refreshNextRender();
    }

    private void refreshNextRender() {
        needsRefresh = true;
    }

    private void refreshNow() {
        needsRefresh = false;
        clearChildren();
        init();
    }

    private void run(String command) {
        commandHandler.runLocal(command, sink());
    }

    private SessionController.MessageSink sink() {
        return PaintingScreenMessages.chatSink();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (needsRefresh) {
            refreshNow();
        }
        context.fill(0, 0, width, height, SCREEN_DIM);
        context.fill(panelLeft, 4, panelRight, panelBottom, PANEL_BACKGROUND);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 11, 0xFFFF55);
        for (Rectangle row : rowBackgrounds) {
            context.fill(row.left(), row.top(), row.right(), row.bottom(), ROW_BACKGROUND);
        }
        for (Rectangle glow : glows) {
            context.fill(glow.left(), glow.top(), glow.right(), glow.bottom(), ACTIVE_GLOW);
        }
        for (Label label : labels) {
            context.drawTextWithShadow(textRenderer, label.text(), label.x(), label.y(), label.color());
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void blur() {
    }

    private record Label(String text, int x, int y, int color) {
    }

    private record Rectangle(int left, int top, int right, int bottom) {
    }
}
