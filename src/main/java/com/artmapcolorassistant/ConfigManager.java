package com.artmapcolorassistant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class ConfigManager {
    public static final String MOD_ID = "artmap_color_assistant";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Config config = Config.defaults();
    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("artmap_color_assistant.json");
    private final Path importsPath = FabricLoader.getInstance().getGameDir().resolve("artmap_color_assistant").resolve("imports");
    private final Path calibrationsPath = FabricLoader.getInstance().getGameDir().resolve("artmap_color_assistant").resolve("calibrations");
    private final List<String> warnings = new ArrayList<>();
    private int serverColorOverridesApplied;
    private int serverColorOverridesSkipped;

    public Config config() {
        return config;
    }

    public Path importsPath() {
        return importsPath;
    }

    public Path configPath() {
        return configPath;
    }

    public Path calibrationsPath() {
        return calibrationsPath;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public int serverColorOverridesApplied() {
        return serverColorOverridesApplied;
    }

    public int serverColorOverridesSkipped() {
        return serverColorOverridesSkipped;
    }

    public void load(Consumer<Text> warningSink) {
        warnings.clear();
        try {
            Files.createDirectories(configPath.getParent());
            Files.createDirectories(importsPath);
            Files.createDirectories(calibrationsPath);
            if (Files.notExists(configPath)) {
                config = Config.defaults();
                save();
            }
            try (Reader reader = Files.newBufferedReader(configPath)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                config = parse(root);
            }
            save();
        } catch (Exception e) {
            warn("Failed to load ArtMapColorAssistant config, using defaults: " + e.getMessage(), warningSink);
            config = Config.defaults();
        }
    }

    public void save() throws IOException {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(toJson(config), writer);
        }
    }

    private Config parse(JsonObject root) {
        Config defaults = Config.defaults();
        int canvasWidth = intValue(root, "canvasWidth", defaults.canvasWidth);
        int canvasHeight = intValue(root, "canvasHeight", defaults.canvasHeight);
        int reservedHotbarSlot = clamp(intValue(root, "reservedHotbarSlot", defaults.reservedHotbarSlot), 0, 8);
        boolean autoSwapFromInventory = boolValue(root, "autoSwapFromInventory", defaults.autoSwapFromInventory);
        boolean advanceOnLeftClick = boolValue(root, "advanceOnLeftClick", defaults.advanceOnLeftClick);
        boolean advanceOnRightClick = boolValue(root, "advanceOnRightClick", defaults.advanceOnRightClick);
        boolean onlyAdvanceWhenCrosshairTargetExists = boolValue(root, "onlyAdvanceWhenCrosshairTargetExists", defaults.onlyAdvanceWhenCrosshairTargetExists);
        int alphaThreshold = clamp(intValue(root, "alphaThreshold", defaults.alphaThreshold), 0, 255);
        TransparentPixelMode transparentPixelMode = TransparentPixelMode.fromString(stringValue(root, "transparentPixelMode", defaults.transparentPixelMode.name()));
        boolean debug = boolValue(root, "debug", defaults.debug);
        boolean useOnlyInventoryAvailableColors = boolValue(root, "useOnlyInventoryAvailableColors", defaults.useOnlyInventoryAvailableColors);
        ColorMatchMode colorMatchMode = ColorMatchMode.fromString(stringValue(root, "colorMatchMode", defaults.colorMatchMode.name()));
        boolean includeToolsInColorMatching = boolValue(root, "includeToolsInColorMatching", defaults.includeToolsInColorMatching);
        boolean confirmMode = boolValue(root, "confirmMode", defaults.confirmMode);
        PaintingMode paintingMode = PaintingMode.fromString(stringValue(root, "paintingMode", defaults.paintingMode.name()));
        int autoPaintDefaultDelayTicks = Math.max(1, intValue(root, "autoPaintDefaultDelayTicks", defaults.autoPaintDefaultDelayTicks));
        int autoPaintMinDelayTicks = Math.max(1, intValue(root, "autoPaintMinDelayTicks", defaults.autoPaintMinDelayTicks));
        if (autoPaintMinDelayTicks == 20) {
            autoPaintMinDelayTicks = defaults.autoPaintMinDelayTicks;
        }
        AutoClickButton autoPaintClickButton = AutoClickButton.fromString(stringValue(root, "autoPaintClickButton", defaults.autoPaintClickButton.name()));
        int autoAimSettleTicks = Math.max(0, intValue(root, "autoAimSettleTicks", defaults.autoAimSettleTicks));
        double autoAimToleranceDegrees = Math.max(0.05D, doubleValue(root, "autoAimToleranceDegrees", defaults.autoAimToleranceDegrees));
        boolean autoRequireCalibration = boolValue(root, "autoRequireCalibration", defaults.autoRequireCalibration);
        boolean autoLockCameraDuringAuto = boolValue(root, "autoLockCameraDuringAuto", defaults.autoLockCameraDuringAuto);
        boolean portableExactCalibrationMode = boolValue(root, "portableExactCalibrationMode", defaults.portableExactCalibrationMode);
        boolean useBundledDirectionalCalibration = boolValue(root, "useBundledDirectionalCalibration", defaults.useBundledDirectionalCalibration);
        String defaultBundledCalibrationPrefix = sanitizeCalibrationName(stringValue(root, "defaultBundledCalibrationPrefix", defaults.defaultBundledCalibrationPrefix));
        boolean autoDetectCalibrationDirectionOnAutoStart = boolValue(root, "autoDetectCalibrationDirectionOnAutoStart", defaults.autoDetectCalibrationDirectionOnAutoStart);
        double cardinalDirectionToleranceDegrees = clampDouble(doubleValue(root, "cardinalDirectionToleranceDegrees", defaults.cardinalDirectionToleranceDegrees), 0.1D, 45.0D);
        boolean autoEnablePortableForBundledCalibration = boolValue(root, "autoEnablePortableForBundledCalibration", defaults.autoEnablePortableForBundledCalibration);
        boolean autoDragSameColorRuns = boolValue(root, "autoDragSameColorRuns", defaults.autoDragSameColorRuns);
        int autoDragMinRunLength = Math.max(2, intValue(root, "autoDragMinRunLength", defaults.autoDragMinRunLength));
        int autoDragPixelTicks = Math.max(1, intValue(root, "autoDragPixelTicks", defaults.autoDragPixelTicks));
        if (autoDragPixelTicks == 1) {
            autoDragPixelTicks = defaults.autoDragPixelTicks;
        }
        boolean autoDragRequireExactCalibration = boolValue(root, "autoDragRequireExactCalibration", defaults.autoDragRequireExactCalibration);
        int autoDragStartHoldTicks = Math.max(0, intValue(root, "autoDragStartHoldTicks", defaults.autoDragStartHoldTicks));
        int autoDragEndHoldTicks = Math.max(0, intValue(root, "autoDragEndHoldTicks", defaults.autoDragEndHoldTicks));
        boolean smartEnabled = boolValue(root, "smartEnabled", defaults.smartEnabled);
        SmartPaintMode smartMode = SmartPaintMode.fromString(stringValue(root, "smartMode", defaults.smartMode.name()));
        boolean smartBaseCoatEnabled = boolValue(root, "smartBaseCoatEnabled", defaults.smartBaseCoatEnabled);
        int smartBucketThreshold = Math.max(2, intValue(root, "smartBucketThreshold", defaults.smartBucketThreshold));
        int smartDragThreshold = Math.max(2, intValue(root, "smartDragThreshold", defaults.smartDragThreshold));
        boolean bucketEnabled = boolValue(root, "bucketEnabled", defaults.bucketEnabled);
        int bucketClickRepeats = clamp(intValue(root, "bucketClickRepeats", defaults.bucketClickRepeats), 1, 4);
        int bucketClickGapTicks = Math.max(0, intValue(root, "bucketClickGapTicks", defaults.bucketClickGapTicks));
        int bucketSwapDelayTicks = Math.max(0, intValue(root, "bucketSwapDelayTicks", defaults.bucketSwapDelayTicks));
        int bucketAimSettleTicks = Math.max(0, intValue(root, "bucketAimSettleTicks", defaults.bucketAimSettleTicks));
        int bucketAfterDelayTicks = Math.max(0, intValue(root, "bucketAfterDelayTicks", defaults.bucketAfterDelayTicks));
        String selectedCalibrationName = sanitizeCalibrationName(stringValue(root, "selectedCalibrationName", defaults.selectedCalibrationName));
        boolean serverColorOverridesEnabled = boolValue(root, "serverColorOverridesEnabled", defaults.serverColorOverridesEnabled);
        boolean batchAutoStartAfterContinue = boolValue(root, "batchAutoStartAfterContinue", defaults.batchAutoStartAfterContinue);
        int batchDefaultSpeedTicks = Math.max(1, intValue(root, "batchDefaultSpeedTicks", defaults.batchDefaultSpeedTicks));
        boolean batchEnableDrag = boolValue(root, "batchEnableDrag", defaults.batchEnableDrag);
        boolean postPaintAutomationEnabled = boolValue(root, "postPaintAutomationEnabled", defaults.postPaintAutomationEnabled);
        int postPaintSaveHotbarSlot = clamp(intValue(root, "postPaintSaveHotbarSlot", defaults.postPaintSaveHotbarSlot), 0, 8);
        int postPaintFinishedHotbarSlot = clamp(intValue(root, "postPaintFinishedHotbarSlot", defaults.postPaintFinishedHotbarSlot), 0, 8);
        int postPaintBlankCanvasHotbarSlot = clamp(intValue(root, "postPaintBlankCanvasHotbarSlot", defaults.postPaintBlankCanvasHotbarSlot), 0, 8);
        int postPaintAimCalibrationIndex = Math.max(0, intValue(root, "postPaintAimCalibrationIndex", defaults.postPaintAimCalibrationIndex));
        String postPaintVaultCommand = stringValue(root, "postPaintVaultCommand", defaults.postPaintVaultCommand);
        int postPaintSaveSelectDelayTicks = Math.max(0, intValue(root, "postPaintSaveSelectDelayTicks", defaults.postPaintSaveSelectDelayTicks));
        int postPaintSaveAimSettleTicks = Math.max(0, intValue(root, "postPaintSaveAimSettleTicks", defaults.postPaintSaveAimSettleTicks));
        int postPaintRenameOpenDelayTicks = Math.max(0, intValue(root, "postPaintRenameOpenDelayTicks", defaults.postPaintRenameOpenDelayTicks));
        int postPaintRightClickRetries = Math.max(0, intValue(root, "postPaintRightClickRetries", defaults.postPaintRightClickRetries));
        boolean postPaintFunJumpsEnabled = boolValue(root, "postPaintFunJumpsEnabled", defaults.postPaintFunJumpsEnabled);
        int postPaintFunJumpCount = Math.max(0, intValue(root, "postPaintFunJumpCount", defaults.postPaintFunJumpCount));
        int postPaintFunJumpPressTicks = Math.max(1, intValue(root, "postPaintFunJumpPressTicks", defaults.postPaintFunJumpPressTicks));
        int postPaintFunJumpGapTicks = Math.max(0, intValue(root, "postPaintFunJumpGapTicks", defaults.postPaintFunJumpGapTicks));
        RecordedClickPoint postPaintRenameClickPoint = parseClickPoint(root, "postPaintRenameClickPoint");
        RecordedClickPoint postPaintPv2ClickPoint = parseClickPoint(root, "postPaintPv2ClickPoint");

        List<ArtMapColor> colors = new ArrayList<>();
        JsonArray array = root.has("artMapColors") && root.get("artMapColors").isJsonArray()
                ? root.getAsJsonArray("artMapColors")
                : defaultColorJson();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            ArtMapColor color = parseColor(element.getAsJsonObject());
            if (color != null) {
                colors.add(color);
            }
        }
        if (colors.isEmpty()) {
            colors = defaults.artMapColors;
        }
        List<ServerColorOverride> serverColorOverrides = new ArrayList<>();
        JsonArray overrideArray = root.has("serverColorOverrides") && root.get("serverColorOverrides").isJsonArray()
                ? root.getAsJsonArray("serverColorOverrides")
                : defaultServerColorOverrideJson();
        serverColorOverridesSkipped = 0;
        for (JsonElement element : overrideArray) {
            if (!element.isJsonObject()) {
                serverColorOverridesSkipped++;
                continue;
            }
            ServerColorOverride override = parseServerColorOverride(element.getAsJsonObject());
            if (override == null) {
                serverColorOverridesSkipped++;
            } else {
                serverColorOverrides.add(override);
            }
        }
        OverrideResult overrideResult = applyServerColorOverrides(colors, serverColorOverridesEnabled, serverColorOverrides);
        serverColorOverridesApplied = overrideResult.applied();
        return new Config(canvasWidth, canvasHeight, reservedHotbarSlot, autoSwapFromInventory,
                advanceOnLeftClick, advanceOnRightClick, onlyAdvanceWhenCrosshairTargetExists,
                alphaThreshold, transparentPixelMode, debug, useOnlyInventoryAvailableColors,
                colorMatchMode, includeToolsInColorMatching, confirmMode, paintingMode, autoPaintDefaultDelayTicks,
                autoPaintMinDelayTicks, autoPaintClickButton, autoAimSettleTicks,
                autoAimToleranceDegrees, autoRequireCalibration, autoLockCameraDuringAuto, portableExactCalibrationMode,
                useBundledDirectionalCalibration, defaultBundledCalibrationPrefix,
                autoDetectCalibrationDirectionOnAutoStart, cardinalDirectionToleranceDegrees,
                autoEnablePortableForBundledCalibration, autoDragSameColorRuns,
                autoDragMinRunLength, autoDragPixelTicks, autoDragRequireExactCalibration,
                autoDragStartHoldTicks, autoDragEndHoldTicks, smartEnabled, smartMode,
                smartBaseCoatEnabled, smartBucketThreshold, smartDragThreshold, bucketEnabled,
                bucketClickRepeats, bucketClickGapTicks, bucketSwapDelayTicks, bucketAimSettleTicks,
                bucketAfterDelayTicks, selectedCalibrationName,
                serverColorOverridesEnabled, List.copyOf(serverColorOverrides),
                batchAutoStartAfterContinue, batchDefaultSpeedTicks, batchEnableDrag,
                postPaintAutomationEnabled, postPaintSaveHotbarSlot, postPaintFinishedHotbarSlot,
                postPaintBlankCanvasHotbarSlot, postPaintAimCalibrationIndex, postPaintVaultCommand,
                postPaintSaveSelectDelayTicks, postPaintSaveAimSettleTicks, postPaintRenameOpenDelayTicks,
                postPaintRightClickRetries, postPaintFunJumpsEnabled, postPaintFunJumpCount,
                postPaintFunJumpPressTicks, postPaintFunJumpGapTicks, postPaintRenameClickPoint,
                postPaintPv2ClickPoint, List.copyOf(colors),
                overrideResult.colors());
    }

    private ArtMapColor parseColor(JsonObject object) {
        String name = stringValue(object, "name", "UNKNOWN");
        String bukkitMaterial = stringValue(object, "bukkitMaterial", "");
        Identifier item = parseIdentifier(stringValue(object, "item", null));
        if (item == null || !Registries.ITEM.containsId(item)) {
            warnings.add("Skipping ArtMap color " + name + ": invalid item ID " + stringValue(object, "item", ""));
            return null;
        }
        Identifier legacyItem = parseIdentifier(stringValue(object, "legacyItem", null));
        if (legacyItem != null && !Registries.ITEM.containsId(legacyItem)) {
            warnings.add("Ignoring invalid legacyItem for " + name + ": " + legacyItem);
            legacyItem = null;
        }
        int rgb;
        try {
            rgb = RgbUtil.parseHex(stringValue(object, "rgb", "#000000"));
        } catch (IllegalArgumentException e) {
            warnings.add("Skipping ArtMap color " + name + ": invalid RGB");
            return null;
        }
        boolean tool = boolValue(object, "tool", false);
        return new ArtMapColor(name, bukkitMaterial, item, legacyItem, rgb, tool);
    }

    private ServerColorOverride parseServerColorOverride(JsonObject object) {
        Identifier item = parseIdentifier(stringValue(object, "item", null));
        if (item == null || !Registries.ITEM.containsId(item)) {
            warnings.add("Skipping server color override: invalid item ID " + stringValue(object, "item", ""));
            return null;
        }
        int rgb;
        try {
            rgb = RgbUtil.parseHex(stringValue(object, "rgb", "#000000"));
        } catch (IllegalArgumentException e) {
            warnings.add("Skipping server color override for " + item + ": invalid RGB");
            return null;
        }
        return new ServerColorOverride(item, rgb);
    }

    public JsonObject toJson(Config value) {
        JsonObject root = new JsonObject();
        root.addProperty("canvasWidth", value.canvasWidth);
        root.addProperty("canvasHeight", value.canvasHeight);
        root.addProperty("reservedHotbarSlot", value.reservedHotbarSlot);
        root.addProperty("autoSwapFromInventory", value.autoSwapFromInventory);
        root.addProperty("advanceOnLeftClick", value.advanceOnLeftClick);
        root.addProperty("advanceOnRightClick", value.advanceOnRightClick);
        root.addProperty("onlyAdvanceWhenCrosshairTargetExists", value.onlyAdvanceWhenCrosshairTargetExists);
        root.addProperty("alphaThreshold", value.alphaThreshold);
        root.addProperty("transparentPixelMode", value.transparentPixelMode.name());
        root.addProperty("debug", value.debug);
        root.addProperty("useOnlyInventoryAvailableColors", value.useOnlyInventoryAvailableColors);
        root.addProperty("colorMatchMode", value.colorMatchMode.name());
        root.addProperty("includeToolsInColorMatching", value.includeToolsInColorMatching);
        root.addProperty("confirmMode", value.confirmMode);
        root.addProperty("paintingMode", value.paintingMode.name());
        root.addProperty("autoPaintDefaultDelayTicks", value.autoPaintDefaultDelayTicks);
        root.addProperty("autoPaintMinDelayTicks", value.autoPaintMinDelayTicks);
        root.addProperty("autoPaintClickButton", value.autoPaintClickButton.name());
        root.addProperty("autoAimSettleTicks", value.autoAimSettleTicks);
        root.addProperty("autoAimToleranceDegrees", value.autoAimToleranceDegrees);
        root.addProperty("autoRequireCalibration", value.autoRequireCalibration);
        root.addProperty("autoLockCameraDuringAuto", value.autoLockCameraDuringAuto);
        root.addProperty("portableExactCalibrationMode", value.portableExactCalibrationMode);
        root.addProperty("useBundledDirectionalCalibration", value.useBundledDirectionalCalibration);
        root.addProperty("defaultBundledCalibrationPrefix", value.defaultBundledCalibrationPrefix);
        root.addProperty("autoDetectCalibrationDirectionOnAutoStart", value.autoDetectCalibrationDirectionOnAutoStart);
        root.addProperty("cardinalDirectionToleranceDegrees", value.cardinalDirectionToleranceDegrees);
        root.addProperty("autoEnablePortableForBundledCalibration", value.autoEnablePortableForBundledCalibration);
        root.addProperty("autoDragSameColorRuns", value.autoDragSameColorRuns);
        root.addProperty("autoDragMinRunLength", value.autoDragMinRunLength);
        root.addProperty("autoDragPixelTicks", value.autoDragPixelTicks);
        root.addProperty("autoDragRequireExactCalibration", value.autoDragRequireExactCalibration);
        root.addProperty("autoDragStartHoldTicks", value.autoDragStartHoldTicks);
        root.addProperty("autoDragEndHoldTicks", value.autoDragEndHoldTicks);
        root.addProperty("smartEnabled", value.smartEnabled);
        root.addProperty("smartMode", value.smartMode.name());
        root.addProperty("smartBaseCoatEnabled", value.smartBaseCoatEnabled);
        root.addProperty("smartBucketThreshold", value.smartBucketThreshold);
        root.addProperty("smartDragThreshold", value.smartDragThreshold);
        root.addProperty("bucketEnabled", value.bucketEnabled);
        root.addProperty("bucketClickRepeats", value.bucketClickRepeats);
        root.addProperty("bucketClickGapTicks", value.bucketClickGapTicks);
        root.addProperty("bucketSwapDelayTicks", value.bucketSwapDelayTicks);
        root.addProperty("bucketAimSettleTicks", value.bucketAimSettleTicks);
        root.addProperty("bucketAfterDelayTicks", value.bucketAfterDelayTicks);
        root.addProperty("selectedCalibrationName", value.selectedCalibrationName);
        root.addProperty("serverColorOverridesEnabled", value.serverColorOverridesEnabled);
        root.addProperty("batchAutoStartAfterContinue", value.batchAutoStartAfterContinue);
        root.addProperty("batchDefaultSpeedTicks", value.batchDefaultSpeedTicks);
        root.addProperty("batchEnableDrag", value.batchEnableDrag);
        root.addProperty("postPaintAutomationEnabled", value.postPaintAutomationEnabled);
        root.addProperty("postPaintSaveHotbarSlot", value.postPaintSaveHotbarSlot);
        root.addProperty("postPaintFinishedHotbarSlot", value.postPaintFinishedHotbarSlot);
        root.addProperty("postPaintBlankCanvasHotbarSlot", value.postPaintBlankCanvasHotbarSlot);
        root.addProperty("postPaintAimCalibrationIndex", value.postPaintAimCalibrationIndex);
        root.addProperty("postPaintVaultCommand", value.postPaintVaultCommand);
        root.addProperty("postPaintSaveSelectDelayTicks", value.postPaintSaveSelectDelayTicks);
        root.addProperty("postPaintSaveAimSettleTicks", value.postPaintSaveAimSettleTicks);
        root.addProperty("postPaintRenameOpenDelayTicks", value.postPaintRenameOpenDelayTicks);
        root.addProperty("postPaintRightClickRetries", value.postPaintRightClickRetries);
        root.addProperty("postPaintFunJumpsEnabled", value.postPaintFunJumpsEnabled);
        root.addProperty("postPaintFunJumpCount", value.postPaintFunJumpCount);
        root.addProperty("postPaintFunJumpPressTicks", value.postPaintFunJumpPressTicks);
        root.addProperty("postPaintFunJumpGapTicks", value.postPaintFunJumpGapTicks);
        addClickPoint(root, "postPaintRenameClickPoint", value.postPaintRenameClickPoint);
        addClickPoint(root, "postPaintPv2ClickPoint", value.postPaintPv2ClickPoint);
        JsonArray colors = new JsonArray();
        for (ArtMapColor color : value.artMapColors) {
            JsonObject object = new JsonObject();
            object.addProperty("name", color.name());
            object.addProperty("bukkitMaterial", color.bukkitMaterial());
            object.addProperty("item", color.item().toString());
            if (color.legacyItem() != null) {
                object.addProperty("legacyItem", color.legacyItem().toString());
            }
            object.addProperty("rgb", RgbUtil.toHex(color.rgb()));
            object.addProperty("tool", color.tool());
            colors.add(object);
        }
        root.add("artMapColors", colors);
        JsonArray overrides = new JsonArray();
        for (ServerColorOverride override : value.serverColorOverrides) {
            JsonObject object = new JsonObject();
            object.addProperty("item", override.item().toString());
            object.addProperty("rgb", RgbUtil.toHex(override.rgb()));
            overrides.add(object);
        }
        root.add("serverColorOverrides", overrides);
        return root;
    }

    private void warn(String warning, Consumer<Text> warningSink) {
        warnings.add(warning);
        if (warningSink != null) {
            warningSink.accept(Text.literal("[ArtMap] " + warning));
        }
    }

    public void setSelectedCalibrationName(String rawName, Consumer<Text> warningSink) {
        String name = sanitizeCalibrationName(rawName);
        config = config.withSelectedCalibrationName(name);
        try {
            save();
        } catch (IOException e) {
            warn("Failed to save selected calibration '" + name + "': " + e.getMessage(), warningSink);
        }
    }

    public void setPostPaintAutomationEnabled(boolean enabled, Consumer<Text> warningSink) {
        config = config.withPostPaintAutomationEnabled(enabled);
        saveConfigChange("post-paint automation", warningSink);
    }

    public void setPortableExactCalibrationMode(boolean enabled, Consumer<Text> warningSink) {
        config = config.withPortableExactCalibrationMode(enabled);
        saveConfigChange("portable exact calibration mode", warningSink);
    }

    public void setPaintingMode(PaintingMode mode, Consumer<Text> warningSink) {
        config = config.withPaintingModePreset(mode == null ? PaintingMode.SMART : mode);
        saveConfigChange("painting mode", warningSink);
    }

    public void setAutoDragSameColorRuns(boolean enabled, Consumer<Text> warningSink) {
        config = config.withAutoDragSameColorRuns(enabled);
        saveConfigChange("auto drag", warningSink);
    }

    public void setSmartEnabled(boolean enabled, Consumer<Text> warningSink) {
        config = config.withSmartSettings(enabled, config.smartMode(), config.smartBaseCoatEnabled(),
                config.smartBucketThreshold(), config.smartDragThreshold());
        saveConfigChange("smart mode", warningSink);
    }

    public void setSmartBaseCoatEnabled(boolean enabled, Consumer<Text> warningSink) {
        config = config.withSmartSettings(config.smartEnabled(), config.smartMode(), enabled,
                config.smartBucketThreshold(), config.smartDragThreshold());
        saveConfigChange("smart basecoat", warningSink);
    }

    public void setSmartBucketThreshold(int value, Consumer<Text> warningSink) {
        config = config.withSmartSettings(config.smartEnabled(), config.smartMode(), config.smartBaseCoatEnabled(),
                Math.max(2, value), config.smartDragThreshold());
        saveConfigChange("smart bucket threshold", warningSink);
    }

    public void setSmartDragThreshold(int value, Consumer<Text> warningSink) {
        config = config.withSmartSettings(config.smartEnabled(), config.smartMode(), config.smartBaseCoatEnabled(),
                config.smartBucketThreshold(), Math.max(2, value));
        saveConfigChange("smart drag threshold", warningSink);
    }

    public void setBucketEnabled(boolean enabled, Consumer<Text> warningSink) {
        config = config.withBucketSettings(enabled, config.bucketClickRepeats(), config.bucketClickGapTicks(),
                config.bucketSwapDelayTicks(), config.bucketAimSettleTicks(), config.bucketAfterDelayTicks());
        saveConfigChange("bucket mode", warningSink);
    }

    public void setBucketClickRepeats(int value, Consumer<Text> warningSink) {
        config = config.withBucketSettings(config.bucketEnabled(), clamp(value, 1, 4), config.bucketClickGapTicks(),
                config.bucketSwapDelayTicks(), config.bucketAimSettleTicks(), config.bucketAfterDelayTicks());
        saveConfigChange("bucket click repeats", warningSink);
    }

    public void setBucketClickGapTicks(int value, Consumer<Text> warningSink) {
        config = config.withBucketSettings(config.bucketEnabled(), config.bucketClickRepeats(), Math.max(0, value),
                config.bucketSwapDelayTicks(), config.bucketAimSettleTicks(), config.bucketAfterDelayTicks());
        saveConfigChange("bucket click gap", warningSink);
    }

    public void setBucketSwapDelayTicks(int value, Consumer<Text> warningSink) {
        config = config.withBucketSettings(config.bucketEnabled(), config.bucketClickRepeats(), config.bucketClickGapTicks(),
                Math.max(0, value), config.bucketAimSettleTicks(), config.bucketAfterDelayTicks());
        saveConfigChange("bucket swap delay", warningSink);
    }

    public void setBucketAfterDelayTicks(int value, Consumer<Text> warningSink) {
        config = config.withBucketSettings(config.bucketEnabled(), config.bucketClickRepeats(), config.bucketClickGapTicks(),
                config.bucketSwapDelayTicks(), config.bucketAimSettleTicks(), Math.max(0, value));
        saveConfigChange("bucket after delay", warningSink);
    }

    public void setPostPaintRenameClickPoint(RecordedClickPoint point, Consumer<Text> warningSink) {
        config = config.withPostPaintRenameClickPoint(point);
        saveConfigChange("rename click point", warningSink);
    }

    public void setPostPaintPv2ClickPoint(RecordedClickPoint point, Consumer<Text> warningSink) {
        config = config.withPostPaintPv2ClickPoint(point);
        saveConfigChange("PV2 click point", warningSink);
    }

    private void saveConfigChange(String label, Consumer<Text> warningSink) {
        try {
            save();
        } catch (IOException e) {
            warn("Failed to save " + label + ": " + e.getMessage(), warningSink);
        }
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        try {
            return object != null && object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean boolValue(JsonObject object, String key, boolean fallback) {
        try {
            return object != null && object.has(key) ? object.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double doubleValue(JsonObject object, String key, double fallback) {
        try {
            return object != null && object.has(key) ? object.get(key).getAsDouble() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        try {
            return object != null && object.has(key) ? object.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static RecordedClickPoint parseClickPoint(JsonObject root, String key) {
        try {
            if (root == null || !root.has(key) || !root.get(key).isJsonObject()) {
                return null;
            }
            JsonObject object = root.getAsJsonObject(key);
            return new RecordedClickPoint(
                    doubleValue(object, "x", 0.0D),
                    doubleValue(object, "y", 0.0D),
                    doubleValue(object, "normalizedX", 0.0D),
                    doubleValue(object, "normalizedY", 0.0D),
                    intValue(object, "button", 0),
                    stringValue(object, "source", "cursor")
            );
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void addClickPoint(JsonObject root, String key, RecordedClickPoint point) {
        if (point == null) {
            return;
        }
        JsonObject object = new JsonObject();
        object.addProperty("x", point.x());
        object.addProperty("y", point.y());
        object.addProperty("normalizedX", point.normalizedX());
        object.addProperty("normalizedY", point.normalizedY());
        object.addProperty("button", point.button());
        object.addProperty("source", point.source());
        root.add(key, object);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Identifier parseIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Identifier.of(value.toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String sanitizeCalibrationName(String value) {
        if (value == null) {
            return "1";
        }
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return normalized.isBlank() ? "1" : normalized;
    }

    static OverrideResult applyServerColorOverrides(List<ArtMapColor> baseColors, boolean enabled, List<ServerColorOverride> overrides) {
        List<ArtMapColor> effective = new ArrayList<>(baseColors);
        if (!enabled) {
            return new OverrideResult(List.copyOf(effective), 0);
        }
        int applied = 0;
        for (ServerColorOverride override : overrides) {
            int existingIndex = findColorIndex(effective, override.item());
            if (existingIndex >= 0) {
                ArtMapColor existing = effective.get(existingIndex);
                effective.set(existingIndex, new ArtMapColor(
                        existing.name(),
                        existing.bukkitMaterial(),
                        existing.item(),
                        existing.legacyItem(),
                        override.rgb(),
                        existing.tool()
                ));
            } else {
                effective.add(new ArtMapColor(
                        readableName(override.item()),
                        bukkitMaterialName(override.item()),
                        override.item(),
                        null,
                        override.rgb(),
                        false
                ));
            }
            applied++;
        }
        return new OverrideResult(List.copyOf(effective), applied);
    }

    private static int findColorIndex(List<ArtMapColor> colors, Identifier item) {
        for (int i = 0; i < colors.size(); i++) {
            ArtMapColor color = colors.get(i);
            if (color.item().equals(item)) {
                return i;
            }
        }
        return -1;
    }

    private static String readableName(Identifier item) {
        return item.getPath().toUpperCase(Locale.ROOT);
    }

    private static String bukkitMaterialName(Identifier item) {
        return item.getPath().toUpperCase(Locale.ROOT);
    }

    public record Config(
            int canvasWidth,
            int canvasHeight,
            int reservedHotbarSlot,
            boolean autoSwapFromInventory,
            boolean advanceOnLeftClick,
            boolean advanceOnRightClick,
            boolean onlyAdvanceWhenCrosshairTargetExists,
            int alphaThreshold,
            TransparentPixelMode transparentPixelMode,
            boolean debug,
            boolean useOnlyInventoryAvailableColors,
            ColorMatchMode colorMatchMode,
            boolean includeToolsInColorMatching,
            boolean confirmMode,
            PaintingMode paintingMode,
            int autoPaintDefaultDelayTicks,
            int autoPaintMinDelayTicks,
            AutoClickButton autoPaintClickButton,
            int autoAimSettleTicks,
            double autoAimToleranceDegrees,
            boolean autoRequireCalibration,
            boolean autoLockCameraDuringAuto,
            boolean portableExactCalibrationMode,
            boolean useBundledDirectionalCalibration,
            String defaultBundledCalibrationPrefix,
            boolean autoDetectCalibrationDirectionOnAutoStart,
            double cardinalDirectionToleranceDegrees,
            boolean autoEnablePortableForBundledCalibration,
            boolean autoDragSameColorRuns,
            int autoDragMinRunLength,
            int autoDragPixelTicks,
            boolean autoDragRequireExactCalibration,
            int autoDragStartHoldTicks,
            int autoDragEndHoldTicks,
            boolean smartEnabled,
            SmartPaintMode smartMode,
            boolean smartBaseCoatEnabled,
            int smartBucketThreshold,
            int smartDragThreshold,
            boolean bucketEnabled,
            int bucketClickRepeats,
            int bucketClickGapTicks,
            int bucketSwapDelayTicks,
            int bucketAimSettleTicks,
            int bucketAfterDelayTicks,
            String selectedCalibrationName,
            boolean serverColorOverridesEnabled,
            List<ServerColorOverride> serverColorOverrides,
            boolean batchAutoStartAfterContinue,
            int batchDefaultSpeedTicks,
            boolean batchEnableDrag,
            boolean postPaintAutomationEnabled,
            int postPaintSaveHotbarSlot,
            int postPaintFinishedHotbarSlot,
            int postPaintBlankCanvasHotbarSlot,
            int postPaintAimCalibrationIndex,
            String postPaintVaultCommand,
            int postPaintSaveSelectDelayTicks,
            int postPaintSaveAimSettleTicks,
            int postPaintRenameOpenDelayTicks,
            int postPaintRightClickRetries,
            boolean postPaintFunJumpsEnabled,
            int postPaintFunJumpCount,
            int postPaintFunJumpPressTicks,
            int postPaintFunJumpGapTicks,
            RecordedClickPoint postPaintRenameClickPoint,
            RecordedClickPoint postPaintPv2ClickPoint,
            List<ArtMapColor> artMapColors,
            List<ArtMapColor> effectiveArtMapColors
    ) {
        public List<ArtMapColor> effectiveArtMapColors() {
            return effectiveArtMapColors;
        }

        public Config withSelectedCalibrationName(String value) {
            return new Config(canvasWidth, canvasHeight, reservedHotbarSlot, autoSwapFromInventory,
                    advanceOnLeftClick, advanceOnRightClick, onlyAdvanceWhenCrosshairTargetExists,
                    alphaThreshold, transparentPixelMode, debug, useOnlyInventoryAvailableColors, colorMatchMode,
                    includeToolsInColorMatching, confirmMode, paintingMode, autoPaintDefaultDelayTicks,
                    autoPaintMinDelayTicks, autoPaintClickButton, autoAimSettleTicks,
                    autoAimToleranceDegrees, autoRequireCalibration, autoLockCameraDuringAuto, portableExactCalibrationMode,
                    useBundledDirectionalCalibration, defaultBundledCalibrationPrefix,
                    autoDetectCalibrationDirectionOnAutoStart, cardinalDirectionToleranceDegrees,
                    autoEnablePortableForBundledCalibration, autoDragSameColorRuns,
                    autoDragMinRunLength, autoDragPixelTicks, autoDragRequireExactCalibration,
                    autoDragStartHoldTicks, autoDragEndHoldTicks, smartEnabled, smartMode,
                    smartBaseCoatEnabled, smartBucketThreshold, smartDragThreshold, bucketEnabled,
                    bucketClickRepeats, bucketClickGapTicks, bucketSwapDelayTicks, bucketAimSettleTicks,
                    bucketAfterDelayTicks, ConfigManager.sanitizeCalibrationName(value),
                    serverColorOverridesEnabled, serverColorOverrides, batchAutoStartAfterContinue,
                    batchDefaultSpeedTicks, batchEnableDrag, postPaintAutomationEnabled,
                    postPaintSaveHotbarSlot, postPaintFinishedHotbarSlot, postPaintBlankCanvasHotbarSlot,
                    postPaintAimCalibrationIndex, postPaintVaultCommand, postPaintSaveSelectDelayTicks,
                    postPaintSaveAimSettleTicks, postPaintRenameOpenDelayTicks, postPaintRightClickRetries,
                    postPaintFunJumpsEnabled, postPaintFunJumpCount, postPaintFunJumpPressTicks,
                    postPaintFunJumpGapTicks, postPaintRenameClickPoint,
                    postPaintPv2ClickPoint, artMapColors, effectiveArtMapColors);
        }

        public Config withPostPaintAutomationEnabled(boolean value) {
            return copy(value, postPaintRenameClickPoint, postPaintPv2ClickPoint);
        }

        public Config withPortableExactCalibrationMode(boolean value) {
            return new Config(canvasWidth, canvasHeight, reservedHotbarSlot, autoSwapFromInventory,
                    advanceOnLeftClick, advanceOnRightClick, onlyAdvanceWhenCrosshairTargetExists,
                    alphaThreshold, transparentPixelMode, debug, useOnlyInventoryAvailableColors, colorMatchMode,
                    includeToolsInColorMatching, confirmMode, paintingMode, autoPaintDefaultDelayTicks,
                    autoPaintMinDelayTicks, autoPaintClickButton, autoAimSettleTicks,
                    autoAimToleranceDegrees, autoRequireCalibration, autoLockCameraDuringAuto, value,
                    useBundledDirectionalCalibration, defaultBundledCalibrationPrefix,
                    autoDetectCalibrationDirectionOnAutoStart, cardinalDirectionToleranceDegrees,
                    autoEnablePortableForBundledCalibration, autoDragSameColorRuns,
                    autoDragMinRunLength, autoDragPixelTicks, autoDragRequireExactCalibration,
                    autoDragStartHoldTicks, autoDragEndHoldTicks, smartEnabled, smartMode,
                    smartBaseCoatEnabled, smartBucketThreshold, smartDragThreshold, bucketEnabled,
                    bucketClickRepeats, bucketClickGapTicks, bucketSwapDelayTicks, bucketAimSettleTicks,
                    bucketAfterDelayTicks, selectedCalibrationName,
                    serverColorOverridesEnabled, serverColorOverrides, batchAutoStartAfterContinue,
                    batchDefaultSpeedTicks, batchEnableDrag, postPaintAutomationEnabled,
                    postPaintSaveHotbarSlot, postPaintFinishedHotbarSlot, postPaintBlankCanvasHotbarSlot,
                    postPaintAimCalibrationIndex, postPaintVaultCommand, postPaintSaveSelectDelayTicks,
                    postPaintSaveAimSettleTicks, postPaintRenameOpenDelayTicks, postPaintRightClickRetries,
                    postPaintFunJumpsEnabled, postPaintFunJumpCount, postPaintFunJumpPressTicks,
                    postPaintFunJumpGapTicks, postPaintRenameClickPoint,
                    postPaintPv2ClickPoint, artMapColors, effectiveArtMapColors);
        }

        public Config withPaintingModePreset(PaintingMode mode) {
            PaintingMode nextMode = mode == null ? PaintingMode.SMART : mode;
            boolean nextSmart = nextMode == PaintingMode.SMART;
            boolean nextBucket = nextMode == PaintingMode.SMART;
            boolean nextDrag = nextMode != PaintingMode.MANUAL;
            boolean nextPostPaint = nextMode == PaintingMode.MANUAL ? postPaintAutomationEnabled : true;
            int nextSpeed = 5;
            return new Config(canvasWidth, canvasHeight, reservedHotbarSlot, true,
                    advanceOnLeftClick, advanceOnRightClick, onlyAdvanceWhenCrosshairTargetExists,
                    alphaThreshold, transparentPixelMode, debug, useOnlyInventoryAvailableColors, colorMatchMode,
                    includeToolsInColorMatching, confirmMode, nextMode, nextSpeed,
                    nextSpeed, autoPaintClickButton, autoAimSettleTicks,
                    autoAimToleranceDegrees, autoRequireCalibration, autoLockCameraDuringAuto, portableExactCalibrationMode,
                    useBundledDirectionalCalibration, defaultBundledCalibrationPrefix,
                    autoDetectCalibrationDirectionOnAutoStart, cardinalDirectionToleranceDegrees,
                    autoEnablePortableForBundledCalibration, nextDrag,
                    autoDragMinRunLength, autoDragPixelTicks, autoDragRequireExactCalibration,
                    autoDragStartHoldTicks, autoDragEndHoldTicks, nextSmart, smartMode,
                    smartBaseCoatEnabled, smartBucketThreshold, smartDragThreshold, nextBucket,
                    bucketClickRepeats, bucketClickGapTicks, bucketSwapDelayTicks, bucketAimSettleTicks,
                    bucketAfterDelayTicks, "ee",
                    serverColorOverridesEnabled, serverColorOverrides, batchAutoStartAfterContinue,
                    nextSpeed, nextDrag, nextPostPaint,
                    postPaintSaveHotbarSlot, postPaintFinishedHotbarSlot, postPaintBlankCanvasHotbarSlot,
                    postPaintAimCalibrationIndex, postPaintVaultCommand, postPaintSaveSelectDelayTicks,
                    postPaintSaveAimSettleTicks, postPaintRenameOpenDelayTicks, postPaintRightClickRetries,
                    postPaintFunJumpsEnabled, postPaintFunJumpCount, postPaintFunJumpPressTicks,
                    postPaintFunJumpGapTicks, postPaintRenameClickPoint,
                    postPaintPv2ClickPoint, artMapColors, effectiveArtMapColors);
        }

        public Config withAutoDragSameColorRuns(boolean value) {
            return new Config(canvasWidth, canvasHeight, reservedHotbarSlot, autoSwapFromInventory,
                    advanceOnLeftClick, advanceOnRightClick, onlyAdvanceWhenCrosshairTargetExists,
                    alphaThreshold, transparentPixelMode, debug, useOnlyInventoryAvailableColors, colorMatchMode,
                    includeToolsInColorMatching, confirmMode, paintingMode, autoPaintDefaultDelayTicks,
                    autoPaintMinDelayTicks, autoPaintClickButton, autoAimSettleTicks,
                    autoAimToleranceDegrees, autoRequireCalibration, autoLockCameraDuringAuto, portableExactCalibrationMode,
                    useBundledDirectionalCalibration, defaultBundledCalibrationPrefix,
                    autoDetectCalibrationDirectionOnAutoStart, cardinalDirectionToleranceDegrees,
                    autoEnablePortableForBundledCalibration, value,
                    autoDragMinRunLength, autoDragPixelTicks, autoDragRequireExactCalibration,
                    autoDragStartHoldTicks, autoDragEndHoldTicks, smartEnabled, smartMode,
                    smartBaseCoatEnabled, smartBucketThreshold, smartDragThreshold, bucketEnabled,
                    bucketClickRepeats, bucketClickGapTicks, bucketSwapDelayTicks, bucketAimSettleTicks,
                    bucketAfterDelayTicks, selectedCalibrationName,
                    serverColorOverridesEnabled, serverColorOverrides, batchAutoStartAfterContinue,
                    batchDefaultSpeedTicks, value, postPaintAutomationEnabled,
                    postPaintSaveHotbarSlot, postPaintFinishedHotbarSlot, postPaintBlankCanvasHotbarSlot,
                    postPaintAimCalibrationIndex, postPaintVaultCommand, postPaintSaveSelectDelayTicks,
                    postPaintSaveAimSettleTicks, postPaintRenameOpenDelayTicks, postPaintRightClickRetries,
                    postPaintFunJumpsEnabled, postPaintFunJumpCount, postPaintFunJumpPressTicks,
                    postPaintFunJumpGapTicks, postPaintRenameClickPoint,
                    postPaintPv2ClickPoint, artMapColors, effectiveArtMapColors);
        }

        public Config withPostPaintRenameClickPoint(RecordedClickPoint value) {
            return copy(postPaintAutomationEnabled, value, postPaintPv2ClickPoint);
        }

        public Config withPostPaintPv2ClickPoint(RecordedClickPoint value) {
            return copy(postPaintAutomationEnabled, postPaintRenameClickPoint, value);
        }

        public Config withSmartSettings(boolean enabled, SmartPaintMode mode, boolean baseCoatEnabled,
                                        int bucketThreshold, int dragThreshold) {
            return new Config(canvasWidth, canvasHeight, reservedHotbarSlot, autoSwapFromInventory,
                    advanceOnLeftClick, advanceOnRightClick, onlyAdvanceWhenCrosshairTargetExists,
                    alphaThreshold, transparentPixelMode, debug, useOnlyInventoryAvailableColors, colorMatchMode,
                    includeToolsInColorMatching, confirmMode, paintingMode, autoPaintDefaultDelayTicks,
                    autoPaintMinDelayTicks, autoPaintClickButton, autoAimSettleTicks,
                    autoAimToleranceDegrees, autoRequireCalibration, autoLockCameraDuringAuto, portableExactCalibrationMode,
                    useBundledDirectionalCalibration, defaultBundledCalibrationPrefix,
                    autoDetectCalibrationDirectionOnAutoStart, cardinalDirectionToleranceDegrees,
                    autoEnablePortableForBundledCalibration, autoDragSameColorRuns,
                    autoDragMinRunLength, autoDragPixelTicks, autoDragRequireExactCalibration,
                    autoDragStartHoldTicks, autoDragEndHoldTicks, enabled, mode,
                    baseCoatEnabled, Math.max(2, bucketThreshold), Math.max(2, dragThreshold), bucketEnabled,
                    bucketClickRepeats, bucketClickGapTicks, bucketSwapDelayTicks, bucketAimSettleTicks,
                    bucketAfterDelayTicks, selectedCalibrationName,
                    serverColorOverridesEnabled, serverColorOverrides, batchAutoStartAfterContinue,
                    batchDefaultSpeedTicks, batchEnableDrag, postPaintAutomationEnabled,
                    postPaintSaveHotbarSlot, postPaintFinishedHotbarSlot, postPaintBlankCanvasHotbarSlot,
                    postPaintAimCalibrationIndex, postPaintVaultCommand, postPaintSaveSelectDelayTicks,
                    postPaintSaveAimSettleTicks, postPaintRenameOpenDelayTicks, postPaintRightClickRetries,
                    postPaintFunJumpsEnabled, postPaintFunJumpCount, postPaintFunJumpPressTicks,
                    postPaintFunJumpGapTicks, postPaintRenameClickPoint,
                    postPaintPv2ClickPoint, artMapColors, effectiveArtMapColors);
        }

        public Config withBucketSettings(boolean enabled, int repeats, int gapTicks, int swapDelayTicks,
                                         int aimSettleTicks, int afterDelayTicks) {
            return new Config(canvasWidth, canvasHeight, reservedHotbarSlot, autoSwapFromInventory,
                    advanceOnLeftClick, advanceOnRightClick, onlyAdvanceWhenCrosshairTargetExists,
                    alphaThreshold, transparentPixelMode, debug, useOnlyInventoryAvailableColors, colorMatchMode,
                    includeToolsInColorMatching, confirmMode, paintingMode, autoPaintDefaultDelayTicks,
                    autoPaintMinDelayTicks, autoPaintClickButton, autoAimSettleTicks,
                    autoAimToleranceDegrees, autoRequireCalibration, autoLockCameraDuringAuto, portableExactCalibrationMode,
                    useBundledDirectionalCalibration, defaultBundledCalibrationPrefix,
                    autoDetectCalibrationDirectionOnAutoStart, cardinalDirectionToleranceDegrees,
                    autoEnablePortableForBundledCalibration, autoDragSameColorRuns,
                    autoDragMinRunLength, autoDragPixelTicks, autoDragRequireExactCalibration,
                    autoDragStartHoldTicks, autoDragEndHoldTicks, smartEnabled, smartMode,
                    smartBaseCoatEnabled, smartBucketThreshold, smartDragThreshold, enabled,
                    ConfigManager.clamp(repeats, 1, 4), Math.max(0, gapTicks), Math.max(0, swapDelayTicks),
                    Math.max(0, aimSettleTicks), Math.max(0, afterDelayTicks), selectedCalibrationName,
                    serverColorOverridesEnabled, serverColorOverrides, batchAutoStartAfterContinue,
                    batchDefaultSpeedTicks, batchEnableDrag, postPaintAutomationEnabled,
                    postPaintSaveHotbarSlot, postPaintFinishedHotbarSlot, postPaintBlankCanvasHotbarSlot,
                    postPaintAimCalibrationIndex, postPaintVaultCommand, postPaintSaveSelectDelayTicks,
                    postPaintSaveAimSettleTicks, postPaintRenameOpenDelayTicks, postPaintRightClickRetries,
                    postPaintFunJumpsEnabled, postPaintFunJumpCount, postPaintFunJumpPressTicks,
                    postPaintFunJumpGapTicks, postPaintRenameClickPoint,
                    postPaintPv2ClickPoint, artMapColors, effectiveArtMapColors);
        }

        private Config copy(boolean enabled, RecordedClickPoint renamePoint, RecordedClickPoint pv2Point) {
            return new Config(canvasWidth, canvasHeight, reservedHotbarSlot, autoSwapFromInventory,
                    advanceOnLeftClick, advanceOnRightClick, onlyAdvanceWhenCrosshairTargetExists,
                    alphaThreshold, transparentPixelMode, debug, useOnlyInventoryAvailableColors, colorMatchMode,
                    includeToolsInColorMatching, confirmMode, paintingMode, autoPaintDefaultDelayTicks,
                    autoPaintMinDelayTicks, autoPaintClickButton, autoAimSettleTicks,
                    autoAimToleranceDegrees, autoRequireCalibration, autoLockCameraDuringAuto, portableExactCalibrationMode,
                    useBundledDirectionalCalibration, defaultBundledCalibrationPrefix,
                    autoDetectCalibrationDirectionOnAutoStart, cardinalDirectionToleranceDegrees,
                    autoEnablePortableForBundledCalibration, autoDragSameColorRuns,
                    autoDragMinRunLength, autoDragPixelTicks, autoDragRequireExactCalibration,
                    autoDragStartHoldTicks, autoDragEndHoldTicks, smartEnabled, smartMode,
                    smartBaseCoatEnabled, smartBucketThreshold, smartDragThreshold, bucketEnabled,
                    bucketClickRepeats, bucketClickGapTicks, bucketSwapDelayTicks, bucketAimSettleTicks,
                    bucketAfterDelayTicks, selectedCalibrationName,
                    serverColorOverridesEnabled, serverColorOverrides, batchAutoStartAfterContinue,
                    batchDefaultSpeedTicks, batchEnableDrag, enabled, postPaintSaveHotbarSlot,
                    postPaintFinishedHotbarSlot, postPaintBlankCanvasHotbarSlot, postPaintAimCalibrationIndex,
                    postPaintVaultCommand, postPaintSaveSelectDelayTicks, postPaintSaveAimSettleTicks,
                    postPaintRenameOpenDelayTicks, postPaintRightClickRetries, postPaintFunJumpsEnabled,
                    postPaintFunJumpCount, postPaintFunJumpPressTicks, postPaintFunJumpGapTicks, renamePoint, pv2Point,
                    artMapColors, effectiveArtMapColors);
        }

        public static Config defaults() {
            List<ArtMapColor> colors = new ArrayList<>();
            for (JsonElement element : defaultColorJson()) {
                JsonObject object = element.getAsJsonObject();
                colors.add(new ArtMapColor(
                        object.get("name").getAsString(),
                        object.get("bukkitMaterial").getAsString(),
                        Identifier.of(object.get("item").getAsString()),
                        object.has("legacyItem") ? Identifier.of(object.get("legacyItem").getAsString()) : null,
                        RgbUtil.parseHex(object.get("rgb").getAsString()),
                        object.get("tool").getAsBoolean()
                ));
            }
            List<ServerColorOverride> overrides = new ArrayList<>();
            for (JsonElement element : defaultServerColorOverrideJson()) {
                JsonObject object = element.getAsJsonObject();
                overrides.add(new ServerColorOverride(
                        Identifier.of(object.get("item").getAsString()),
                        RgbUtil.parseHex(object.get("rgb").getAsString())
                ));
            }
            OverrideResult overrideResult = applyServerColorOverrides(colors, true, overrides);
            return new Config(32, 32, 8, true, true, true, false, 10,
                    TransparentPixelMode.SKIP, false, true, ColorMatchMode.RGB, false, false, PaintingMode.SMART,
                    5, 5, AutoClickButton.RIGHT, 2, 0.75D, true, true, false,
                    true, "ee", true, 45.0D, true,
                    true, 2, 2, true, 2, 1, true, SmartPaintMode.AGGRESSIVE,
                    true, 10, 5, true, 1, 2, 6, 2, 10, "ee", true, List.copyOf(overrides),
                    true, 5, true, true, 2, 0, 1, 500, "/pv 2", 2, 3, 20, 2, true, 5, 2, 4, null, null,
                    List.copyOf(colors), overrideResult.colors());
        }
    }

    record OverrideResult(List<ArtMapColor> colors, int applied) {
    }

    private static JsonArray defaultColorJson() {
        String json = """
                [
                  {"name":"VOID","bukkitMaterial":"ENDER_EYE","item":"minecraft:ender_eye","rgb":"#000000","tool":false},
                  {"name":"GRASS","bukkitMaterial":"GRASS","item":"minecraft:short_grass","legacyItem":"minecraft:grass","rgb":"#7FB238","tool":false},
                  {"name":"CREAM","bukkitMaterial":"PUMPKIN_SEEDS","item":"minecraft:pumpkin_seeds","rgb":"#F7E9A3","tool":false},
                  {"name":"LIGHT_GRAY","bukkitMaterial":"COBWEB","item":"minecraft:cobweb","rgb":"#C7C7C7","tool":false},
                  {"name":"RED","bukkitMaterial":"RED_DYE","item":"minecraft:red_dye","rgb":"#B02E26","tool":false},
                  {"name":"ICE","bukkitMaterial":"ICE","item":"minecraft:ice","rgb":"#A0A7D6","tool":false},
                  {"name":"SILVER","bukkitMaterial":"LIGHT_GRAY_DYE","item":"minecraft:light_gray_dye","rgb":"#9D9D97","tool":false},
                  {"name":"LEAVES","bukkitMaterial":"OAK_LEAVES","item":"minecraft:oak_leaves","rgb":"#667F33","tool":false},
                  {"name":"SNOW","bukkitMaterial":"SNOW","item":"minecraft:snow","rgb":"#FFFFFF","tool":false},
                  {"name":"GRAY","bukkitMaterial":"GRAY_DYE","item":"minecraft:gray_dye","rgb":"#474F52","tool":false},
                  {"name":"COFFEE","bukkitMaterial":"MELON_SEEDS","item":"minecraft:melon_seeds","rgb":"#835432","tool":false},
                  {"name":"STONE","bukkitMaterial":"GHAST_TEAR","item":"minecraft:ghast_tear","rgb":"#707070","tool":false},
                  {"name":"WATER","bukkitMaterial":"LAPIS_BLOCK","item":"minecraft:lapis_block","rgb":"#4040FF","tool":false},
                  {"name":"DARK_WOOD","bukkitMaterial":"DARK_OAK_LOG","item":"minecraft:dark_oak_log","rgb":"#664C33","tool":false},
                  {"name":"WHITE","bukkitMaterial":"BONE_MEAL","item":"minecraft:bone_meal","rgb":"#F9FFFE","tool":false},
                  {"name":"ORANGE","bukkitMaterial":"ORANGE_DYE","item":"minecraft:orange_dye","rgb":"#F9801D","tool":false},
                  {"name":"MAGENTA","bukkitMaterial":"MAGENTA_DYE","item":"minecraft:magenta_dye","rgb":"#C74EBD","tool":false},
                  {"name":"LIGHT_BLUE","bukkitMaterial":"LIGHT_BLUE_DYE","item":"minecraft:light_blue_dye","rgb":"#3AB3DA","tool":false},
                  {"name":"YELLOW","bukkitMaterial":"YELLOW_DYE","item":"minecraft:yellow_dye","rgb":"#FED83D","tool":false},
                  {"name":"LIME","bukkitMaterial":"LIME_DYE","item":"minecraft:lime_dye","rgb":"#80C71F","tool":false},
                  {"name":"PINK","bukkitMaterial":"PINK_DYE","item":"minecraft:pink_dye","rgb":"#F38BAA","tool":false},
                  {"name":"GRAPHITE","bukkitMaterial":"FLINT","item":"minecraft:flint","rgb":"#252525","tool":false},
                  {"name":"GUNPOWDER","bukkitMaterial":"GUNPOWDER","item":"minecraft:gunpowder","rgb":"#707070","tool":false},
                  {"name":"CYAN","bukkitMaterial":"CYAN_DYE","item":"minecraft:cyan_dye","rgb":"#169C9C","tool":false},
                  {"name":"PURPLE","bukkitMaterial":"PURPLE_DYE","item":"minecraft:purple_dye","rgb":"#8932B8","tool":false},
                  {"name":"BLUE","bukkitMaterial":"LAPIS_LAZULI","item":"minecraft:lapis_lazuli","rgb":"#3C44AA","tool":false},
                  {"name":"BROWN","bukkitMaterial":"COCOA_BEANS","item":"minecraft:cocoa_beans","rgb":"#835432","tool":false},
                  {"name":"GREEN","bukkitMaterial":"GREEN_DYE","item":"minecraft:green_dye","rgb":"#5E7C16","tool":false},
                  {"name":"BRICK","bukkitMaterial":"BRICK","item":"minecraft:brick","rgb":"#963430","tool":false},
                  {"name":"BLACK","bukkitMaterial":"INK_SAC","item":"minecraft:ink_sac","rgb":"#1D1D21","tool":false},
                  {"name":"GOLD","bukkitMaterial":"GOLD_NUGGET","item":"minecraft:gold_nugget","rgb":"#FAEE4D","tool":false},
                  {"name":"AQUA","bukkitMaterial":"PRISMARINE_CRYSTALS","item":"minecraft:prismarine_crystals","rgb":"#5CDBD5","tool":false},
                  {"name":"LAPIS","bukkitMaterial":"LAPIS_ORE","item":"minecraft:lapis_ore","rgb":"#4A80FF","tool":false},
                  {"name":"EMERALD","bukkitMaterial":"EMERALD","item":"minecraft:emerald","rgb":"#00D93A","tool":false},
                  {"name":"LIGHT_WOOD","bukkitMaterial":"BIRCH_WOOD","item":"minecraft:birch_wood","rgb":"#D8C29D","tool":false},
                  {"name":"MAROON","bukkitMaterial":"NETHER_WART","item":"minecraft:nether_wart","rgb":"#700200","tool":false},
                  {"name":"WHITE_TERRACOTTA","bukkitMaterial":"EGG","item":"minecraft:egg","rgb":"#D1B1A1","tool":false},
                  {"name":"ORANGE_TERRACOTTA","bukkitMaterial":"MAGMA_CREAM","item":"minecraft:magma_cream","rgb":"#9F5224","tool":false},
                  {"name":"MAGENTA_TERRACOTTA","bukkitMaterial":"BEETROOT","item":"minecraft:beetroot","rgb":"#95576C","tool":false},
                  {"name":"LIGHT_BLUE_TERRACOTTA","bukkitMaterial":"MYCELIUM","item":"minecraft:mycelium","rgb":"#706C8A","tool":false},
                  {"name":"YELLOW_TERRACOTTA","bukkitMaterial":"GLOWSTONE_DUST","item":"minecraft:glowstone_dust","rgb":"#BA8524","tool":false},
                  {"name":"LIME_TERRACOTTA","bukkitMaterial":"SLIME_BALL","item":"minecraft:slime_ball","rgb":"#677535","tool":false},
                  {"name":"PINK_TERRACOTTA","bukkitMaterial":"SPIDER_EYE","item":"minecraft:spider_eye","rgb":"#A04D4E","tool":false},
                  {"name":"GRAY_TERRACOTTA","bukkitMaterial":"SOUL_SAND","item":"minecraft:soul_sand","rgb":"#392923","tool":false},
                  {"name":"LIGHT_GRAY_TERRACOTTA","bukkitMaterial":"BROWN_MUSHROOM","item":"minecraft:brown_mushroom","rgb":"#876B62","tool":false},
                  {"name":"CYAN_TERRACOTTA","bukkitMaterial":"IRON_NUGGET","item":"minecraft:iron_nugget","rgb":"#575C5C","tool":false},
                  {"name":"PURPLE_TERRACOTTA","bukkitMaterial":"CHORUS_FRUIT","item":"minecraft:chorus_fruit","rgb":"#7A4958","tool":false},
                  {"name":"BLUE_TERRACOTTA","bukkitMaterial":"PURPUR_BLOCK","item":"minecraft:purpur_block","rgb":"#4C3E5C","tool":false},
                  {"name":"BROWN_TERRACOTTA","bukkitMaterial":"PODZOL","item":"minecraft:podzol","rgb":"#4C3223","tool":false},
                  {"name":"GREEN_TERRACOTTA","bukkitMaterial":"POISONOUS_POTATO","item":"minecraft:poisonous_potato","rgb":"#4C522A","tool":false},
                  {"name":"RED_TERRACOTTA","bukkitMaterial":"APPLE","item":"minecraft:apple","rgb":"#8E3C2E","tool":false},
                  {"name":"BLACK_TERRACOTTA","bukkitMaterial":"CHARCOAL","item":"minecraft:charcoal","rgb":"#251610","tool":false},
                  {"name":"DARKEN_TOOL","bukkitMaterial":"COAL","item":"minecraft:coal","rgb":"#000000","tool":true},
                  {"name":"LIGHTEN_TOOL","bukkitMaterial":"FEATHER","item":"minecraft:feather","rgb":"#FFFFFF","tool":true}
                ]
                """;
        return GSON.fromJson(json, JsonArray.class);
    }

    private static JsonArray defaultServerColorOverrideJson() {
        String json = """
                [
                  {"item":"minecraft:crimson_nylium","rgb":"#9F2829"},
                  {"item":"minecraft:beetroot","rgb":"#7D495A"},
                  {"item":"minecraft:brick","rgb":"#812B2B"},
                  {"item":"minecraft:red_dye","rgb":"#D70000"},
                  {"item":"minecraft:apple","rgb":"#773126"},
                  {"item":"minecraft:spider_eye","rgb":"#874041"},
                  {"item":"minecraft:crimson_hyphae","rgb":"#4D1418"},
                  {"item":"minecraft:crimson_stem","rgb":"#7C3450"},
                  {"item":"minecraft:nether_wart","rgb":"#5E0100"}
                ]
                """;
        return GSON.fromJson(json, JsonArray.class);
    }
}
