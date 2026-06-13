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
    private final HashCommandHandler commandHandler;
    private final AutoPainter autoPainter;
    private final CalibrationManager calibrationManager;
    private final ConfigManager configManager;
    private final BatchManager batchManager;
    private final List<SectionLabel> sectionLabels = new ArrayList<>();
    private boolean confirmCalibrationRestart;
    private boolean needsRefresh;

    public PaintingControlScreen(HashCommandHandler commandHandler, AutoPainter autoPainter,
                                 CalibrationManager calibrationManager, ConfigManager configManager, BatchManager batchManager) {
        super(Text.literal("ArtMap Controls"));
        this.commandHandler = commandHandler;
        this.autoPainter = autoPainter;
        this.calibrationManager = calibrationManager;
        this.configManager = configManager;
        this.batchManager = batchManager;
    }

    @Override
    protected void init() {
        sectionLabels.clear();
        int buttonWidth = 150;
        int buttonHeight = 20;
        int gap = 4;
        int left = width / 2 - buttonWidth - 6;
        int right = width / 2 + 6;
        int y = 34;
        String calibrationName = calibrationManager.lastCalibrationName();

        if (confirmCalibrationRestart) {
            y = addSection("Calibration restart warning", y);
            addButton(left, y, buttonWidth * 2 + 12, buttonHeight,
                    Text.literal("Restart calibration " + calibrationName).formatted(Formatting.RED),
                    "This clears the in-memory recording and starts fresh.", () -> {
                        confirmCalibrationRestart = false;
                        run("#painting calibrate start " + calibrationName);
                    });
            y += buttonHeight + gap;
            addButton(left, y, buttonWidth * 2 + 12, buttonHeight,
                    Text.literal("Cancel").formatted(Formatting.YELLOW),
                    "Return without restarting the completed calibration.", () -> {
                        confirmCalibrationRestart = false;
                        refreshNextRender();
                    });
            return;
        }

        y = addSection("Auto", y);
        addButton(left, y, buttonWidth, buttonHeight, "Start full auto", "Start full auto painting.", "#painting auto full");
        addButton(right, y, buttonWidth, buttonHeight, "Stop auto", "Stop auto painting immediately.", "#painting auto stop");
        y += buttonHeight + gap;
        addButton(left, y, buttonWidth, buttonHeight, "Pause auto", "Pause auto painting.", "#painting auto pause");
        addButton(right, y, buttonWidth, buttonHeight, "Resume auto", "Resume auto painting.", "#painting auto resume");
        y += buttonHeight + gap;
        addButton(left, y, buttonWidth, buttonHeight, "Status", "Show painting and auto status.", "#painting status");
        addButton(right, y, buttonWidth, buttonHeight, "Auto status", "Show current auto state.", "#painting auto status");

        y += buttonHeight + gap + 8;
        y = addSection("Batch", y);
        addButton(left, y, buttonWidth, buttonHeight, "Batch continue", "Start the next numbered batch image after manual setup.", "#painting batch continue");
        addButton(right, y, buttonWidth, buttonHeight, "Batch status", "Show numbered image batch progress.", "#painting batch status");
        y += buttonHeight + gap;
        addButton(left, y, buttonWidth, buttonHeight, "Batch stop", "Stop the current batch queue.", "#painting batch stop");
        addButton(right, y, buttonWidth, buttonHeight, "Batch help", "Show batch command format.", "#painting help");

        y += buttonHeight + gap + 8;
        y = addSection("Drag", y);
        Text dragText = Text.literal("Drag: " + (autoPainter.dragEnabled() ? "ON" : "OFF"))
                .formatted(autoPainter.dragEnabled() ? Formatting.GREEN : Formatting.RED);
        addButton(left, y, buttonWidth, buttonHeight, dragText,
                "Toggle same-color row dragging. Drag mode holds right-click across calibrated same-color pixels in one row.",
                () -> run("#painting auto drag " + (autoPainter.dragEnabled() ? "off" : "on")));
        addButton(right, y, buttonWidth, buttonHeight, "Drag status", "Show auto drag status.", "#painting auto drag status");

        y += buttonHeight + gap + 8;
        y = addSection("Calibration: " + calibrationName, y);
        addButton(left, y, buttonWidth, buttonHeight, Text.literal("Current: " + calibrationName),
                "Choose, use, continue, reset, or create a calibration.", () -> MinecraftClient.getInstance().setScreen(
                        new CalibrationPickerScreen(commandHandler, autoPainter, calibrationManager, configManager, batchManager, this)));
        addButton(right, y, buttonWidth, buttonHeight, "Calibration status", "Show calibration progress.", "#painting calibrate status");
        y += buttonHeight + gap;
        addButton(left, y, buttonWidth, buttonHeight, "Use " + calibrationName, "Load saved calibration '" + calibrationName + "'.", "#painting usecalibration " + calibrationName);
        addButton(right, y, buttonWidth, buttonHeight, "Cal continue " + calibrationName,
                "Continue calibration '" + calibrationName + "' from saved progress.", "#painting calibrate continue " + calibrationName);
        y += buttonHeight + gap;
        addButton(left, y, buttonWidth, buttonHeight, Text.literal("Cal start " + calibrationName),
                "Start a fresh unsaved calibration using the last calibration name.", () -> startCalibrationFromGui(calibrationName));
        addButton(right, y, buttonWidth, buttonHeight, "Cal save " + calibrationName,
                "Save current calibration points to '" + calibrationName + "'.", "#painting calibrate save " + calibrationName);
        y += buttonHeight + gap;
        addButton(left, y, buttonWidth, buttonHeight, "Cal reset " + calibrationName,
                "Delete saved calibration '" + calibrationName + "'.", "#painting calibrate reset " + calibrationName);
        addButton(right, y, buttonWidth, buttonHeight, "Cal stop", "Stop calibration without saving.", "#painting calibrate stop");

        y += buttonHeight + gap + 8;
        y = addSection("Help", y);
        addButton(left, y, buttonWidth, buttonHeight, "Help", "Show clickable command help.", "#painting help");
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                .dimensions(right, y, buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal("Close this client-only control screen.")))
                .build());
    }

    private int addSection(String label, int y) {
        sectionLabels.add(new SectionLabel(label, y));
        return y + 14;
    }

    private void addButton(int x, int y, int width, int height, String label, String tooltip, String command) {
        addButton(x, y, width, height, Text.literal(label), tooltip, () -> run(command));
    }

    private void addButton(int x, int y, int width, int height, Text label, String tooltip, Runnable action) {
        addDrawableChild(ButtonWidget.builder(label, button -> {
                    action.run();
                    refreshNextRender();
                })
                .dimensions(x, y, width, height)
                .tooltip(Tooltip.of(Text.literal(tooltip)))
                .build());
    }

    private void startCalibrationFromGui(String calibrationName) {
        if (calibrationManager.savedExactCompleteForLast(configManager.config())) {
            confirmCalibrationRestart = true;
            refreshNextRender();
            return;
        }
        run("#painting calibrate start " + calibrationName);
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
        return new SessionController.MessageSink() {
            @Override
            public void info(String message) {
                sendInfo(Text.literal(message));
            }

            @Override
            public void error(String message) {
                sendError(Text.literal(message));
            }

            @Override
            public void info(Text message) {
                sendInfo(message);
            }

            @Override
            public void error(Text message) {
                sendError(message);
            }

            private void sendInfo(Text message) {
                send(prefix().append(message.copy().formatted(Formatting.YELLOW)));
            }

            private void sendError(Text message) {
                send(prefix().append(message.copy().formatted(Formatting.RED)));
            }

            private MutableText prefix() {
                return Text.literal("[ArtMap] ").formatted(Formatting.GOLD);
            }

            private void send(MutableText message) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    client.player.sendMessage(message, false);
                }
            }
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (needsRefresh) {
            refreshNow();
        }
        context.fill(0, 0, width, height, 0x08000000);
        context.fill(width / 2 - 180, 8, width / 2 + 180, 34, 0x66000000);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFF55);
        String calibrationName = calibrationManager.lastCalibrationName();
        String autoState = autoPainter.running() ? (autoPainter.paused() ? "paused" : "running") : "stopped";
        String status = "auto=" + autoState
                + " phase=" + autoPainter.phase()
                + " drag=" + (autoPainter.dragEnabled() ? "ON" : "OFF")
                + " cal=" + calibrationName
                + " " + batchManager.statusLine()
                + " saved=" + calibrationManager.savedExactCountForLast(configManager.config()) + "/"
                + (configManager.config().canvasWidth() * configManager.config().canvasHeight());
        context.drawCenteredTextWithShadow(textRenderer, status, width / 2, 24, 0xDDDDDD);
        if (confirmCalibrationRestart) {
            context.fill(width / 2 - 180, 54, width / 2 + 180, 68, 0x66000000);
            context.drawCenteredTextWithShadow(textRenderer,
                    "Calibration \"" + calibrationName + "\" is already complete. Restart it?",
                    width / 2, 58, 0xFF5555);
        }
        for (SectionLabel section : sectionLabels) {
            context.fill(width / 2 - 160, section.y() - 2, width / 2 - 10, section.y() + 11, 0x55000000);
            context.drawTextWithShadow(textRenderer, section.label(), width / 2 - 156, section.y(), 0xFFFF55);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void blur() {
    }

    private record SectionLabel(String label, int y) {
    }
}
