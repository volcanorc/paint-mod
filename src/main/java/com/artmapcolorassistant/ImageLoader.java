package com.artmapcolorassistant;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ImageLoader {
    public LoadedImage load(Path importsPath, String filename, int expectedWidth, int expectedHeight) throws ImageLoadException {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new ImageLoadException("Use a simple PNG filename from the imports folder.");
        }
        if (!filename.toLowerCase().endsWith(".png")) {
            throw new ImageLoadException("Only .png files are supported.");
        }
        Path path = importsPath.resolve(filename).normalize();
        if (!path.startsWith(importsPath.normalize())) {
            throw new ImageLoadException("Image must be inside the imports folder.");
        }
        if (Files.notExists(path)) {
            throw new ImageLoadException("File not found in imports folder: " + filename);
        }
        BufferedImage image;
        try {
            image = ImageIO.read(path.toFile());
        } catch (IOException e) {
            throw new ImageLoadException("Could not read PNG: " + e.getMessage());
        }
        if (image == null) {
            throw new ImageLoadException("Could not decode PNG.");
        }
        if (image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
            throw new ImageLoadException("PNG must be exactly " + expectedWidth + "x" + expectedHeight
                    + ", got " + image.getWidth() + "x" + image.getHeight() + ".");
        }
        int[] argb = new int[expectedWidth * expectedHeight];
        image.getRGB(0, 0, expectedWidth, expectedHeight, argb, 0, expectedWidth);
        return new LoadedImage(filename, expectedWidth, expectedHeight, argb);
    }

    public record LoadedImage(String filename, int width, int height, int[] argb) {
    }

    public static final class ImageLoadException extends Exception {
        public ImageLoadException(String message) {
            super(message);
        }
    }
}
