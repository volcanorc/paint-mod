package com.artmapcolorassistant;

final class HashMessagePolicy {
    private static final String LONG_PREFIX = "#painting";
    private static final String SHORT_PREFIX = "#paint";
    private static final String BOT_PREFIX = "#bot";

    private HashMessagePolicy() {
    }

    static Classification classify(String raw) {
        if (raw == null) {
            return Classification.NORMAL_CHAT;
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("#")) {
            return Classification.NORMAL_CHAT;
        }
        if (matchesCommandPrefix(trimmed, BOT_PREFIX)) {
            return Classification.NORMAL_CHAT;
        }
        if (matchesCommandPrefix(trimmed, LONG_PREFIX) || matchesCommandPrefix(trimmed, SHORT_PREFIX)) {
            return Classification.PAINTING_COMMAND;
        }
        return Classification.BLOCKED_HASH;
    }

    private static boolean matchesCommandPrefix(String message, String prefix) {
        if (!message.startsWith(prefix)) {
            return false;
        }
        return message.length() == prefix.length()
                || Character.isWhitespace(message.charAt(prefix.length()));
    }

    enum Classification {
        NORMAL_CHAT,
        PAINTING_COMMAND,
        BLOCKED_HASH
    }
}
