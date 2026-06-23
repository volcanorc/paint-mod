package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinConfigurationTest {
    @Test
    void chatAndScreenMouseMixinsAreBothRegistered() throws IOException {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("artmap_color_assistant.mixins.json")) {
            assertNotNull(stream, "Mixin configuration resource is missing");
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"ChatScreenMixin\""));
            assertTrue(json.contains("\"ScreenMixin\""));
        }
    }
}
