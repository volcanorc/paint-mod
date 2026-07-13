package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class LocalSuggestionMouseHandlerTest {
    @Test
    void directlyLoadedHelperStaysOutsideReservedMixinPackage() {
        assertFalse(LocalSuggestionMouseHandler.class.getPackageName()
                .startsWith("com.artmapcolorassistant.mixin"));
    }
}
