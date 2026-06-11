package com.artmapcolorassistant;

public final class RgbUtil {
    private RgbUtil() {
    }

    public static int parseHex(String hex) {
        if (hex == null || !hex.matches("#?[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("RGB must be #RRGGBB");
        }
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        return Integer.parseInt(clean, 16);
    }

    public static String toHex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    public static int squaredDistance(int leftRgb, int rightRgb) {
        int lr = (leftRgb >> 16) & 0xFF;
        int lg = (leftRgb >> 8) & 0xFF;
        int lb = leftRgb & 0xFF;
        int rr = (rightRgb >> 16) & 0xFF;
        int rg = (rightRgb >> 8) & 0xFF;
        int rb = rightRgb & 0xFF;
        int dr = lr - rr;
        int dg = lg - rg;
        int db = lb - rb;
        return dr * dr + dg * dg + db * db;
    }
}
