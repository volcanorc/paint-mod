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

    public static List<Entry> chatSuggestions(String input) {
        if (input == null) {
            return List.of();
        }
        String trimmed = input.trim();
        if (!trimmed.startsWith("#") || trimmed.startsWith("#/")) {
            return List.of();
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("#painting") && !lower.startsWith("#paint")) {
            return "#painting".startsWith(lower) || "#paint".startsWith(lower)
                    ? fullRoot("")
                    : List.of();
        }
        String rest = commandRest(trimmed);
        if (rest == null) {
            return fullRoot("");
        }
        String normalized = rest.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("auto")) {
            return prefixed("#painting auto ", AUTO, normalized.substring("auto".length()).trim());
        }
        if (normalized.startsWith("smart")) {
            return prefixed("#painting smart ", SMART, normalized.substring("smart".length()).trim());
        }
        if (normalized.startsWith("bucket")) {
            return prefixed("#painting bucket ", BUCKET, normalized.substring("bucket".length()).trim());
        }
        if (normalized.startsWith("palette")) {
            return prefixed("#painting palette ", PALETTE, normalized.substring("palette".length()).trim());
        }
        if (normalized.startsWith("batch")) {
            return prefixed("#painting batch ", BATCH, normalized.substring("batch".length()).trim());
        }
        if (normalized.startsWith("postpaint")) {
            return prefixed("#painting postpaint ", POSTPAINT, normalized.substring("postpaint".length()).trim());
        }
        if (normalized.startsWith("rename")) {
            return prefixed("#painting rename ", RENAME, normalized.substring("rename".length()).trim());
        }
        if (normalized.equals("pv2") || normalized.startsWith("pv2 ")) {
            return prefixed("#painting pv2 ", PV2, normalized.substring("pv2".length()).trim());
        }
        if (normalized.equals("pv") || normalized.startsWith("pv ")) {
            return prefixed("#painting pv ", PV, normalized.substring("pv".length()).trim());
        }
        if (normalized.startsWith("android")) {
            return prefixed("#painting android ", ANDROID, normalized.substring("android".length()).trim());
        }
        if (normalized.startsWith("calibration")) {
            return prefixed("#painting calibration ", CALIBRATION, normalized.substring("calibration".length()).trim());
        }
        if (normalized.startsWith("calibrate")) {
            return prefixed("#painting calibrate ", CALIBRATE, normalized.substring("calibrate".length()).trim());
        }
        if (normalized.startsWith("cal")) {
            return prefixed("#painting cal ", CAL, normalized.substring("cal".length()).trim());
        }
        return fullRoot(normalized.trim());
    }

    public static String firstCompletion(String input) {
        List<Entry> entries = chatSuggestions(input);
        return entries.isEmpty() ? null : entries.getFirst().command();
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

    private static List<Entry> fullRoot(String filter) {
        return prefixed("#painting ", ROOT, filter);
    }

    private static List<Entry> prefixed(String prefix, List<Entry> entries, String filter) {
        String normalized = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        return entries.stream()
                .filter(entry -> normalized.isBlank() || entry.command().toLowerCase(Locale.ROOT).startsWith(normalized))
                .map(entry -> new Entry(prefix + entry.command(), entry.description()))
                .collect(Collectors.toList());
    }

    public record Entry(String command, String description) {
    }
}
