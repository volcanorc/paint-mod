package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandGuideTest {
    @Test
    void suggestionsOnlyAppearForLocalPaintingPrefixes() {
        assertFalse(CommandGuide.chatSuggestions("#painting").isEmpty());
        assertFalse(CommandGuide.chatSuggestions("#paint").isEmpty());
        assertTrue(CommandGuide.chatSuggestions("hello server").isEmpty());
        assertTrue(CommandGuide.chatSuggestions("/help").isEmpty());
        assertTrue(CommandGuide.chatSuggestions("#/painting").isEmpty());
    }

    @Test
    void tabCompletionStillUsesFirstFilteredSuggestion() {
        assertEquals("#painting gui", CommandGuide.firstCompletion("#painting g"));
        assertEquals("#painting auto start", CommandGuide.firstCompletion("#painting auto st"));
        assertNull(CommandGuide.firstCompletion("normal chat"));
    }
}
