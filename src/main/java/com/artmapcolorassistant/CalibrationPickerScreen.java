package com.artmapcolorassistant;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class CalibrationPickerScreen extends Screen {
    private static final int VISIBLE_ROWS = 6;

    private final HashCommandHandler commandHandler;
    private final AutoPainter autoPainter;
    private final CalibrationManager calibrationManager;
    private final ConfigManager configManager;
    private final BatchManager batchManager;
    private final Screen parent;
    private List<CalibrationManager.CalibrationFileInfo> calibrations = List.of();
    private TextFieldWidget nameField;
    private int offset;
    private String confirmRestartName;

    public CalibrationPickerScreen(HashCommandHandler commandHandler, AutoPainter autoPainter,
                                   CalibrationManager calibrationManager, ConfigManager configManager,
                                   BatchManager batchManager, Screen parent) {
        super(Text.literal("Choose Calibration"));
        this.commandHandler = commandHandler;
        this.autoPainter = autoPainter;
        this.calibrationManager = calibrationManager;
        this.configManager = configManager;
        this.batchManager = batchManager;
        this.parent = parent;
    }

    @Override
    protected void init() {
        calibrations = calibrationManager.calibrationFiles(configManager.config());
        int buttonHeight = 20;
        int listLeft = width / 2 - 180;
        int y = 42;

        if (confirmRestartName != null) {
            addButton(listLeft, y + 34, 360, buttonHeight,
                    Text.literal("Restart calibration " + confirmRestartName).formatted(Formatting.RED),
                    "Start a fresh unsaved calibration with this completed name.", () -> {
                        String name = confirmRestartName;
                        confirmRestartName = null;
                        run("#painting calibrate start " + name);
                        openMainScreen();
                    });
            addButton(listLeft, y + 58, 360, buttonHeight, Text.literal("Cancel").formatted(Formatting.YELLOW),
                    "Return to the calibration picker without restarting.", () -> {
                        confirmRestartName = null;
                        rebuild();
                    });
            return;
        }

        nameField = new TextFieldWidget(textRenderer, listLeft, y, 220, buttonHeight, Text.literal("Calibration name"));
        nameField.setMaxLength(32);
        nameField.setText("");
        nameField.setPlaceholder(Text.literal("new_calibration_name").formatted(Formatting.DARK_GRAY));
        addDrawableChild(nameField);
        addButton(listLeft + 226, y, 134, buttonHeight, "Create Calibration",
                "Create/select this name and immediately start recording.", this::createCalibration);
        y += 30;

        int end = Math.min(calibrations.size(), offset + VISIBLE_ROWS);
        for (int i = offset; i < end; i++) {
            CalibrationManager.CalibrationFileInfo info = calibrations.get(i);
            int rowY = y + (i - offset) * 26;
            addButton(listLeft + 176, rowY, 54, buttonHeight, "Use", "Load and select " + info.name() + ".",
                    () -> {
                        run("#painting usecalibration " + info.name());
                        openMainScreen();
                    });
            addButton(listLeft + 234, rowY, 68, buttonHeight, "Continue", "Continue recording from saved progress.",
                    () -> {
                        run("#painting calibrate continue " + info.name());
                        openMainScreen();
                    });
            addButton(listLeft + 306, rowY, 54, buttonHeight, "Reset", "Delete this saved calibration.",
                    () -> {
                        run("#painting calibrate reset " + info.name());
                        rebuild();
                    });
        }

        y += VISIBLE_ROWS * 26 + 4;
        addButton(listLeft, y, 78, buttonHeight, "Up", "Show previous calibration files.", () -> {
            offset = Math.max(0, offset - VISIBLE_ROWS);
            rebuild();
        });
        addButton(listLeft + 84, y, 78, buttonHeight, "Down", "Show more calibration files.", () -> {
            offset = Math.min(Math.max(0, calibrations.size() - 1), offset + VISIBLE_ROWS);
            rebuild();
        });
        addButton(listLeft + 168, y, 92, buttonHeight, "Refresh", "Reload calibration files from disk.", this::rebuild);
        addButton(listLeft + 266, y, 94, buttonHeight, "Back", "Return to ArtMap controls.", this::openMainScreen);
    }

    private void createCalibration() {
        String rawName = nameField == null ? "" : nameField.getText();
        if (rawName == null || rawName.isBlank()) {
            sendError(Text.literal("Enter a calibration name first."));
            return;
        }
        String name = ConfigManager.sanitizeCalibrationName(rawName);
        calibrationManager.setLastCalibrationName(name);
        configManager.setSelectedCalibrationName(name, text -> sendError(Text.literal(text.getString())));
        if (calibrationManager.savedExactCompleteForLast(configManager.config())) {
            confirmRestartName = name;
            rebuild();
            return;
        }
        run("#painting calibrate start " + name);
        openMainScreen();
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

    private void rebuild() {
        clearChildren();
        init();
    }

    private void openMainScreen() {
        MinecraftClient.getInstance().setScreen(new PaintingControlScreen(commandHandler, autoPainter, calibrationManager, configManager, batchManager));
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
        };
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x08000000);
        context.fill(width / 2 - 190, 8, width / 2 + 190, 34, 0x66000000);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFF55);
        context.drawCenteredTextWithShadow(textRenderer,
                "Current Calibration: " + calibrationManager.lastCalibrationName(),
                width / 2, 24, 0xDDDDDD);
        if (confirmRestartName != null) {
            context.fill(width / 2 - 190, 52, width / 2 + 190, 66, 0x66000000);
            context.drawCenteredTextWithShadow(textRenderer,
                    "Calibration \"" + confirmRestartName + "\" is already complete. Restart it?",
                    width / 2, 56, 0xFF5555);
        } else {
            int listLeft = width / 2 - 180;
            int y = 74;
            if (calibrations.isEmpty()) {
                context.drawTextWithShadow(textRenderer, "No saved calibration files yet.", listLeft, y, 0xDDDDDD);
            }
            int end = Math.min(calibrations.size(), offset + VISIBLE_ROWS);
            for (int i = offset; i < end; i++) {
                CalibrationManager.CalibrationFileInfo info = calibrations.get(i);
                int rowY = y + (i - offset) * 26;
                int color = info.name().equals(calibrationManager.lastCalibrationName()) ? 0x55FF55 : 0xFFFF55;
                context.fill(listLeft - 4, rowY + 3, listLeft + 172, rowY + 17, 0x55000000);
                context.drawTextWithShadow(textRenderer, info.name(), listLeft, rowY + 6, color);
                context.drawTextWithShadow(textRenderer,
                        info.savedCount() + "/" + info.total() + " " + info.status(),
                        listLeft + 72, rowY + 6, 0xDDDDDD);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void blur() {
    }
}
