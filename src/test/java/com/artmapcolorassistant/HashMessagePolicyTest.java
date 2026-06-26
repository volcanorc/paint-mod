package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static com.artmapcolorassistant.HashMessagePolicy.Classification.BLOCKED_HASH;
import static com.artmapcolorassistant.HashMessagePolicy.Classification.NORMAL_CHAT;
import static com.artmapcolorassistant.HashMessagePolicy.Classification.PAINTING_COMMAND;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HashMessagePolicyTest {
    @Test
    void recognizesOnlyExactPaintingCommandPrefixes() {
        assertEquals(PAINTING_COMMAND, HashMessagePolicy.classify("#painting"));
        assertEquals(PAINTING_COMMAND, HashMessagePolicy.classify("#painting status"));
        assertEquals(PAINTING_COMMAND, HashMessagePolicy.classify("#paint gui"));
        assertEquals(PAINTING_COMMAND, HashMessagePolicy.classify("   #painting\tstatus   "));

        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("#paintball"));
        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("#paintingcow"));
        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("#Painting status"));
    }

    @Test
    void blocksEveryOtherLeadingHashShape() {
        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("#"));
        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("##"));
        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("############"));
        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("#cow"));
        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("#iii"));
        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("#orc####"));
        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("#/anything"));
        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("   #cow"));
    }

    @Test
    void allowsNormalEmbeddedHashAndSlashChat() {
        assertEquals(NORMAL_CHAT, HashMessagePolicy.classify(null));
        assertEquals(NORMAL_CHAT, HashMessagePolicy.classify(""));
        assertEquals(NORMAL_CHAT, HashMessagePolicy.classify("   "));
        assertEquals(NORMAL_CHAT, HashMessagePolicy.classify("hello"));
        assertEquals(NORMAL_CHAT, HashMessagePolicy.classify("hello #cow"));
        assertEquals(NORMAL_CHAT, HashMessagePolicy.classify("/help"));
        assertEquals(NORMAL_CHAT, HashMessagePolicy.classify("/msg player #cow"));
    }

    @Test
    void allowsExactLowercaseBotPrefixForOtherLocalMods() {
        assertEquals(NORMAL_CHAT, HashMessagePolicy.classify("#bot"));
        assertEquals(NORMAL_CHAT, HashMessagePolicy.classify("#bot help"));
        assertEquals(NORMAL_CHAT, HashMessagePolicy.classify("   #bot\tstatus   "));
    }

    @Test
    void stillBlocksBotPrefixCollisionsAndCaseChanges() {
        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("#botany"));
        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("#bot#"));
        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("#Bot"));
        assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("#botcmd"));
    }

    @Test
    void repeatedMistakesAreAlwaysBlocked() {
        for (int attempt = 0; attempt < 10; attempt++) {
            assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("#cow"));
            assertEquals(BLOCKED_HASH, HashMessagePolicy.classify("###"));
        }
    }
}
