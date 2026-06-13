package com.artmapcolorassistant;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class HashCommandHandler {
    private final ConfigManager configManager;
    private final SessionController controller;
    private final AutoPainter autoPainter;
    private final CalibrationManager calibrationManager;
    private final BatchManager batchManager;
    private final GuiClickRecorder guiClickRecorder;
    private boolean confirmMode;

    public HashCommandHandler(ConfigManager configManager, SessionController controller, AutoPainter autoPainter,
                              CalibrationManager calibrationManager, BatchManager batchManager,
                              GuiClickRecorder guiClickRecorder) {
        this.configManager = configManager;
        this.controller = controller;
        this.autoPainter = autoPainter;
        this.calibrationManager = calibrationManager;
        this.batchManager = batchManager;
        this.guiClickRecorder = guiClickRecorder;
        this.confirmMode = configManager.config().confirmMode();
    }

    public boolean confirmMode() {
        return confirmMode;
    }

    public static boolean isPaintingCommand(String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim();
        return trimmed.startsWith("#painting") || trimmed.startsWith("#paint");
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
                controller.stop(sink);
            }
            case "pause" -> controller.pause(sink);
            case "resume" -> controller.resume(sink);
            case "back" -> controller.back(sink);
            case "skip" -> controller.skip(sink);
            case "status" -> controller.status(sink);
            case "help" -> help(sink);
            case "gui" -> openGui(sink);
            case "full" -> autoPainter.start(configManager.config(), sink);
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
            case "palette" -> handlePalette(parts, sink);
            case "batch" -> handleBatch(parts, sink);
            case "postpaint" -> handlePostPaint(parts, sink);
            case "rename" -> handleRename(parts, sink);
            case "pv2" -> handlePv2(parts, sink);
            case "cal" -> handleCalibration(parts, sink);
            case "calibrate" -> handleExactCalibration(parts, sink);
            case "usecalibration" -> handleUseCalibration(parts, sink);
            default -> {
                if (parts.length == 1 && command.endsWith(".png")) {
                    controller.start(parts[0], sink);
                } else {
                    usage(sink);
                }
            }
        }
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
        if (parts.length != 2) {
            sink.error("Usage: #painting pv2 click|clear");
            return;
        }
        switch (parts[1].toLowerCase()) {
            case "click" -> guiClickRecorder.armPv2(sink);
            case "clear" -> guiClickRecorder.clearPv2(sink);
            default -> sink.error("Usage: #painting pv2 click|clear");
        }
    }

    private void postPaintStatus(SessionController.MessageSink sink) {
        ConfigManager.Config config = configManager.config();
        sink.info("Post-paint automation=" + config.postPaintAutomationEnabled()
                + " saveSlot=" + (config.postPaintSaveHotbarSlot() + 1)
                + " finishedSlot=" + (config.postPaintFinishedHotbarSlot() + 1)
                + " blankSlot=" + (config.postPaintBlankCanvasHotbarSlot() + 1)
                + " aimIndex=" + config.postPaintAimCalibrationIndex()
                + " vaultCommand=\"" + config.postPaintVaultCommand() + "\""
                + " renamePoint=" + (config.postPaintRenameClickPoint() != null)
                + " pv2Point=" + (config.postPaintPv2ClickPoint() != null)
                + " " + batchManager.statusLine() + ".");
    }

    private void handleBatch(String[] parts, SessionController.MessageSink sink) {
        if (parts.length < 2) {
            sink.error("Usage: #painting batch start <first> <last> <nameSuffix>|continue|status|stop");
            return;
        }
        switch (parts[1].toLowerCase()) {
            case "start" -> {
                if (parts.length < 5) {
                    sink.error("Usage: #painting batch start <first> <last> <nameSuffix>");
                    return;
                }
                try {
                    int first = Integer.parseInt(parts[2]);
                    int last = Integer.parseInt(parts[3]);
                    String suffix = joinParts(parts, 4);
                    batchManager.start(first, last, suffix, sink);
                } catch (NumberFormatException e) {
                    sink.error("Batch first and last values must be numbers.");
                }
            }
            case "continue" -> batchManager.continueBatch(sink);
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
            case "start", "full" -> autoPainter.start(configManager.config(), sink);
            case "stop" -> autoPainter.stop(sink);
            case "pause" -> autoPainter.pause(sink);
            case "resume" -> autoPainter.resume(sink);
            case "status" -> sink.info(autoPainter.status(configManager.config()));
            case "speed" -> handleAutoSpeed(parts, sink);
            case "drag" -> handleAutoDrag(parts, sink);
            default -> sink.error("Usage: #painting auto start|stop|pause|resume|status|speed <ticks>|drag on|off|status");
        }
    }

    private void handleAutoDrag(String[] parts, SessionController.MessageSink sink) {
        if (parts.length != 3) {
            sink.error("Usage: #painting auto drag on|off|status");
            return;
        }
        switch (parts[2].toLowerCase()) {
            case "on" -> autoPainter.setDragEnabled(true, sink);
            case "off" -> autoPainter.setDragEnabled(false, sink);
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
        sink.info("Usage: #painting help, gui, <file.png>, dryrun, palette ..., batch ..., postpaint ..., rename ..., pv2 ..., auto full, stop, pause, resume, back, skip, goto, calibrate ..., usecalibration <name>, cal ...");
    }

    private void openGui(SessionController.MessageSink sink) {
        ArtMapColorAssistantClient.requestGuiOpen();
        sink.info("Opening ArtMap controls.");
    }

    void runLocal(String command, SessionController.MessageSink sink) {
        handle(command, sink);
    }

    private void help(SessionController.MessageSink sink) {
        sink.info(Text.literal("ArtMapColorAssistant help").formatted(Formatting.GOLD));
        for (CommandGuide.Entry entry : CommandGuide.suggestions("#painting")) {
            helpLine(sink, "#painting " + entry.command(), entry.description());
        }
        for (CommandGuide.Entry entry : CommandGuide.suggestions("#painting auto")) {
            helpLine(sink, "#painting auto " + entry.command(), entry.description());
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
