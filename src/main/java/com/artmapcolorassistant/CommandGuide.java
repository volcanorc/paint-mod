package com.artmapcolorassistant;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class CommandGuide {
    private static final List<Entry> ROOT = List.of(
            new Entry("help", "Show clickable help."),
            new Entry("gui", "Open client controls."),
            new Entry("paths", "Show import and calibration folders."),
            new Entry("set manual|auto|smart", "Choose painting type."),
            new Entry("android", "Android/Pojav diagnostics."),
            new Entry("<file.png>", "Start an imported image."),
            new Entry("dryrun <file.png>", "Analyze without painting."),
            new Entry("palette", "Inspect color matching."),
            new Entry("batch", "Paint numbered image queue."),
            new Entry("postpaint", "Post-paint save/vault setup."),
            new Entry("rename", "Record save GUI click."),
            new Entry("pv <1-40>", "Choose the finished-canvas Player Vault."),
            new Entry("pv2", "Select Player Vault 2; click/clear are legacy."),
            new Entry("full", "Start full auto."),
            new Entry("auto", "Auto painting controls."),
            new Entry("smart", "Smart hybrid painting controls."),
            new Entry("bucket", "Smart bucket timing and guard controls."),
            new Entry("calibrate", "Exact calibration controls."),
            new Entry("calibration", "Portable calibration mode."),
            new Entry("cal", "Corner calibration and aim test."),
            new Entry("usecalibration <name>", "Load a saved calibration."),
            new Entry("status", "Show current status.")
    );
    private static final List<Entry> AUTO = List.of(
            new Entry("full", "Start full auto."),
            new Entry("start", "Start auto painting."),
            new Entry("stop", "Stop auto painting."),
            new Entry("pause", "Pause auto painting."),
            new Entry("resume", "Resume auto painting."),
            new Entry("status", "Show auto status."),
            new Entry("speed <ticks>", "Set auto delay."),
            new Entry("drag on|off|status", "Control row dragging.")
    );
    private static final List<Entry> SMART = List.of(
            new Entry("on", "Enable smart hybrid auto."),
            new Entry("off", "Disable smart hybrid auto."),
            new Entry("status", "Show smart state."),
            new Entry("preview", "Estimate smart route without painting."),
            new Entry("basecoat on|off", "Control dominant-color basecoat."),
            new Entry("threshold <number>", "Set minimum bucket region."),
            new Entry("dragthreshold <number>", "Set minimum smart drag run.")
    );
    private static final List<Entry> BUCKET = List.of(
            new Entry("on", "Enable the initial dominant-color bucket base coat."),
            new Entry("off", "Disable the initial bucket base coat."),
            new Entry("status", "Show bucket guard/timing."),
            new Entry("preview", "Preview the prepared initial base coat and drag route."),
            new Entry("selectdelay <ticks>", "Set dominant-color selection delay."),
            new Entry("swapdelay <ticks>", "Set hand-swap verification delay."),
            new Entry("aimdelay <ticks>", "Set fill-anchor aim delay."),
            new Entry("afterdelay <ticks>", "Set post-fill delay."),
            new Entry("restoredelay <ticks>", "Set hand-restoration delay.")
    );
    private static final List<Entry> PALETTE = List.of(
            new Entry("status", "Show loaded and usable colors."),
            new Entry("reds", "List available red-ish colors."),
            new Entry("why <hex>", "Explain nearest colors for RGB.")
    );
    private static final List<Entry> BATCH = List.of(
            new Entry("start <first> <last> <nameSuffix>", "Start numbered PNG batch."),
            new Entry("continue", "Start next batch image after setup."),
            new Entry("status", "Show batch progress."),
            new Entry("stop", "Stop batch queue.")
    );
    private static final List<Entry> POSTPAINT = List.of(
            new Entry("on", "Enable guarded post-paint automation."),
            new Entry("off", "Disable post-paint automation."),
            new Entry("status", "Show post-paint setup.")
    );
    private static final List<Entry> RENAME = List.of(
            new Entry("click", "Record save GUI click point."),
            new Entry("clear", "Clear save GUI click point.")
    );
    private static final List<Entry> PV2 = List.of(
            new Entry("click", "Legacy only; automatic slot transfer is default."),
            new Entry("clear", "Clear legacy vault click point.")
    );
    private static final List<Entry> PV = List.of(
            new Entry("<1-40>", "Save finished canvases in this Player Vault.")
    );
    private static final List<Entry> ANDROID = List.of(
            new Entry("status", "Show Android/Pojav paths and runtime."),
            new Entry("testinput", "Check cursor/touch capture.")
    );
    private static final List<Entry> CALIBRATION = List.of(
            new Entry("portable on", "Allow transferred exact calibration."),
            new Entry("portable off", "Require same eye position."),
            new Entry("portable status", "Show portable mode.")
    );
    private static final List<Entry> CALIBRATE = List.of(
            new Entry("start <name>", "Start fresh unsaved calibration."),
            new Entry("continue <name>", "Continue from saved file."),
            new Entry("resume <name>", "Discard unsaved and reload saved."),
            new Entry("save <name>", "Save current calibration."),
            new Entry("stop", "Stop without saving."),
            new Entry("undo", "Remove last recorded point."),
            new Entry("reset <name>", "Delete saved calibration."),
            new Entry("status", "Show calibration progress.")
    );
    private static final List<Entry> CAL = List.of(
            new Entry("test <x> <y>", "Aim at calibrated pixel."),
            new Entry("status", "Show calibration status."),
            new Entry("clear", "Clear calibration in memory."),
            new Entry("top-left", "Set corner direction."),
            new Entry("top-right", "Set corner direction."),
            new Entry("bottom-left", "Set corner direction."),
            new Entry("bottom-right", "Set corner direction.")
    );
    private static final List<Node> CHAT_ROOT = List.of(
            leaf("help", "Show clickable help."),
            leaf("gui", "Open client controls."),
            leaf("paths", "Show import and calibration folders."),
            branch("set", "Choose painting type.",
                    leaf("manual", "Use assisted manual painting."),
                    leaf("auto", "Use classic automatic painting."),
                    leaf("smart", "Use Smart painting.")),
            branch("android", "Android/Pojav diagnostics.",
                    leaf("status", "Show Android/Pojav paths and runtime."),
                    leaf("testinput", "Check cursor and touch capture.")),
            argument("dryrun", "Analyze an image without painting.", "<file.png>"),
            branch("palette", "Inspect color matching.",
                    leaf("status", "Show loaded and usable colors."),
                    leaf("reds", "List available red-ish colors."),
                    argument("why", "Explain nearest colors for RGB.", "<hex>")),
            branch("batch", "Paint a numbered image queue.",
                    argument("start", "Start a numbered PNG batch.", "<first> <last> <nameSuffix>"),
                    leaf("continue", "Start the next prepared batch image."),
                    leaf("status", "Show batch progress."),
                    leaf("stop", "Stop the batch queue.")),
            branch("postpaint", "Control guarded post-paint automation.",
                    leaf("on", "Enable post-paint automation."),
                    leaf("off", "Disable post-paint automation."),
                    leaf("status", "Show post-paint setup.")),
            branch("rename", "Record the save GUI click point.",
                    leaf("click", "Record a new save GUI click point."),
                    leaf("clear", "Clear the save GUI click point.")),
            argument("pv", "Choose the finished-canvas Player Vault.", "<1-40>"),
            branch("pv2", "Select Player Vault 2; click and clear are legacy.",
                    leaf("click", "Record the legacy vault click point."),
                    leaf("clear", "Clear the legacy vault click point.")),
            leaf("full", "Start the selected painting mode."),
            branch("auto", "Automatic painting controls.",
                    leaf("full", "Start the selected painting mode."),
                    leaf("start", "Start automatic painting."),
                    leaf("stop", "Stop automatic painting."),
                    leaf("pause", "Pause automatic painting."),
                    leaf("resume", "Resume automatic painting."),
                    leaf("status", "Show automatic painting status."),
                    argument("speed", "Set the automatic painting delay.", "<ticks>"),
                    branch("drag", "Control same-color row dragging.",
                            leaf("on", "Enable row dragging."),
                            leaf("off", "Disable row dragging."),
                            leaf("status", "Show row-dragging status."))),
            branch("smart", "Smart hybrid painting controls.",
                    leaf("on", "Enable Smart painting."),
                    leaf("off", "Disable Smart painting."),
                    leaf("status", "Show Smart painting status."),
                    leaf("preview", "Preview the Smart route."),
                    branch("basecoat", "Control the dominant-color base coat.",
                            leaf("on", "Enable the Smart base coat."),
                            leaf("off", "Disable the Smart base coat.")),
                    argument("threshold", "Set the minimum bucket region.", "<number>"),
                    argument("dragthreshold", "Set the minimum Smart drag run.", "<number>")),
            branch("bucket", "Smart bucket timing and guard controls.",
                    leaf("on", "Enable the guarded bucket action."),
                    leaf("off", "Disable the guarded bucket action."),
                    leaf("status", "Show bucket guard and timing."),
                    leaf("preview", "Preview the prepared Smart route."),
                    argument("selectdelay", "Set dominant-color selection delay.", "<ticks>"),
                    argument("swapdelay", "Set hand-swap verification delay.", "<ticks>"),
                    argument("aimdelay", "Set fill-anchor aim delay.", "<ticks>"),
                    argument("afterdelay", "Set post-fill delay.", "<ticks>"),
                    argument("restoredelay", "Set hand-restoration delay.", "<ticks>")),
            branch("calibrate", "Exact calibration controls.",
                    argument("start", "Start a fresh exact calibration.", "<name>"),
                    argument("continue", "Continue a saved calibration.", "<name>"),
                    argument("resume", "Reload and continue a saved calibration.", "<name>"),
                    argument("save", "Save calibration progress.", "<name>"),
                    argument("reset", "Delete a saved calibration.", "<name>"),
                    leaf("stop", "Stop calibration recording."),
                    leaf("undo", "Remove the last recorded point."),
                    leaf("status", "Show calibration progress."),
                    leaf("clear", "Clear calibration in memory.")),
            branch("calibration", "Portable calibration mode.",
                    branch("portable", "Control portable exact calibration.",
                            leaf("on", "Enable portable calibration."),
                            leaf("off", "Disable portable calibration."),
                            leaf("status", "Show portable calibration status."))),
            branch("cal", "Corner calibration and aim tests.",
                    leaf("top-left", "Set the top-left corner direction."),
                    leaf("top-right", "Set the top-right corner direction."),
                    leaf("bottom-left", "Set the bottom-left corner direction."),
                    leaf("bottom-right", "Set the bottom-right corner direction."),
                    leaf("status", "Show calibration status."),
                    leaf("clear", "Clear calibration in memory."),
                    argument("test", "Aim at a calibrated pixel.", "<x> <y>")),
            argument("usecalibration", "Load a saved exact calibration.", "<name>"),
            leaf("status", "Show current painting status."),
            leaf("stop", "Stop painting and active automation."),
            leaf("pause", "Pause the current painting session."),
            leaf("resume", "Resume the current painting session."),
            leaf("back", "Move back one painting pixel."),
            leaf("skip", "Skip the current painting pixel."),
            leaf("reload", "Reload the client configuration."),
            argument("goto", "Move to a pixel index or coordinate.", "<index> or <x> <y>"),
            argument("pos", "Move to a canvas coordinate.", "<x> <y>"),
            branch("confirm", "Control click confirmation mode.",
                    leaf("on", "Enable confirmation mode."),
                    leaf("off", "Disable confirmation mode.")),
            hint("<file.png>", "Load an imported PNG by filename.")
    );

    private CommandGuide() {
    }

    public static List<Entry> suggestions(String input) {
        String rest = commandRest(input);
        if (rest == null) {
            return List.of();
        }
        String lower = rest.toLowerCase(Locale.ROOT).trim();
        if (lower.startsWith("auto")) {
            return AUTO;
        }
        if (lower.startsWith("smart")) {
            return SMART;
        }
        if (lower.startsWith("bucket")) {
            return BUCKET;
        }
        if (lower.startsWith("palette")) {
            return PALETTE;
        }
        if (lower.startsWith("batch")) {
            return BATCH;
        }
        if (lower.startsWith("postpaint")) {
            return POSTPAINT;
        }
        if (lower.startsWith("rename")) {
            return RENAME;
        }
        if (lower.equals("pv2") || lower.startsWith("pv2 ")) {
            return PV2;
        }
        if (lower.equals("pv") || lower.startsWith("pv ")) {
            return PV;
        }
        if (lower.startsWith("android")) {
            return ANDROID;
        }
        if (lower.startsWith("calibration")) {
            return CALIBRATION;
        }
        if (lower.startsWith("calibrate")) {
            return CALIBRATE;
        }
        if (lower.startsWith("cal ")) {
            return CAL;
        }
        return ROOT;
    }

    public static List<Completion> chatSuggestions(String input) {
        PrefixInput parsed = PrefixInput.parse(input);
        if (parsed == null) {
            return List.of();
        }
        String rest = parsed.rest();
        boolean trailingSpace = !rest.isEmpty() && Character.isWhitespace(rest.charAt(rest.length() - 1));
        String normalized = rest.trim().toLowerCase(Locale.ROOT);
        String[] tokens = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        int consumed = trailingSpace ? tokens.length : Math.max(0, tokens.length - 1);
        String filter = trailingSpace || tokens.length == 0 ? "" : tokens[tokens.length - 1];
        List<Node> level = CHAT_ROOT;
        List<String> path = new java.util.ArrayList<>();
        Node current = null;
        for (int i = 0; i < consumed; i++) {
            String token = tokens[i];
            current = level.stream()
                    .filter(node -> node.token().equals(token))
                    .findFirst()
                    .orElse(null);
            if (current == null) {
                return List.of();
            }
            path.add(current.token());
            level = current.children();
        }
        if (trailingSpace && current != null && level.isEmpty()) {
            return placeholderSuggestions(parsed.prefix(), path, current);
        }
        String pathPrefix = path.isEmpty() ? parsed.prefix() : parsed.prefix() + " " + String.join(" ", path);
        return level.stream()
                .filter(node -> filter.isBlank() || node.token().startsWith(filter))
                .map(node -> completion(pathPrefix, node))
                .toList();
    }

    public static String firstCompletion(String input) {
        return chatSuggestions(input).stream()
                .filter(Completion::insertable)
                .map(Completion::insertion)
                .findFirst()
                .orElse(null);
    }

    public static String commandRest(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.startsWith("#painting")) {
            return trimmed.substring("#painting".length()).trim();
        }
        if (trimmed.startsWith("#paint")) {
            return trimmed.substring("#paint".length()).trim();
        }
        return null;
    }

    private static List<Entry> prefixed(String prefix, List<Entry> entries, String filter) {
        String normalized = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        return entries.stream()
                .filter(entry -> normalized.isBlank() || entry.command().toLowerCase(Locale.ROOT).startsWith(normalized))
                .map(entry -> new Entry(prefix + entry.command(), entry.description()))
                .collect(Collectors.toList());
    }

    private static Completion completion(String pathPrefix, Node node) {
        String base = pathPrefix + " " + node.token();
        String display = node.placeholders().isEmpty()
                ? base
                : base + " " + String.join(" | ", node.placeholders());
        return new Completion(display, node.insertable() ? base : null, node.description());
    }

    private static List<Completion> placeholderSuggestions(String prefix, List<String> path, Node node) {
        if (node.placeholders().isEmpty()) {
            return List.of();
        }
        String base = prefix + " " + String.join(" ", path);
        return node.placeholders().stream()
                .map(placeholder -> new Completion(base + " " + placeholder, null, node.description()))
                .toList();
    }

    private static Node leaf(String token, String description) {
        return new Node(token, description, List.of(), List.of(), true);
    }

    private static Node branch(String token, String description, Node... children) {
        return new Node(token, description, List.of(children), List.of(), true);
    }

    private static Node argument(String token, String description, String... placeholders) {
        return new Node(token, description, List.of(), List.of(placeholders), true);
    }

    private static Node hint(String display, String description) {
        return new Node(display, description, List.of(), List.of(), false);
    }

    public record Entry(String command, String description) {
    }

    public record Completion(String display, String insertion, String description) {
        public boolean insertable() {
            return insertion != null && !insertion.isBlank();
        }
    }

    private record Node(String token, String description, List<Node> children,
                        List<String> placeholders, boolean insertable) {
    }

    private record PrefixInput(String prefix, String rest) {
        private static PrefixInput parse(String input) {
            if (input == null) {
                return null;
            }
            String trimmed = input.stripLeading();
            if (!trimmed.startsWith("#")) {
                return null;
            }
            String prefix;
            if (matchesPrefix(trimmed, "#painting")) {
                prefix = "#painting";
            } else if (matchesPrefix(trimmed, "#paint")) {
                prefix = "#paint";
            } else if ("#painting".startsWith(trimmed) || "#paint".startsWith(trimmed)) {
                return new PrefixInput(trimmed.startsWith("#paint") ? "#paint" : "#painting", "");
            } else {
                return null;
            }
            return new PrefixInput(prefix, trimmed.substring(prefix.length()).stripLeading());
        }

        private static boolean matchesPrefix(String input, String prefix) {
            return input.startsWith(prefix)
                    && (input.length() == prefix.length()
                    || Character.isWhitespace(input.charAt(prefix.length())));
        }
    }
}
