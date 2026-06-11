package com.artmapcolorassistant;

public final class HashCommandHandler {
    private final ConfigManager configManager;
    private final SessionController controller;
    private boolean confirmMode;

    public HashCommandHandler(ConfigManager configManager, SessionController controller) {
        this.configManager = configManager;
        this.controller = controller;
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
            case "stop" -> controller.stop(sink);
            case "pause" -> controller.pause(sink);
            case "resume" -> controller.resume(sink);
            case "back" -> controller.back(sink);
            case "skip" -> controller.skip(sink);
            case "status" -> controller.status(sink);
            case "reload" -> {
                configManager.load(text -> sink.error(text.getString()));
                confirmMode = configManager.config().confirmMode();
                sink.info("Config reloaded.");
                for (String warning : configManager.warnings()) {
                    sink.error(warning);
                }
            }
            case "goto" -> handleGoto(parts, sink);
            case "pos" -> handlePos(parts, sink);
            case "confirm" -> handleConfirm(parts, sink);
            default -> {
                if (parts.length == 1 && command.endsWith(".png")) {
                    controller.start(parts[0], sink);
                } else {
                    usage(sink);
                }
            }
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

    private void usage(SessionController.MessageSink sink) {
        sink.info("Usage: #painting <file.png>, dryrun, stop, pause, resume, back, skip, goto, pos, status, reload, confirm on|off.");
    }
}
