package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandGuideTest {
    @Test
    void suggestionsOnlyAppearForLocalPaintingPrefixes() {
        assertFalse(CommandGuide.chatSuggestions("#painting").isEmpty());
        assertFalse(CommandGuide.chatSuggestions("#paint").isEmpty());
        assertTrue(CommandGuide.chatSuggestions("").isEmpty());
        assertTrue(CommandGuide.chatSuggestions("   ").isEmpty());
        assertTrue(CommandGuide.chatSuggestions("hello server").isEmpty());
        assertTrue(CommandGuide.chatSuggestions("/help").isEmpty());
        assertTrue(CommandGuide.chatSuggestions("#/painting").isEmpty());
        assertTrue(CommandGuide.chatSuggestions("#paintingball").isEmpty());
    }

    @Test
    void tabCompletionStillUsesFirstFilteredSuggestion() {
        assertEquals("#painting gui", CommandGuide.firstCompletion("#painting g"));
        assertEquals("#painting auto start", CommandGuide.firstCompletion("#painting auto st"));
        assertEquals("#paint gui", CommandGuide.firstCompletion("#paint g"));
        assertEquals("#paint auto start", CommandGuide.firstCompletion("#paint auto st"));
        assertNull(CommandGuide.firstCompletion("normal chat"));
    }

    @Test
    void fixedChoicesAreHierarchicalAndPlaceholdersAreNeverInserted() {
        assertEquals(Set.of("#painting set manual", "#painting set auto", "#painting set smart"),
                insertions("#painting set "));
        assertEquals(Set.of("#painting auto drag on", "#painting auto drag off", "#painting auto drag status"),
                insertions("#painting auto drag "));

        CommandGuide.Completion speed = CommandGuide.chatSuggestions("#painting auto sp").getFirst();
        assertEquals("#painting auto speed <ticks>", speed.display());
        assertEquals("#painting auto speed", speed.insertion());
        CommandGuide.Completion speedHint = CommandGuide.chatSuggestions("#painting auto speed ").getFirst();
        assertFalse(speedHint.insertable());
        assertNull(speedHint.insertion());
    }

    @Test
    void rootCompletionsCoverEveryImplementedNamedRootCommand() {
        Set<String> expected = Set.of("help", "gui", "paths", "set", "android", "dryrun", "palette", "batch",
                "recovery", "postpaint", "rename", "pv", "pv2", "full", "auto", "smart", "bucket", "coalblack",
                "fakeclick", "calibrate", "calibration", "cal", "usecalibration", "status", "stop", "pause", "resume",
                "back", "skip", "reload", "goto", "pos", "confirm");
        Set<String> actual = CommandGuide.chatSuggestions("#painting ").stream()
                .filter(CommandGuide.Completion::insertable)
                .map(CommandGuide.Completion::insertion)
                .map(command -> command.substring("#painting ".length()))
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
    }

    @Test
    void nestedFixedChoicesMatchImplementedCommandRoutes() {
        assertInsertions("#painting android ", "status", "testinput");
        assertInsertions("#painting palette ", "status", "reds", "why");
        assertInsertions("#painting batch ", "start", "continue", "status", "stop");
        assertInsertions("#painting recovery ", "status", "clear");
        assertInsertions("#painting postpaint ", "on", "off", "status");
        assertInsertions("#painting rename ", "click", "clear");
        assertInsertions("#painting pv2 ", "click", "clear");
        assertInsertions("#painting auto ", "full", "start", "stop", "pause", "resume", "status", "speed", "drag");
        assertInsertions("#painting smart ", "on", "off", "status", "preview", "basecoat", "threshold", "dragthreshold");
        assertInsertions("#painting smart basecoat ", "on", "off");
        assertInsertions("#painting bucket ", "on", "off", "status", "preview", "selectdelay", "swapdelay",
                "aimdelay", "afterdelay", "restoredelay", "natural");
        assertInsertions("#painting bucket natural ", "on", "off", "status", "delay");
        assertInsertions("#painting coalblack ", "on", "off", "status", "passes");
        assertInsertions("#painting fakeclick ", "on", "off", "status");
        assertInsertions("#painting calibrate ", "start", "continue", "resume", "save", "reset", "stop", "undo",
                "status", "clear");
        assertInsertions("#painting calibration ", "portable");
        assertInsertions("#painting calibration portable ", "on", "off", "status");
        assertInsertions("#painting cal ", "top-left", "top-right", "bottom-left", "bottom-right", "status", "clear",
                "test");
        assertInsertions("#painting confirm ", "on", "off");
    }

    @Test
    void argumentPromptsNeverBecomeLiteralCompletions() {
        for (String input : Set.of("#painting dryrun ", "#painting palette why ", "#painting batch start ",
                "#painting pv ", "#painting auto speed ", "#painting smart threshold ",
                "#painting bucket aimdelay ", "#painting bucket natural delay ", "#painting coalblack passes ",
                "#painting calibrate start ", "#painting cal test ",
                "#painting usecalibration ", "#painting goto ", "#painting pos ")) {
            assertFalse(CommandGuide.chatSuggestions(input).isEmpty(), input);
            assertTrue(CommandGuide.chatSuggestions(input).stream().noneMatch(CommandGuide.Completion::insertable), input);
        }
    }

    private static Set<String> insertions(String input) {
        return CommandGuide.chatSuggestions(input).stream()
                .filter(CommandGuide.Completion::insertable)
                .map(CommandGuide.Completion::insertion)
                .collect(Collectors.toSet());
    }

    private static void assertInsertions(String input, String... suffixes) {
        Set<String> prefix = java.util.Arrays.stream(suffixes)
                .map(suffix -> input + suffix)
                .collect(Collectors.toSet());
        assertEquals(prefix, insertions(input));
    }
}
