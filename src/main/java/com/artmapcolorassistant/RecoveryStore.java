package com.artmapcolorassistant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public final class RecoveryStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long DEBOUNCE_MILLIS = 1000L;

    private final Path progressPath;
    private final Path importsPath;
    private RecoveryProgress pending;
    private long lastWriteMillis;

    public RecoveryStore(Path progressPath, Path importsPath) {
        this.progressPath = progressPath;
        this.importsPath = importsPath;
    }

    public Path progressPath() {
        return progressPath;
    }

    public Optional<RecoveryProgress> load() {
        if (Files.notExists(progressPath)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(progressPath)) {
            RecoveryProgress progress = GSON.fromJson(reader, RecoveryProgress.class);
            if (progress == null || progress.filename().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(progress);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void saveForSession(PaintSession session, PaintingMode mode, RecoveryProgress.BatchSnapshot batch,
                               int smartActionBoundary, boolean smartBucketInFlight, String warning,
                               boolean immediate, Consumer<Text> warningSink) {
        RecoveryProgress.ImageFingerprint fingerprint;
        try {
            fingerprint = fingerprint(session.filename());
        } catch (IOException e) {
            warn("Could not save painting recovery progress: " + e.getMessage(), warningSink);
            return;
        }
        RecoveryProgress progress = RecoveryProgress.forSession(session, mode, batch, smartActionBoundary,
                smartBucketInFlight, fingerprint, warning);
        if (progress != null) {
            save(progress, immediate, warningSink);
        }
    }

    public void save(RecoveryProgress progress, boolean immediate, Consumer<Text> warningSink) {
        if (progress == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!immediate && now - lastWriteMillis < DEBOUNCE_MILLIS) {
            pending = progress;
            return;
        }
        write(progress, warningSink);
    }

    public void flushDue(Consumer<Text> warningSink) {
        if (pending == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastWriteMillis >= DEBOUNCE_MILLIS) {
            RecoveryProgress progress = pending;
            pending = null;
            write(progress, warningSink);
        }
    }

    public void flushNow(Consumer<Text> warningSink) {
        if (pending == null) {
            return;
        }
        RecoveryProgress progress = pending;
        pending = null;
        write(progress, warningSink);
    }

    public void clear(Consumer<Text> warningSink) {
        pending = null;
        try {
            Files.deleteIfExists(progressPath);
        } catch (IOException e) {
            warn("Could not clear painting recovery progress: " + e.getMessage(), warningSink);
        }
    }

    public Validation validate(RecoveryProgress progress) {
        if (progress == null || progress.filename().isBlank()) {
            return Validation.invalid("No painting recovery checkpoint is saved.");
        }
        try {
            RecoveryProgress.ImageFingerprint current = fingerprint(progress.filename());
            if (current.size() != progress.pngSize()
                    || current.lastModifiedMillis() != progress.pngLastModifiedMillis()) {
                return Validation.invalid("Saved recovery belongs to an older/different " + progress.filename()
                        + ". Start a new batch or run #painting recovery clear.");
            }
            return Validation.valid();
        } catch (IOException e) {
            return Validation.invalid("Cannot resume " + progress.filename() + ": " + e.getMessage());
        }
    }

    public RecoveryProgress.ImageFingerprint fingerprint(String filename) throws IOException {
        Path path = importsPath.resolve(filename).normalize();
        if (!path.startsWith(importsPath.normalize())) {
            throw new IOException("image must stay inside imports folder");
        }
        if (Files.notExists(path)) {
            throw new IOException("file is missing from imports folder");
        }
        return new RecoveryProgress.ImageFingerprint(Files.size(path), Files.getLastModifiedTime(path).toMillis());
    }

    public String describe(RecoveryProgress progress) {
        if (progress == null) {
            return "No recovery checkpoint.";
        }
        String batch = progress.hasBatch()
                ? " batch=" + progress.batchFirst() + "-" + progress.batchLast()
                + " current=" + progress.batchCurrent()
                + " suffix=\"" + progress.batchSuffix() + "\""
                : " batch=none";
        return "Recovery: file=" + progress.filename()
                + " index=" + (progress.currentIndex() + 1) + "/" + progress.totalSteps()
                + " mode=" + progress.paintingMode()
                + batch
                + " smartBoundary=" + progress.smartActionBoundary()
                + " bucketInFlight=" + progress.smartBucketInFlight()
                + (progress.hasWarning() ? " last=\"" + progress.lastWarning() + "\"" : "");
    }

    private void write(RecoveryProgress progress, Consumer<Text> warningSink) {
        try {
            Files.createDirectories(progressPath.getParent());
            try (Writer writer = Files.newBufferedWriter(progressPath)) {
                GSON.toJson(progress, writer);
            }
            lastWriteMillis = System.currentTimeMillis();
        } catch (IOException e) {
            warn("Could not save painting recovery progress: " + e.getMessage(), warningSink);
        }
    }

    private void warn(String message, Consumer<Text> warningSink) {
        if (warningSink != null) {
            warningSink.accept(Text.literal(message));
        }
    }

    public record Validation(boolean accepted, String message) {
        static Validation valid() {
            return new Validation(true, "");
        }

        static Validation invalid(String message) {
            return new Validation(false, message);
        }
    }
}
