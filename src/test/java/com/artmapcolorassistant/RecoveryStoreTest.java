package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsCompactBatchProgress() throws Exception {
        Path imports = tempDir.resolve("imports");
        Files.createDirectories(imports);
        Path png = imports.resolve("15.png");
        Files.write(png, new byte[]{1, 2, 3});

        RecoveryStore store = new RecoveryStore(tempDir.resolve("progress.json"), imports);
        RecoveryProgress.ImageFingerprint fingerprint = store.fingerprint("15.png");
        RecoveryProgress progress = new RecoveryProgress(RecoveryProgress.SCHEMA_VERSION, "15.png",
                123, 1024, PaintingMode.SMART, true, 5, 20, 15, "Dragon",
                4, false, fingerprint.size(), fingerprint.lastModifiedMillis(), 1000L, "lag pause");

        store.save(progress, true, text -> { });

        RecoveryProgress loaded = store.load().orElseThrow();
        assertEquals("15.png", loaded.filename());
        assertEquals(123, loaded.currentIndex());
        assertEquals(PaintingMode.SMART, loaded.paintingMode());
        assertTrue(loaded.hasBatch());
        assertEquals(5, loaded.batchFirst());
        assertEquals(20, loaded.batchLast());
        assertEquals(15, loaded.batchCurrent());
        assertEquals("Dragon", loaded.batchSuffix());
        assertEquals(4, loaded.smartActionBoundary());
        assertFalse(loaded.smartBucketInFlight());
        assertEquals("lag pause", loaded.lastWarning());
    }

    @Test
    void changedActivePngBlocksRecovery() throws Exception {
        Path imports = tempDir.resolve("imports");
        Files.createDirectories(imports);
        Path png = imports.resolve("15.png");
        Files.write(png, new byte[]{1, 2, 3});

        RecoveryStore store = new RecoveryStore(tempDir.resolve("progress.json"), imports);
        RecoveryProgress.ImageFingerprint fingerprint = store.fingerprint("15.png");
        RecoveryProgress progress = new RecoveryProgress(RecoveryProgress.SCHEMA_VERSION, "15.png",
                0, 1024, PaintingMode.AUTO, false, 0, 0, 0, "",
                0, false, fingerprint.size(), fingerprint.lastModifiedMillis(), 1000L, "");

        Thread.sleep(5L);
        Files.write(png, new byte[]{1, 2, 3, 4});

        RecoveryStore.Validation validation = store.validate(progress);
        assertFalse(validation.accepted());
        assertTrue(validation.message().contains("older/different 15.png"));
    }

    @Test
    void clearDeletesProgressFile() throws Exception {
        Path imports = tempDir.resolve("imports");
        Files.createDirectories(imports);
        RecoveryStore store = new RecoveryStore(tempDir.resolve("progress.json"), imports);
        RecoveryProgress progress = new RecoveryProgress(RecoveryProgress.SCHEMA_VERSION, "1.png",
                0, 1024, PaintingMode.AUTO, false, 0, 0, 0, "",
                0, false, 1, 2, 1000L, "");

        store.save(progress, true, text -> { });
        assertTrue(store.load().isPresent());

        store.clear(text -> { });

        assertTrue(store.load().isEmpty());
    }
}
