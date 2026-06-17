package com.artmapcolorassistant;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BundledCalibrationResourceTest {
    private static final Gson GSON = new Gson();

    @Test
    void bundledDirectionalCalibrationsAreComplete32x32Resources() {
        for (String direction : new String[]{"north", "south", "west", "east"}) {
            JsonObject root = read("ee_" + direction);
            assertEquals("ee_" + direction, root.get("name").getAsString());
            assertEquals(32, root.get("canvasWidth").getAsInt());
            assertEquals(32, root.get("canvasHeight").getAsInt());
            JsonArray samples = root.getAsJsonArray("samples");
            assertEquals(1024, samples.size());

            Set<Integer> indexes = new HashSet<>();
            for (JsonElement element : samples) {
                JsonObject sample = element.getAsJsonObject();
                indexes.add(sample.get("index").getAsInt());
            }
            assertEquals(1024, indexes.size());
            for (int index = 0; index < 1024; index++) {
                assertEquals(true, indexes.contains(index), "missing index " + index + " in " + direction);
            }
        }
    }

    @Test
    void generatedDirectionsKeepPitchAndApplyExpectedYawOffsets() {
        JsonArray north = read("ee_north").getAsJsonArray("samples");
        assertYawAndPitch(north, read("ee_south").getAsJsonArray("samples"), -180.0D);
        assertYawAndPitch(north, read("ee_west").getAsJsonArray("samples"), -90.0D);
        assertYawAndPitch(north, read("ee_east").getAsJsonArray("samples"), 90.0D);
    }

    private static void assertYawAndPitch(JsonArray north, JsonArray generated, double offset) {
        for (int i = 0; i < north.size(); i++) {
            JsonObject baseline = north.get(i).getAsJsonObject();
            JsonObject sample = generated.get(i).getAsJsonObject();
            assertEquals(baseline.get("index").getAsInt(), sample.get("index").getAsInt());
            assertEquals(baseline.get("x").getAsInt(), sample.get("x").getAsInt());
            assertEquals(baseline.get("y").getAsInt(), sample.get("y").getAsInt());
            assertEquals(baseline.get("pitch").getAsFloat(), sample.get("pitch").getAsFloat());
            assertEquals(
                    CalibrationDirection.wrapYaw(baseline.get("yaw").getAsDouble() + offset),
                    sample.get("yaw").getAsFloat(),
                    0.00001F
            );
        }
    }

    private static JsonObject read(String name) {
        String path = "assets/artmap_color_assistant/calibrations/" + name + ".json";
        InputStream stream = BundledCalibrationResourceTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            throw new AssertionError("Failed to read " + path, e);
        }
    }
}
