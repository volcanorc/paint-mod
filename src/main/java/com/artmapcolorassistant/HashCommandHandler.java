package com.artmapcolorassistant;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

public final class HashCommandHandler {
    private final ConfigManager configManager;
    private final SessionController controller;
    private final AutoPainter autoPainter;
    private final SmartPainter smartPainter;
    private final CalibrationManager calibrationManager;
    private final BatchManager batchManager;
    private final GuiClickRecorder guiClickRecorder;
    private boolean confirmMode;

    public HashCommandHandler(ConfigManager configManager, SessionController controller, AutoPainter autoPainter,
                              SmartPainter smartPainter,
                              CalibrationManager calibrationManager, BatchManager batchManager,
                              GuiClickRecorder guiClickRecorder) {
        this.configManager = configManager;
        this.controller = controller;
        this.autoPainter = autoPainter;
        this.smartPainter = smartPainter;
        this.calibrationManager = calibrationManager;
        this.batchManager = batchManager;
        this.guiClickRecorder = guiClickRecorder;
        this.confirmMode = configManager.config().confirmMode();
    }

    public boolean confirmMode() {
        return confirmMode;
    }

    public static boolean isPaintingCommand(String raw) {
        return HashMessagePolicy.classify(raw) == HashMessagePolicy.Classification.PAINTING_COMMAND;
    }

    public static boolean isHashPrefixedMessage(String raw) {
        return HashMessagePolicy.classify(raw) != HashMessagePolicy.Classification.NORMAL_CHAT;
    }

    public boolean handleHashMessage(String raw, SessionController.MessageSink sink) {
        HashMessagePolicy.Classification classification = HashMessagePolicy.classify(raw);
        if (classification == HashMessagePolicy.Classification.NORMAL_CHAT) {
            return false;
        }
        if (classification == HashMessagePolicy.Classification.PAINTING_COMMAND) {
            handle(raw, sink);
            return true;
        }
        sink.error("That is not a correct ArtMap command. It was blocked and not sent to the server.");
        help(sink);
        return true;
    }

    public void handle(String raw, SessionController.MessageSink sink) {
        String trimmed = raw == null ? "" : raw.trim();
        if (!isPaintingCommand(trimmed)) {
            return;
        }
        String prefix = trimmed.startsWith("#painting") ? "#painting" : "#paint";
        String rest = trimmed.substring(prefix.length()).trim();
        if (rest.isBlank()) {
            usage(sink);
            return;
        }
        String[] parts = rest.split("\\s+");
        String command = parts[0].toLowerCase();
        switch (command) {
            case "dryrun" -> {
                if (parts.length != 2) {
                    sink.error("Usage: #painting dryrun <filename.png>");
                } else {
                    controller.dryrun(parts[1], sink);
                }
            }
            case "stop" -> {
                if (batchManager.active()) {
                    batchManager.stop(sink);
                }
                if (autoPainter.running()) {
                    autoPainter.stop(sink);
                }
                if (smartPainter.running()) {
                    smartPainter.stop(sink);
                }
                controller.stop(sink);
            }
            case "pause" -> {
                if (smartPainter.running()) {
                    smartPainter.invalidateTrust("manual pause");
                    smartPainter.pause(sink);
                }
                controller.pause(sink);
            }
            case "resume" -> {
                smartPainter.resume(sink);
                controller.resume(sink);
            }
            case "back" -> {
                if (smartPainter.running()) {
                    smartPainter.invalidateTrust("back command");
                }
                controller.back(sink);
            }
            case "skip" -> {
                if (smartPainter.running()) {
                    smartPainter.invalidateTrust("skip command");
                }
                controller.skip(sink);
            }
            case "status" -> paintingStatus(sink);
            case "help" -> help(sink);
            case "gui" -> openGui(sink);
            case "paths" -> paths(sink);
            case "set" -> handleSet(parts, sink);
            case "android" -> handleAndroid(parts, sink);
            case "calibration" -> handleCalibrationPortable(parts, sink);
            case "full" -> startAutoOrSmart(sink);
            case "reload" -> {
                configManager.load(text -> sink.error(text.getString()));
                confirmMode = configManager.config().confirmMode();
                autoPainter.refreshConfig(configManager.config());
                sink.info("Config reloaded.");
                for (String warning : configManager.warnings()) {
                    sink.error(warning);
                }
            }
            case "goto" -> handleGoto(parts, sink);
            case "pos" -> handlePos(parts, sink);
            case "confirm" -> handleConfirm(parts, sink);
            case "auto" -> handleAuto(parts, sink);
            case "smart" -> handleSmart(parts, sink);
            case "bucket" -> handleBucket(parts, sink);
            case "palette" -> handlePalette(parts, sink);
            case "batch" -> handleBatch(parts, sink);
            case "postpaint" -> handlePostPaint(parts, sink);
            case "rename" -> handleRename(parts, sink);
            case "pv" -> handlePlayerVault(parts, sink);
            case "pv2" -> handlePv2(parts, sink);
            case "cal" -> handleCalibration(parts, sink);
            case "calibrate" -> handleExactCalibration(parts, sink);
            case "usecalibration" -> handleUseCalibration(parts, sink);
            default -> {
                if (PlayerVaultSelection.looksCompact(command)) {
                    handleCompactPlayerVault(command, parts, sink);
                } else if (parts.length == 1 && command.endsWith(".png")) {
                    startImage(parts[0], sink);
                } else {
                    usage(sink);
                }
            }
        }
    }

