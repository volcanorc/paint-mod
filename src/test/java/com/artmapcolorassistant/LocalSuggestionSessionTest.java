package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSuggestionSessionTest {
    @Test
    void repeatedCyclesKeepTheOriginalCandidateListAndWrap() {
        LocalSuggestionSession session = new LocalSuggestionSession();
        session.sync("#painting ", CommandGuide.chatSuggestions("#painting "));
        int insertableCount = (int) session.candidates().stream()
                .filter(CommandGuide.Completion::insertable)
                .count();

        CommandGuide.Completion first = session.cycle(1, 8);
        assertEquals("#painting help", first.insertion());
        session.markApplied(first.insertion());
        session.sync(first.insertion(), CommandGuide.chatSuggestions(first.insertion()));
        assertEquals("#painting gui", session.cycle(1, 8).insertion());

        for (int i = 1; i < insertableCount; i++) {
            session.cycle(1, 8);
        }
        assertEquals("#painting help", session.candidates().get(session.selectedIndex()).insertion());
    }

    @Test
    void reverseCycleStartsAtTheLastInsertableChoice() {
        LocalSuggestionSession session = new LocalSuggestionSession();
        session.sync("#painting set ", CommandGuide.chatSuggestions("#painting set "));

        assertEquals("#painting set smart", session.cycle(-1, 8).insertion());
        assertEquals("#painting set auto", session.cycle(-1, 8).insertion());
    }

    @Test
    void typingAfterACompletionResetsToTheNewFilter() {
        LocalSuggestionSession session = new LocalSuggestionSession();
        session.sync("#painting ", CommandGuide.chatSuggestions("#painting "));
        CommandGuide.Completion first = session.cycle(1, 8);
        session.markApplied(first.insertion());

        session.sync("#painting sm", CommandGuide.chatSuggestions("#painting sm"));

        assertEquals(-1, session.selectedIndex());
        assertEquals(1, session.candidates().size());
        assertEquals("#painting smart", session.cycle(1, 8).insertion());
    }

    @Test
    void viewportFollowsSelectionBeyondEightRows() {
        LocalSuggestionSession session = new LocalSuggestionSession();
        session.sync("#painting ", CommandGuide.chatSuggestions("#painting "));

        for (int i = 0; i < 11; i++) {
            session.cycle(1, 8);
        }

        assertEquals(10, session.selectedIndex());
        assertEquals(3, session.viewportStart());
        assertEquals(8, session.visible(8).size());
    }

    @Test
    void nonInsertablePlaceholderCannotBeSelected() {
        LocalSuggestionSession session = new LocalSuggestionSession();
        session.sync("#painting auto speed ", CommandGuide.chatSuggestions("#painting auto speed "));

        assertTrue(session.candidates().getFirst().display().endsWith("<ticks>"));
        assertNull(session.cycle(1, 8));
        assertNull(session.select(0, 8));
    }
}
