package com.artmapcolorassistant;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FabricMetadataCompatibilityTest {
    @Test
    void processedMetadataPublishesTestedRuntimeMinimums() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("fabric.mod.json")) {
            assertNotNull(stream, "Processed fabric.mod.json is missing");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonObject depends = root.getAsJsonObject("depends");

            assertEquals("1.0.2", root.get("version").getAsString());
            assertEquals(">=0.15.11", depends.get("fabricloader").getAsString());
            assertEquals(">=0.101.2+1.21.1", depends.get("fabric-api").getAsString());
            assertEquals("1.21.1", depends.get("minecraft").getAsString());
            assertEquals(">=21", depends.get("java").getAsString());
            assertFalse(root.toString().contains("${"), "Unexpanded Gradle placeholders remain in metadata");
        }
    }
}
