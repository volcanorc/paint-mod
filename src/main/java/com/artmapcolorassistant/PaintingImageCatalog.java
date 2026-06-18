package com.artmapcolorassistant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class PaintingImageCatalog {
    private static final Comparator<String> NATURAL_NAME_ORDER = PaintingImageCatalog::compareNaturally;

    private PaintingImageCatalog() {
    }

    static Result scan(Path importsPath) {
        if (importsPath == null || !Files.isDirectory(importsPath)) {
            return new Result(List.of(), "Imports folder is unavailable: " + importsPath);
        }
        try (Stream<Path> paths = Files.list(importsPath)) {
            List<String> filenames = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted(NATURAL_NAME_ORDER)
                    .toList();
            return new Result(filenames, null);
        } catch (IOException | SecurityException e) {
            String detail = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            return new Result(List.of(), "Could not read imports folder: " + detail);
        }
    }

    private static int compareNaturally(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            char leftChar = left.charAt(leftIndex);
            char rightChar = right.charAt(rightIndex);
            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int leftEnd = digitEnd(left, leftIndex);
                int rightEnd = digitEnd(right, rightIndex);
                String leftNumber = trimLeadingZeroes(left.substring(leftIndex, leftEnd));
                String rightNumber = trimLeadingZeroes(right.substring(rightIndex, rightEnd));
                int lengthComparison = Integer.compare(leftNumber.length(), rightNumber.length());
                if (lengthComparison != 0) {
                    return lengthComparison;
                }
                int numberComparison = leftNumber.compareTo(rightNumber);
                if (numberComparison != 0) {
                    return numberComparison;
                }
                int runLengthComparison = Integer.compare(leftEnd - leftIndex, rightEnd - rightIndex);
                if (runLengthComparison != 0) {
                    return runLengthComparison;
                }
                leftIndex = leftEnd;
                rightIndex = rightEnd;
                continue;
            }
            int characterComparison = Character.compare(
                    Character.toLowerCase(leftChar), Character.toLowerCase(rightChar));
            if (characterComparison != 0) {
                return characterComparison;
            }
            leftIndex++;
            rightIndex++;
        }
        int lengthComparison = Integer.compare(left.length(), right.length());
        return lengthComparison != 0 ? lengthComparison : left.compareTo(right);
    }

    private static int digitEnd(String value, int start) {
        int end = start;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        return end;
    }

    private static String trimLeadingZeroes(String value) {
        int firstNonZero = 0;
        while (firstNonZero < value.length() - 1 && value.charAt(firstNonZero) == '0') {
            firstNonZero++;
        }
        return value.substring(firstNonZero);
    }

    record Result(List<String> filenames, String error) {
        Result {
            filenames = filenames == null ? List.of() : List.copyOf(filenames);
        }

        boolean available() {
            return error == null;
        }
    }
}
