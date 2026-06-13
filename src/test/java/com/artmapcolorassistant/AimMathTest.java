package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimMathTest {
    @Test
    void interpolatesDefaultCanvasPixelCenters() {
        CanvasCalibration calibration = squareCalibration(32, 32);

        WorldPoint topLeft = AimMath.pixelTarget(calibration, 0, 0, 32, 32);
        WorldPoint topRight = AimMath.pixelTarget(calibration, 31, 0, 32, 32);
        WorldPoint bottomLeft = AimMath.pixelTarget(calibration, 0, 31, 32, 32);
        WorldPoint bottomRight = AimMath.pixelTarget(calibration, 31, 31, 32, 32);

        assertPoint(topLeft, 0.5, 0.5, 0.0);
        assertPoint(topRight, 31.5, 0.5, 0.0);
        assertPoint(bottomLeft, 0.5, 31.5, 0.0);
        assertPoint(bottomRight, 31.5, 31.5, 0.0);
    }

    @Test
    void interpolatesNonDefaultCanvasPixelCenters() {
        CanvasCalibration calibration = squareCalibration(16, 8);

        assertPoint(AimMath.pixelTarget(calibration, 0, 0, 16, 8), 0.5, 0.5, 0.0);
        assertPoint(AimMath.pixelTarget(calibration, 15, 7, 16, 8), 15.5, 7.5, 0.0);
    }

    @Test
    void calculatesStableForwardAngles() {
        AimAngles angles = AimMath.anglesTo(new WorldPoint(0.0, 0.0, 0.0), new WorldPoint(0.0, 0.0, 1.0));

        assertEquals(0.0F, angles.yaw(), 0.001F);
        assertEquals(0.0F, angles.pitch(), 0.001F);
    }

    @Test
    void toleranceHandlesYawWrapping() {
        assertTrue(AimMath.withinTolerance(new AimAngles(-179.5F, 0.0F), new AimAngles(179.5F, 0.0F), 2.0D));
    }

    @Test
    void pixelAnglesUseCalibratedCornerDirections() {
        CanvasCalibration calibration = angleCalibration(
                new AimAngles(-45.0F, -10.0F),
                new AimAngles(45.0F, -10.0F),
                new AimAngles(-45.0F, 10.0F),
                new AimAngles(45.0F, 10.0F)
        );

        AimAngles topLeft = AimMath.pixelAngles(calibration, 0, 0, 32, 32);
        AimAngles bottomRight = AimMath.pixelAngles(calibration, 31, 31, 32, 32);

        assertEquals(-45.0F, topLeft.yaw(), 0.001F);
        assertEquals(-10.0F, topLeft.pitch(), 0.001F);
        assertEquals(45.0F, bottomRight.yaw(), 0.001F);
        assertEquals(10.0F, bottomRight.pitch(), 0.001F);
    }

    @Test
    void pixelAnglesInterpolateYawAcrossWrapBoundary() {
        CanvasCalibration calibration = angleCalibration(
                new AimAngles(179.0F, 0.0F),
                new AimAngles(-179.0F, 0.0F),
                new AimAngles(179.0F, 10.0F),
                new AimAngles(-179.0F, 10.0F)
        );

        AimAngles middle = AimMath.pixelAngles(calibration, 15, 0, 32, 32);

        assertTrue(Math.abs(Math.abs(middle.yaw()) - 180.0F) < 1.1F);
    }

    @Test
    void exactSamplesCanStoreEveryPixelDirection() {
        CanvasCalibration calibration = new CanvasCalibration();
        calibration.setExact(CanvasMath.toIndex(31, 31, 32), sample(new AimAngles(72.0F, 14.0F), null));

        CalibrationSample sample = calibration.exact(CanvasMath.toIndex(31, 31, 32));

        assertEquals(72.0F, sample.angles().yaw(), 0.001F);
        assertEquals(14.0F, sample.angles().pitch(), 0.001F);
        assertEquals(1, calibration.exactCount());
    }

    private CanvasCalibration squareCalibration(int width, int height) {
        CanvasCalibration calibration = new CanvasCalibration();
        calibration.set(CalibrationPoint.TOP_LEFT, sample(new AimAngles(0.0F, 0.0F), new WorldPoint(0.0, 0.0, 0.0)));
        calibration.set(CalibrationPoint.TOP_RIGHT, sample(new AimAngles(0.0F, 0.0F), new WorldPoint(width, 0.0, 0.0)));
        calibration.set(CalibrationPoint.BOTTOM_LEFT, sample(new AimAngles(0.0F, 0.0F), new WorldPoint(0.0, height, 0.0)));
        calibration.set(CalibrationPoint.BOTTOM_RIGHT, sample(new AimAngles(0.0F, 0.0F), new WorldPoint(width, height, 0.0)));
        return calibration;
    }

    private CanvasCalibration angleCalibration(AimAngles topLeft, AimAngles topRight, AimAngles bottomLeft, AimAngles bottomRight) {
        CanvasCalibration calibration = new CanvasCalibration();
        calibration.set(CalibrationPoint.TOP_LEFT, sample(topLeft, new WorldPoint(0.0, 0.0, 0.0)));
        calibration.set(CalibrationPoint.TOP_RIGHT, sample(topRight, new WorldPoint(1.0, 0.0, 0.0)));
        calibration.set(CalibrationPoint.BOTTOM_LEFT, sample(bottomLeft, new WorldPoint(0.0, 1.0, 0.0)));
        calibration.set(CalibrationPoint.BOTTOM_RIGHT, sample(bottomRight, new WorldPoint(1.0, 1.0, 0.0)));
        return calibration;
    }

    private CalibrationSample sample(AimAngles angles, WorldPoint hit) {
        return new CalibrationSample(angles, new WorldPoint(0.0, 0.0, 0.0), hit);
    }

    private void assertPoint(WorldPoint actual, double x, double y, double z) {
        assertEquals(x, actual.x(), 0.001D);
        assertEquals(y, actual.y(), 0.001D);
        assertEquals(z, actual.z(), 0.001D);
    }
}