    private void handleSet(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 2) {
            sink.error("Usage: #painting set manual|auto|smart");
            return;
        }
        PaintingMode mode = switch (parts[1].toLowerCase(Locale.ROOT)) {
            case "manual" -> PaintingMode.MANUAL;
            case "auto" -> PaintingMode.AUTO;
            case "smart" -> PaintingMode.SMART;
            default -> null;
        };
        if (mode == null) {
            sink.error("Usage: #painting set manual|auto|smart");
            return;
        }
        applyPaintingMode(mode, sink);
    }

    private void applyPaintingMode(PaintingMode mode, SessionController.MessageSink sink) {
        configManager.setPaintingMode(mode, text -> sink.error(text.getString()));
        ConfigManager.Config config = configManager.config();
        if (config.useBundledDirectionalCalibration() && config.autoDetectCalibrationDirectionOnAutoStart()) {
            sink.info("Built-in directional calibration " + config.defaultBundledCalibrationPrefix()
                    + "_<direction> will load when auto painting starts.");
        } else {
            calibrationManager.loadExact(config.selectedCalibrationName(), config, sink);
        }
        autoPainter.setSpeed(5, config, sink);
        autoPainter.setDragEnabled(config.autoDragSameColorRuns(), sink);
        switch (mode) {
            case MANUAL -> {
                if (smartPainter.running()) {
                    smartPainter.stop(sink);
                }
                if (autoPainter.running()) {
                    autoPainter.stop(sink);
                }
            }
            case AUTO -> {
                if (smartPainter.running()) {
                    smartPainter.stop(sink);
                }
            }
            case SMART -> {
                if (autoPainter.running()) {
                    autoPainter.stop(sink);
                    sink.info("Stopped old auto painter. Run #painting auto start to begin smart painting.");
                }
            }
        }
        sink.info("Painting type set to " + mode.commandName() + ".");
    }

    private void handlePostPaint(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 2) {
            sink.error("Usage: #painting postpaint on|off|status");
            return;
        }
        switch (parts[1].toLowerCase()) {
            case "on" -> {
                configManager.setPostPaintAutomationEnabled(true, text -> sink.error(text.getString()));
                sink.info("Post-paint automation enabled.");
            }
            case "off" -> {
                configManager.setPostPaintAutomationEnabled(false, text -> sink.error(text.getString()));
                sink.info("Post-paint automation disabled.");
            }
            case "status" -> postPaintStatus(sink);
            default -> sink.error("Usage: #painting postpaint on|off|status");
        }
    }

    private void handleRename(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 2) {
            sink.error("Usage: #painting rename click|clear");
            return;
        }
        switch (parts[1].toLowerCase()) {
            case "click" -> guiClickRecorder.armRename(sink);
            case "clear" -> guiClickRecorder.clearRename(sink);
            default -> sink.error("Usage: #painting rename click|clear");
        }
    }

    private void handlePv2(String[] parts, SessionController.MessageSink sink) {
        if (parts.length == 1) {
            applyPlayerVault(new PlayerVaultSelection(2), sink);
            return;
        }
        if (parts.length != 2) {
            sink.error("Usage: #painting pv2 click|clear (legacy Player Vault 2 recorder only; normal transfer is automatic)");
            return;
        }
        switch (parts[1].toLowerCase()) {
            case "click" -> {
                guiClickRecorder.armPv2(sink);
                sink.info("Player Vault 2 click recording is legacy only. Normal post-paint transfers hotbar slot 1 automatically.");
            }
            case "clear" -> guiClickRecorder.clearPv2(sink);
            default -> sink.error("Usage: #painting pv2 click|clear (legacy Player Vault 2 recorder only; normal transfer is automatic)");
        }
    }

    private void handlePlayerVault(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 2) {
            playerVaultUsage(sink);
            return;
        }
        PlayerVaultSelection.ParseResult result = PlayerVaultSelection.parseNumber(parts[1]);
        if (!result.valid()) {
            sink.error(result.error());
            playerVaultUsage(sink);
            return;
        }
        applyPlayerVault(result.selection(), sink);
    }

    private void handleCompactPlayerVault(String command, String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 1) {
            playerVaultUsage(sink);
            return;
        }
        PlayerVaultSelection.ParseResult result = PlayerVaultSelection.parseCompact(command);
        if (!result.valid()) {
            sink.error(result.error());
            playerVaultUsage(sink);
            return;
        }
        applyPlayerVault(result.selection(), sink);
    }

    private void applyPlayerVault(PlayerVaultSelection selection, SessionController.MessageSink sink) {
        if (configManager.setPostPaintVault(selection, text -> sink.error(text.getString()))) {
            sink.info("Finished-canvas storage changed to " + selection.displayName() + " (" + selection.command()
                    + "). This setting is saved and will remain after restarting Minecraft.");
        }
    }

    private void playerVaultUsage(SessionController.MessageSink sink) {
        sink.error("Incorrect Player Vault command. To change finished-canvas storage, use #painting pv <1-40> "
                + "(example: #painting pv 3). Compact commands such as #painting pv3 also work.");
    }

    private void postPaintStatus(SessionController.MessageSink sink) {
        ConfigManager.Config config = configManager.config();
        sink.info("Post-paint automation=" + config.postPaintAutomationEnabled()
                + " saveSlot=" + (config.postPaintSaveHotbarSlot() + 1)
                + " finishedSlot=" + (config.postPaintFinishedHotbarSlot() + 1)
                + " blankSlot=" + (config.postPaintBlankCanvasHotbarSlot() + 1)
                + " aimIndex=" + config.postPaintAimCalibrationIndex()
                + " storage=\"" + PlayerVaultSelection.displayName(config.postPaintVaultCommand()) + "\""
                + " vaultCommand=\"" + config.postPaintVaultCommand() + "\""
                + " renamePoint=" + clickPointStatus(config.postPaintRenameClickPoint())
                + " playerVaultTransfer=automatic-slot-based"
                + " " + batchManager.statusLine() + ".");
    }

    private String clickPointStatus(RecordedClickPoint point) {
        return point == null ? "false" : "true(" + point.source() + ")";
    }

    private void paintingStatus(SessionController.MessageSink sink) {
        ConfigManager.Config config = configManager.config();
        PaintingMode mode = config.paintingMode();
        sink.info("painting type - " + mode.commandName() + " [OK]");
        switch (mode) {
            case MANUAL -> {
                sink.info(readiness("manual assisted item swap - on", config.autoSwapFromInventory()));
                sink.info(readiness("advance after click - on", config.advanceOnLeftClick() || config.advanceOnRightClick()));
                sink.info(calibrationReadiness(config));
                sink.info(readiness("auto swap from inventory", config.autoSwapFromInventory()));
                sink.info(readiness("images in folder - " + imageCount(), imageCount() > 0));
            }
            case AUTO -> {
                sink.info(readiness("auto speed " + autoPainter.delayTicks(), autoPainter.delayTicks() == 5));
                sink.info(readiness("auto drag on", autoPainter.dragEnabled()));
                sink.info(readiness("auto swap from inventory", config.autoSwapFromInventory()));
                sink.info(calibrationReadiness(config));
                sink.info(readiness("postpaint on", config.postPaintAutomationEnabled()));
                sink.info(readiness("recorded rename click", config.postPaintRenameClickPoint() != null));
                sink.info(readiness("storage " + PlayerVaultSelection.displayName(config.postPaintVaultCommand()), true));
                sink.info(readiness("player vault transfer automatic slot-based", true));
                sink.info(readiness("images in folder - " + imageCount(), imageCount() > 0));
                sink.info(readiness("smart off", !config.smartEnabled()));
                sink.info(readiness("bucket off", !config.bucketEnabled()));
                sink.info(batchManager.statusLine());
            }
            case SMART -> {
                sink.info(readiness("smart on", config.smartEnabled()));
                sink.info(readiness("bucket on", config.bucketEnabled()));
                sink.info(readiness("offhand empty bucket", controller.exactEmptyBucketInOffhand()));
                sink.info(readiness("basecoat on", config.smartBaseCoatEnabled()));
                sink.info(readiness("auto speed " + autoPainter.delayTicks(), autoPainter.delayTicks() == 5));
                sink.info(readiness("auto drag fallback on", autoPainter.dragEnabled()));
                sink.info(readiness("auto swap from inventory", config.autoSwapFromInventory()));
                sink.info(calibrationReadiness(config));
                sink.info(readiness("postpaint on", config.postPaintAutomationEnabled()));
                sink.info(readiness("recorded rename click", config.postPaintRenameClickPoint() != null));
                sink.info(readiness("storage " + PlayerVaultSelection.displayName(config.postPaintVaultCommand()), true));
                sink.info(readiness("player vault transfer automatic slot-based", true));
                sink.info(readiness("images in folder - " + imageCount(), imageCount() > 0));
                sink.info(batchManager.statusLine());
                sink.info(smartPainter.status(config));
            }
        }
        controller.status(sink);
    }

    private String readiness(String label, boolean ready) {
        return label + (ready ? " [OK]" : " [MISSING]");
    }

    private String calibrationReadiness(ConfigManager.Config config) {
        String loaded = calibrationManager.calibration().loadedExactName();
        boolean bundledSelected = loaded != null
                && loaded.startsWith(config.defaultBundledCalibrationPrefix() + "_")
                && "bundled".equals(calibrationManager.activeCalibrationSourceLabel());
        boolean selectedLoaded = loaded != null && (loaded.equals(config.selectedCalibrationName()) || bundledSelected);
        String loadedText = loaded == null ? "none" : loaded;
        return readiness("selected calibration " + config.selectedCalibrationName()
                + " loaded exact " + loadedText
                + " source " + calibrationManager.activeCalibrationSourceLabel()
                + " direction " + calibrationManager.activeCalibrationDirectionLabel(),
                selectedLoaded && calibrationManager.hasUsableCalibration(config));
    }

    private long imageCount() {
        try (Stream<Path> paths = Files.list(configManager.importsPath())) {
            return paths.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).count();
        } catch (IOException e) {
            return 0L;
        }
    }

    private void handleAndroid(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 2) {
            sink.error("Usage: #painting android status|testinput");
            return;
        }
        switch (parts[1].toLowerCase()) {
            case "status" -> androidStatus(sink);
            case "testinput" -> androidTestInput(sink);
            default -> sink.error("Usage: #painting android status|testinput");
        }
    }

    private void androidStatus(SessionController.MessageSink sink) {
        ConfigManager.Config config = configManager.config();
        sink.info("Minecraft=" + SharedConstants.getGameVersion().getName()
                + " Java=" + System.getProperty("java.version", "unknown")
                + " OS=" + System.getProperty("os.name", "unknown")
                + " fabricApiLoaded=" + FabricLoader.getInstance().isModLoaded("fabric-api")
                + " selectedCalibration=" + config.selectedCalibrationName()
                + " portableExactCalibration=" + config.portableExactCalibrationMode());
        paths(sink);
    }

    private void androidTestInput(SessionController.MessageSink sink) {
        MinecraftClient client = MinecraftClient.getInstance();
        String screen = client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName();
        try {
            double[] x = new double[1];
            double[] y = new double[1];
            GLFW.glfwGetCursorPos(client.getWindow().getHandle(), x, y);
            double scaledX = x[0] * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
            double scaledY = y[0] * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
            sink.info("Input test: screen=" + screen
                    + " scaledWindow=" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight()
                    + " cursor=" + Math.round(scaledX) + "," + Math.round(scaledY)
                    + " guiRecorderArmed=" + guiClickRecorder.armed() + ".");
        } catch (RuntimeException e) {
            sink.error("Input test failed: " + e.getMessage());
        }
    }

    private void handleCalibrationPortable(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 3 || !parts[1].equalsIgnoreCase("portable")) {
            sink.error("Usage: #painting calibration portable on|off|status");
            return;
        }
        switch (parts[2].toLowerCase()) {
            case "on" -> {
                configManager.setPortableExactCalibrationMode(true, text -> sink.error(text.getString()));
                sink.error("Portable calibration mode enabled. Verify aim with #painting cal test before painting.");
            }
            case "off" -> {
                configManager.setPortableExactCalibrationMode(false, text -> sink.error(text.getString()));
                sink.info("Portable calibration mode disabled.");
            }
            case "status" -> sink.info("Portable exact calibration mode=" + configManager.config().portableExactCalibrationMode() + ".");
            default -> sink.error("Usage: #painting calibration portable on|off|status");
        }
    }

    private void paths(SessionController.MessageSink sink) {
        FabricLoader loader = FabricLoader.getInstance();
        sink.info("Game dir: " + loader.getGameDir());
        sink.info("Config dir: " + loader.getConfigDir());
        sink.info("ArtMap config: " + configManager.configPath());
        sink.info("PNG imports: " + configManager.importsPath());
        sink.info("Calibrations: " + configManager.calibrationsPath());
    }

    private void handleBatch(String[] parts, SessionController.MessageSink sink) {
        if (parts.length < 2) {
            sink.error("Usage: #painting batch start <first> <last> <nameSuffix>|continue|status|stop");
            return;
        }
        switch (parts[1].toLowerCase()) {
            case "start" -> {
                if (!configManager.config().paintingMode().allowsBatch()) {
                    sink.error("Batch requires painting type auto or smart. Run #painting set auto or #painting set smart first.");
                    return;
                }
                if (parts.length < 5) {
                    sink.error("Usage: #painting batch start <first> <last> <nameSuffix>");
                    return;
                }
                stopPaintersBeforeSessionReplace(sink);
                try {
                    int first = Integer.parseInt(parts[2]);
                    int last = Integer.parseInt(parts[3]);
                    String suffix = joinParts(parts, 4);
                    batchManager.start(first, last, suffix, sink);
                } catch (NumberFormatException e) {
                    sink.error("Batch first and last values must be numbers.");
                }
            }
            case "continue" -> {
                if (!configManager.config().paintingMode().allowsBatch()) {
                    sink.error("Batch requires painting type auto or smart. Run #painting set auto or #painting set smart first.");
                    return;
                }
                batchManager.continueBatch(sink);
            }
            case "status" -> batchManager.status(sink);
            case "stop" -> batchManager.stop(sink);
            default -> sink.error("Usage: #painting batch start <first> <last> <nameSuffix>|continue|status|stop");
        }
    }

    private void handlePalette(String[] parts, SessionController.MessageSink sink) {
        if (parts.length < 2) {
            sink.error("Usage: #painting palette status|reds|why <hex>");
            return;
        }
        switch (parts[1].toLowerCase()) {
            case "status" -> controller.paletteStatus(sink);
            case "reds" -> controller.paletteReds(sink);
            case "why" -> {
                if (parts.length != 3) {
                    sink.error("Usage: #painting palette why <hex>");
                    return;
                }
                controller.paletteWhy(parts[2], sink);
            }
            default -> sink.error("Usage: #painting palette status|reds|why <hex>");
        }
    }

    private void handleExactCalibration(String[] parts, SessionController.MessageSink sink) {
        if (parts.length < 2) {
            sink.error("Usage: #painting calibrate start|continue|resume|save|reset <name>, stop, status, clear");
            return;
        }
        switch (parts[1].toLowerCase()) {
            case "start" -> {
                if (parts.length != 3) {
                    sink.error("Usage: #painting calibrate start <name>");
                    return;
                }
                persistCalibrationName(parts[2], sink);
                calibrationManager.startRecording(parts[2], configManager.config(), sink);
            }
            case "continue", "resume" -> {
                if (parts.length != 3) {
                    sink.error("Usage: #painting calibrate " + parts[1].toLowerCase() + " <name>");
                    return;
                }
                persistCalibrationName(parts[2], sink);
                calibrationManager.continueRecording(parts[2], configManager.config(), sink);
            }
            case "save" -> {
                if (parts.length != 3) {
                    sink.error("Usage: #painting calibrate save <name>");
                    return;
                }
                persistCalibrationName(parts[2], sink);
                calibrationManager.saveRecordingAs(parts[2], sink);
            }
            case "reset" -> {
                if (parts.length != 3) {
                    sink.error("Usage: #painting calibrate reset <name>");
                    return;
                }
                persistCalibrationName(parts[2], sink);
                calibrationManager.resetRecording(parts[2], sink);
            }
            case "stop" -> calibrationManager.stopRecording(sink);
            case "undo" -> calibrationManager.undoLastRecordingPoint(sink);
            case "status" -> calibrationManager.status(sink);
            case "clear" -> calibrationManager.clear(sink);
            default -> sink.error("Usage: #painting calibrate start|continue|resume|save|reset <name>, stop, undo, status, clear");
        }
    }

    private void handleUseCalibration(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 2) {
            sink.error("Usage: #painting usecalibration <name>");
            return;
        }
        persistCalibrationName(parts[1], sink);
        calibrationManager.loadExact(parts[1], configManager.config(), sink);
    }

    private void handleCalibration(String[] parts, SessionController.MessageSink sink) {
        if (parts.length < 2) {
            sink.error("Usage: #painting cal top-left|top-right|bottom-left|bottom-right|status|clear|test <x> <y>");
            return;
        }
        String subcommand = parts[1].toLowerCase();
        if (subcommand.equals("status")) {
            calibrationManager.status(sink);
            return;
        }
        if (subcommand.equals("clear")) {
            calibrationManager.clear(sink);
            return;
        }
        if (subcommand.equals("test")) {
            handleCalibrationTest(parts, sink);
            return;
        }
        if (parts.length != 2) {
            sink.error("Usage: #painting cal " + subcommand);
            return;
        }
        CalibrationPoint point = CalibrationPoint.fromCommand(subcommand);
        if (point == null) {
            sink.error("Usage: #painting cal top-left|top-right|bottom-left|bottom-right|status|clear|test <x> <y>");
            return;
        }
        calibrationManager.capture(point, sink);
    }

    private void handleCalibrationTest(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 4) {
            sink.error("Usage: #painting cal test <x> <y>");
            return;
        }
        try {
            calibrationManager.testAim(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), configManager.config(), sink);
        } catch (NumberFormatException e) {
            sink.error("Calibration test coordinates must be numbers.");
        }
    }

    private void handleGoto(String[] parts, SessionController.MessageSink sink) {
        try {
            if (parts.length == 2) {
                controller.gotoIndex(Integer.parseInt(parts[1]), sink);
            } else if (parts.length == 3) {
                controller.gotoXY(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), sink);
            } else {
                sink.error("Usage: #painting goto <index> or #painting goto <x> <y>");
            }
        } catch (NumberFormatException e) {
            sink.error("Goto values must be numbers.");
        }
    }

    private void handlePos(String[] parts, SessionController.MessageSink sink) {
        try {
            if (parts.length == 3) {
                controller.gotoXY(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), sink);
            } else {
                sink.error("Usage: #painting pos <x> <y>");
            }
        } catch (NumberFormatException e) {
            sink.error("Position values must be numbers.");
        }
    }

    private void handleConfirm(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 2 || (!parts[1].equalsIgnoreCase("on") && !parts[1].equalsIgnoreCase("off"))) {
            sink.error("Usage: #painting confirm on|off");
            return;
        }
        confirmMode = parts[1].equalsIgnoreCase("on");
        sink.info("Confirm mode " + (confirmMode ? "enabled. Use the advance keybind." : "disabled. Mouse clicks advance."));
    }

    private void handleAuto(String[] parts, SessionController.MessageSink sink) {
        if (parts.length < 2) {
            sink.info(autoPainter.status());
            return;
        }
        switch (parts[1].toLowerCase()) {
            case "start", "full" -> startAutoOrSmart(sink);
            case "stop" -> {
                if (smartPainter.running()) {
                    smartPainter.stop(sink);
                }
                autoPainter.stop(sink);
            }
            case "pause" -> {
                smartPainter.pause(sink);
                autoPainter.pause(sink);
            }
            case "resume" -> {
                smartPainter.resume(sink);
                autoPainter.resume(sink);
            }
            case "status" -> {
                sink.info(autoPainter.status(configManager.config()));
                sink.info(smartPainter.status(configManager.config()));
                sink.info(calibrationManager.activeCalibrationStatusLine());
            }
            case "speed" -> handleAutoSpeed(parts, sink);
            case "drag" -> handleAutoDrag(parts, sink);
            default -> sink.error("Usage: #painting auto start|stop|pause|resume|status|speed <ticks>|drag on|off|status");
        }
    }

    private boolean startAutoOrSmart(SessionController.MessageSink sink) {
        ConfigManager.Config config = configManager.config();
        return switch (config.paintingMode()) {
            case MANUAL -> startManualAssist(sink);
            case AUTO -> {
                if (controller.session() == null) {
                    sink.error("No active painting session. Start with #painting <image.png> first.");
                    yield false;
                }
                if (smartPainter.running()) {
                    smartPainter.stop(sink);
                }
                if (!calibrationManager.prepareBundledDirectionalCalibration(config, sink)) {
                    yield false;
                }
                autoPainter.start(config, sink);
                yield autoPainter.running();
            }
            case SMART -> {
                if (controller.session() == null) {
                    sink.error("No active painting session. Start with #painting <image.png> first.");
                    yield false;
                }
                if (!calibrationManager.prepareBundledDirectionalCalibration(config, sink)) {
                    yield false;
                }
                if (smartPainter.start(config, sink)) {
                    if (autoPainter.running()) {
                        autoPainter.stop(sink);
                    }
                    yield true;
                } else {
                    autoPainter.start(config, sink);
                    yield autoPainter.running();
                }
            }
        };
    }

    private boolean startManualAssist(SessionController.MessageSink sink) {
        if (smartPainter.running()) {
            smartPainter.stop(sink);
        }
        if (autoPainter.running()) {
            autoPainter.stop(sink);
        }
        if (controller.session() == null) {
            sink.error("No active painting session. Start with #painting <image.png> first.");
            return false;
        }
        controller.switchCurrentNow(sink);
        sink.info("Manual assisted painting ready. The mod will swap to the next color after each allowed user click.");
        return true;
    }

    private void startImage(String filename, SessionController.MessageSink sink) {
        stopPaintersBeforeSessionReplace(sink);
        controller.start(filename, sink);
    }

    private void stopPaintersBeforeSessionReplace(SessionController.MessageSink sink) {
        if (smartPainter.running()) {
            smartPainter.stop(sink);
        }
        if (autoPainter.running()) {
            autoPainter.stop(sink);
        }
    }

    private void handleSmart(String[] parts, SessionController.MessageSink sink) {
        if (parts.length < 2) {
            sink.error("Usage: #painting smart on|off|status|preview|basecoat on|off|threshold <number>|dragthreshold <number>");
            return;
        }
        switch (parts[1].toLowerCase()) {
            case "on" -> {
                applyPaintingMode(PaintingMode.SMART, sink);
                sink.info("Smart painting enabled.");
            }
            case "off" -> {
                if (configManager.config().paintingMode() == PaintingMode.SMART) {
                    applyPaintingMode(PaintingMode.AUTO, sink);
                } else {
                    configManager.setSmartEnabled(false, text -> sink.error(text.getString()));
                }
                smartPainter.invalidateTrust("smart disabled");
                sink.info("Smart painting disabled. Old auto remains available.");
            }
            case "status" -> sink.info(smartPainter.status(configManager.config()));
            case "preview" -> {
                if (configManager.config().paintingMode() != PaintingMode.SMART) {
                    sink.error("Smart preview only applies to painting type smart. Run #painting set smart first.");
                    return;
                }
                smartPreview(sink);
            }
            case "basecoat" -> handleSmartBasecoat(parts, sink);
            case "threshold" -> handleSmartThreshold(parts, sink);
            case "dragthreshold" -> handleSmartDragThreshold(parts, sink);
            default -> sink.error("Usage: #painting smart on|off|status|preview|basecoat on|off|threshold <number>|dragthreshold <number>");
        }
    }

    private void handleSmartBasecoat(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 3) {
            sink.error("Usage: #painting smart basecoat on|off");
            return;
        }
        switch (parts[2].toLowerCase()) {
            case "on" -> {
                configManager.setSmartBaseCoatEnabled(true, text -> sink.error(text.getString()));
                sink.info("Smart basecoat enabled.");
            }
            case "off" -> {
                configManager.setSmartBaseCoatEnabled(false, text -> sink.error(text.getString()));
                sink.info("Smart basecoat disabled.");
            }
            default -> sink.error("Usage: #painting smart basecoat on|off");
        }
    }

    private void handleSmartThreshold(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 3) {
            sink.error("Usage: #painting smart threshold <number>");
            return;
        }
        try {
            int value = Integer.parseInt(parts[2]);
            configManager.setSmartBucketThreshold(value, text -> sink.error(text.getString()));
            sink.info("Smart bucket threshold set to " + Math.max(2, value) + " pixels.");
        } catch (NumberFormatException e) {
            sink.error("Smart threshold must be a number.");
        }
    }

    private void handleSmartDragThreshold(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 3) {
            sink.error("Usage: #painting smart dragthreshold <number>");
            return;
        }
        try {
            int value = Integer.parseInt(parts[2]);
            configManager.setSmartDragThreshold(value, text -> sink.error(text.getString()));
            sink.info("Smart drag threshold set to " + Math.max(2, value) + " pixels.");
        } catch (NumberFormatException e) {
            sink.error("Smart drag threshold must be a number.");
        }
    }

    private void smartPreview(SessionController.MessageSink sink) {
        SmartPreview preview = smartPainter.preview(configManager.config());
        if (preview == null) {
            sink.error("No active painting session. Start with #painting <image.png> first.");
            return;
        }
        sink.info(preview.summary());
    }

    private void handleBucket(String[] parts, SessionController.MessageSink sink) {
        if (parts.length < 2) {
            sink.error("Usage: #painting bucket on|off|status|preview|selectdelay|swapdelay|aimdelay|afterdelay|restoredelay <ticks>");
            return;
        }
        String bucketCommand = parts[1].toLowerCase(Locale.ROOT);
        if (!bucketCommand.equals("status") && !configManager.config().paintingMode().allowsBucketConfig()) {
            sink.error("Bucket only works in painting type smart. Run #painting set smart first.");
            return;
        }
        switch (bucketCommand) {
            case "on" -> {
                configManager.setBucketEnabled(true, text -> sink.error(text.getString()));
                sink.info("Smart bucket actions enabled.");
            }
            case "off" -> {
                configManager.setBucketEnabled(false, text -> sink.error(text.getString()));
                smartPainter.invalidateTrust("bucket disabled");
                sink.info("Smart bucket actions disabled.");
            }
            case "status" -> bucketStatus(sink);
            case "preview" -> smartPreview(sink);
            case "selectdelay" -> handleBucketNumber(parts, "selectdelay", sink);
            case "swapdelay" -> handleBucketNumber(parts, "swapdelay", sink);
            case "aimdelay" -> handleBucketNumber(parts, "aimdelay", sink);
            case "afterdelay" -> handleBucketNumber(parts, "afterdelay", sink);
            case "restoredelay" -> handleBucketNumber(parts, "restoredelay", sink);
            default -> sink.error("Usage: #painting bucket on|off|status|preview|selectdelay|swapdelay|aimdelay|afterdelay|restoredelay <ticks>");
        }
    }

    private void handleBucketNumber(String[] parts, String key, SessionController.MessageSink sink) {
        if (parts.length != 3) {
            sink.error("Usage: #painting bucket " + key + " <number>");
            return;
        }
        try {
            int value = Integer.parseInt(parts[2]);
            switch (key) {
                case "selectdelay" -> configManager.setBucketClickRepeats(value, text -> sink.error(text.getString()));
                case "swapdelay" -> configManager.setBucketClickGapTicks(value, text -> sink.error(text.getString()));
                case "aimdelay" -> configManager.setBucketSwapDelayTicks(value, text -> sink.error(text.getString()));
                case "afterdelay" -> configManager.setBucketAimSettleTicks(value, text -> sink.error(text.getString()));
                case "restoredelay" -> configManager.setBucketAfterDelayTicks(value, text -> sink.error(text.getString()));
                default -> {
                }
            }
            sink.info("Bucket " + key + " updated.");
        } catch (NumberFormatException e) {
            sink.error("Bucket " + key + " must be a number.");
        }
    }

    private void bucketStatus(SessionController.MessageSink sink) {
        ConfigManager.Config config = configManager.config();
        sink.info("Bucket enabled=" + config.bucketEnabled()
                + " mode=single-initial-left-click"
                + " colorSelectDelay=" + config.bucketColorSelectDelayTicks()
                + " handSwapDelay=" + config.bucketHandSwapDelayTicks()
                + " fillAimSettle=" + config.bucketFillAimSettleTicks()
                + " postFillDelay=" + config.bucketPostFillDelayTicks()
                + " handRestoreDelay=" + config.bucketHandRestoreDelayTicks()
                + " offhandExactEmptyBucket=" + controller.exactEmptyBucketInOffhand() + ".");
    }

    private void handleAutoDrag(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 3) {
            sink.error("Usage: #painting auto drag on|off|status");
            return;
        }
        if (!parts[2].equalsIgnoreCase("status") && !configManager.config().paintingMode().allowsAutoDragConfig()) {
            sink.error("Auto drag is disabled in painting type manual because clicks are manual. Run #painting set auto or #painting set smart first.");
            return;
        }
        switch (parts[2].toLowerCase()) {
            case "on" -> {
                configManager.setAutoDragSameColorRuns(true, text -> sink.error(text.getString()));
                autoPainter.setDragEnabled(true, sink);
            }
            case "off" -> {
                configManager.setAutoDragSameColorRuns(false, text -> sink.error(text.getString()));
                autoPainter.setDragEnabled(false, sink);
            }
            case "status" -> sink.info(autoPainter.status());
            default -> sink.error("Usage: #painting auto drag on|off|status");
        }
    }

    private void handleAutoSpeed(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 3) {
            sink.error("Usage: #painting auto speed <ticks>");
            return;
        }
        try {
            autoPainter.setSpeed(Integer.parseInt(parts[2]), configManager.config(), sink);
        } catch (NumberFormatException e) {
            sink.error("Auto paint speed must be a number of ticks.");
        }
    }

    private void usage(SessionController.MessageSink sink) {
        sink.info("Usage: #painting help, gui, <file.png>, dryrun, palette ..., batch ..., postpaint ..., rename ..., pv <1-40>, pv2 ..., auto full, stop, pause, resume, back, skip, goto, calibrate ..., usecalibration <name>, cal ...");
    }

    private void openGui(SessionController.MessageSink sink) {
        ArtMapColorAssistantClient.requestGuiOpen();
        sink.info("Opening ArtMap controls.");
    }

    void runLocal(String command, SessionController.MessageSink sink) {
        handle(command, sink);
    }

    boolean paintNow(String filename, SessionController.MessageSink sink) {
        return GuardedPaintStarter.start(filename,
                () -> stopPaintersBeforeSessionReplace(sink),
                name -> controller.start(name, sink),
                () -> startAutoOrSmart(sink));
    }

    private void help(SessionController.MessageSink sink) {
        sink.info(Text.literal("ArtMapColorAssistant help").formatted(Formatting.GOLD));
        for (CommandGuide.Entry entry : CommandGuide.suggestions("#painting")) {
            helpLine(sink, "#painting " + entry.command(), entry.description());
        }
        for (CommandGuide.Entry entry : CommandGuide.suggestions("#painting auto")) {
            helpLine(sink, "#painting auto " + entry.command(), entry.description());
        }
        for (CommandGuide.Entry entry : CommandGuide.suggestions("#painting smart")) {
            helpLine(sink, "#painting smart " + entry.command(), entry.description());
        }
        for (CommandGuide.Entry entry : CommandGuide.suggestions("#painting bucket")) {
            helpLine(sink, "#painting bucket " + entry.command(), entry.description());
        }
        for (CommandGuide.Entry entry : CommandGuide.suggestions("#painting palette")) {
            helpLine(sink, "#painting palette " + entry.command(), entry.description());
        }
        for (CommandGuide.Entry entry : CommandGuide.suggestions("#painting batch")) {
            helpLine(sink, "#painting batch " + entry.command(), entry.description());
        }
        for (CommandGuide.Entry entry : CommandGuide.suggestions("#painting postpaint")) {
            helpLine(sink, "#painting postpaint " + entry.command(), entry.description());
        }
        for (CommandGuide.Entry entry : CommandGuide.suggestions("#painting rename")) {
            helpLine(sink, "#painting rename " + entry.command(), entry.description());
        }
        for (CommandGuide.Entry entry : CommandGuide.suggestions("#painting pv2")) {
            helpLine(sink, "#painting pv2 " + entry.command(), entry.description());
        }
        for (CommandGuide.Entry entry : CommandGuide.suggestions("#painting android")) {
            helpLine(sink, "#painting android " + entry.command(), entry.description());
        }
        for (CommandGuide.Entry entry : CommandGuide.suggestions("#painting calibration")) {
            helpLine(sink, "#painting calibration " + entry.command(), entry.description());
        }
        for (CommandGuide.Entry entry : CommandGuide.suggestions("#painting calibrate")) {
            helpLine(sink, "#painting calibrate " + entry.command(), entry.description());
        }
        helpLine(sink, "#painting cal test 0 0", "Aim at a calibrated pixel without clicking.");
        helpLine(sink, "#painting goto <x> <y>", "Move session to pixel coordinates.");
        helpLine(sink, "#painting back", "Move back one pixel.");
        helpLine(sink, "#painting skip", "Skip current pixel.");
        helpLine(sink, "#painting pause", "Pause painting session.");
        helpLine(sink, "#painting resume", "Resume painting session.");
    }

    private void helpLine(SessionController.MessageSink sink, String command, String description) {
        MutableText text = Text.literal(command)
                .formatted(Formatting.YELLOW)
                .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(description))));
        sink.info(text.append(Text.literal(" - ").formatted(Formatting.GRAY))
                .append(Text.literal(description).formatted(Formatting.WHITE)));
    }

    private void persistCalibrationName(String rawName, SessionController.MessageSink sink) {
        String name = ConfigManager.sanitizeCalibrationName(rawName);
        calibrationManager.setLastCalibrationName(name);
        configManager.setSelectedCalibrationName(name, text -> sink.error(text.getString()));
    }

    private String joinParts(String[] parts, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(parts[i]);
        }
        return builder.toString();
    }
}
