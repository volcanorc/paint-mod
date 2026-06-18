package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaintingImageCatalogTest {
    @TempDir
    Path tempDir;

    @Test
    void filtersRegularPngFilesAndSortsNaturally() throws IOException {
        Files.createFile(tempDir.resolve("10.png"));
        Files.createFile(tempDir.resolve("2.png"));
        Files.createFile(tempDir.resolve("1.png"));
        Files.createFile(tempDir.resolve("Dragon 3.PNG"));
        Files.createFile(tempDir.resolve("notes.txt"));
        Files.createDirectory(tempDir.resolve("4.png"));

        PaintingImageCatalog.Result result = PaintingImageCatalog.scan(tempDir);

        assertTrue(result.available());
        assertNull(result.error());
        assertEquals(List.of("1.png", "2.png", "10.png", "Dragon 3.PNG"), result.filenames());
    }

    @Test
    void preservesPngFilenamesContainingSpaces() throws IOException {
        Files.createFile(tempDir.resolve("My Painting 2.png"));
        Files.createFile(tempDir.resolve("My Painting 10.png"));

        assertEquals(List.of("My Painting 2.png", "My Painting 10.png"),
                PaintingImageCatalog.scan(tempDir).filenames());
    }

    @Test
    void emptyDirectoryIsAvailableWithNoImages() {
        PaintingImageCatalog.Result result = PaintingImageCatalog.scan(tempDir);

        assertTrue(result.available());
        assertTrue(result.filenames().isEmpty());
    }

    @Test
    void missingDirectoryReturnsReadableError() {
        PaintingImageCatalog.Result result = PaintingImageCatalog.scan(tempDir.resolve("missing"));

        assertFalse(result.available());
        assertTrue(result.filenames().isEmpty());
        assertTrue(result.error().contains("unavailable"));
    }
}
