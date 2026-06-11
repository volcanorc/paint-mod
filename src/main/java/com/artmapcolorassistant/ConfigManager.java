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
    private final List<String> warnings = new ArrayList<>();

    public Config config() {
        return config;
    }

    public Path importsPath() {
        return importsPath;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public void load(Consumer<Text> warningSink) {
        warnings.clear();
        try {
            Files.createDirectories(configPath.getParent());
            Files.createDirectories(importsPath);
            if (Files.notExists(configPath)) {
                config = Config.defaults();
                save();
            }
            try (Reader reader = Files.newBufferedReader(configPath)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                config = parse(root);
            }
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
        boolean includeToolsInColorMatching = boolValue(root, "includeToolsInColorMatching", defaults.includeToolsInColorMatching);
        boolean confirmMode = boolValue(root, "confirmMode", defaults.confirmMode);

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
        return new Config(canvasWidth, canvasHeight, reservedHotbarSlot, autoSwapFromInventory,
                advanceOnLeftClick, advanceOnRightClick, onlyAdvanceWhenCrosshairTargetExists,
                alphaThreshold, transparentPixelMode, debug, useOnlyInventoryAvailableColors,
                includeToolsInColorMatching, confirmMode, List.copyOf(colors));
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
        root.addProperty("includeToolsInColorMatching", value.includeToolsInColorMatching);
        root.addProperty("confirmMode", value.confirmMode);
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
        return root;
    }

    private void warn(String warning, Consumer<Text> warningSink) {
        warnings.add(warning);
        if (warningSink != null) {
            warningSink.accept(Text.literal("[ArtMap] " + warning));
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

    private static String stringValue(JsonObject object, String key, String fallback) {
        try {
            return object != null && object.has(key) ? object.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
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
            boolean includeToolsInColorMatching,
            boolean confirmMode,
            List<ArtMapColor> artMapColors
    ) {
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
            return new Config(32, 32, 8, true, true, true, false, 10,
                    TransparentPixelMode.SKIP, false, true, false, false, List.copyOf(colors));
        }
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
}
