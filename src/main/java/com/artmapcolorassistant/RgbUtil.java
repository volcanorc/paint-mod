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

    public static double distance(int leftRgb, int rightRgb, ColorMatchMode mode) {
        if (mode == ColorMatchMode.PERCEPTUAL) {
            double[] left = lab(leftRgb);
            double[] right = lab(rightRgb);
            double dl = left[0] - right[0];
            double da = left[1] - right[1];
            double db = left[2] - right[2];
            return dl * dl + da * da + db * db;
        }
        return squaredDistance(leftRgb, rightRgb);
    }

    public static boolean isReddish(int rgb) {
        double[] hsv = hsv(rgb);
        double hue = hsv[0];
        double saturation = hsv[1];
        double value = hsv[2];
        boolean redHue = hue <= 28.0D || hue >= 330.0D;
        boolean pinkPurpleHue = hue >= 285.0D && hue <= 329.0D;
        return saturation >= 0.20D && value >= 0.12D && (redHue || pinkPurpleHue);
    }

    private static double[] hsv(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0D;
        double g = ((rgb >> 8) & 0xFF) / 255.0D;
        double b = (rgb & 0xFF) / 255.0D;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double delta = max - min;
        double hue;
        if (delta == 0.0D) {
            hue = 0.0D;
        } else if (max == r) {
            hue = 60.0D * (((g - b) / delta) % 6.0D);
        } else if (max == g) {
            hue = 60.0D * (((b - r) / delta) + 2.0D);
        } else {
            hue = 60.0D * (((r - g) / delta) + 4.0D);
        }
        if (hue < 0.0D) {
            hue += 360.0D;
        }
        double saturation = max == 0.0D ? 0.0D : delta / max;
        return new double[]{hue, saturation, max};
    }

    private static double[] lab(int rgb) {
        double r = linear(((rgb >> 16) & 0xFF) / 255.0D);
        double g = linear(((rgb >> 8) & 0xFF) / 255.0D);
        double b = linear((rgb & 0xFF) / 255.0D);

        double x = (r * 0.4124564D + g * 0.3575761D + b * 0.1804375D) / 0.95047D;
        double y = r * 0.2126729D + g * 0.7151522D + b * 0.0721750D;
        double z = (r * 0.0193339D + g * 0.1191920D + b * 0.9503041D) / 1.08883D;

        double fx = labPivot(x);
        double fy = labPivot(y);
        double fz = labPivot(z);
        return new double[]{
                116.0D * fy - 16.0D,
                500.0D * (fx - fy),
                200.0D * (fy - fz)
        };
    }

    private static double linear(double component) {
        return component <= 0.04045D
                ? component / 12.92D
                : Math.pow((component + 0.055D) / 1.055D, 2.4D);
    }

    private static double labPivot(double value) {
        return value > 0.008856D
                ? Math.cbrt(value)
                : (7.787D * value) + (16.0D / 116.0D);
    }
}
