package com.artmapcolorassistant;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PlayerVaultSelection(int number) {
    public static final int MIN = 1;
    public static final int MAX = 40;
    public static final int DEFAULT = 2;

    private static final Pattern STORED_COMMAND = Pattern.compile("^/?(?:pv|playervault)\\s+(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPACT_COMMAND = Pattern.compile("^pv-?\\d+$", Pattern.CASE_INSENSITIVE);

    public PlayerVaultSelection {
        if (number < MIN || number > MAX) {
            throw new IllegalArgumentException("Player Vault number must be between " + MIN + " and " + MAX + ".");
        }
    }

    public String command() {
        return "/pv " + number;
    }

    public String displayName() {
        return "Player Vault " + number;
    }

    public static ParseResult parseNumber(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return ParseResult.error("A Player Vault number is required.");
        }
        int number;
        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return ParseResult.error("Player Vault must be a whole number from " + MIN + " to " + MAX + ".");
        }
        if (number < MIN || number > MAX) {
            return ParseResult.error("Player Vault must be between " + MIN + " and " + MAX + ".");
        }
        return new ParseResult(new PlayerVaultSelection(number), null);
    }

    public static boolean looksCompact(String rootCommand) {
        return rootCommand != null && COMPACT_COMMAND.matcher(rootCommand.trim()).matches();
    }

    public static ParseResult parseCompact(String rootCommand) {
        if (!looksCompact(rootCommand)) {
            return ParseResult.error("Compact Player Vault commands use #painting pv<number>.");
        }
        return parseNumber(rootCommand.trim().toLowerCase(Locale.ROOT).substring(2));
    }

    public static PlayerVaultSelection fromStoredCommand(String command) {
        if (command == null) {
            return null;
        }
        Matcher matcher = STORED_COMMAND.matcher(command.trim());
        if (!matcher.matches()) {
            return null;
        }
        ParseResult result = parseNumber(matcher.group(1));
        return result.valid() ? result.selection() : null;
    }

    public static String displayName(String configuredCommand) {
        PlayerVaultSelection selection = fromStoredCommand(configuredCommand);
        return selection == null ? "Custom storage" : selection.displayName();
    }

    public record ParseResult(PlayerVaultSelection selection, String error) {
        static ParseResult error(String message) {
            return new ParseResult(null, message);
        }

        public boolean valid() {
            return selection != null;
        }
    }
}
