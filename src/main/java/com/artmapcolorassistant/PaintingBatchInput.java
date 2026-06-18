package com.artmapcolorassistant;

final class PaintingBatchInput {
    private PaintingBatchInput() {
    }

    static Validation validate(String firstText, String lastText, String suffixText, PaintingMode mode) {
        if (mode == null || !mode.allowsBatch()) {
            return Validation.error("Batch painting requires Painting Type Auto or Smart.");
        }
        int first;
        int last;
        try {
            first = Integer.parseInt(normalize(firstText));
            last = Integer.parseInt(normalize(lastText));
        } catch (NumberFormatException e) {
            return Validation.error("First and Last must be whole numbers.");
        }
        if (first <= 0) {
            return Validation.error("First must be greater than zero.");
        }
        if (last < first) {
            return Validation.error("Last must be greater than or equal to First.");
        }
        String suffix = normalizeWhitespace(suffixText);
        if (suffix.isBlank()) {
            return Validation.error("Enter a non-empty name suffix, such as Dragon.");
        }
        return new Validation(true, "#painting batch start " + first + " " + last + " " + suffix, null);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeWhitespace(String value) {
        return normalize(value).replaceAll("\\s+", " ");
    }

    record Validation(boolean valid, String command, String error) {
        static Validation error(String message) {
            return new Validation(false, null, message);
        }
    }
}
