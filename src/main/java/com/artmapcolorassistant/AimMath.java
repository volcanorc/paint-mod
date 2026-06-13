package com.artmapcolorassistant;

import net.minecraft.util.math.MathHelper;

public final class AimMath {
    private AimMath() {
    }

    public static WorldPoint pixelTarget(CanvasCalibration calibration, int x, int y, int width, int height) {
        if (!calibration.complete()) {
            throw new IllegalStateException("Calibration is incomplete.");
        }
        double u = (x + 0.5D) / width;
        double v = (y + 0.5D) / height;
        WorldPoint top = lerp(calibration.get(CalibrationPoint.TOP_LEFT).hitPosition(), calibration.get(CalibrationPoint.TOP_RIGHT).hitPosition(), u);
        WorldPoint bottom = lerp(calibration.get(CalibrationPoint.BOTTOM_LEFT).hitPosition(), calibration.get(CalibrationPoint.BOTTOM_RIGHT).hitPosition(), u);
        return lerp(top, bottom, v);
    }

    public static AimAngles pixelAngles(CanvasCalibration calibration, int x, int y, int width, int height) {
        if (!calibration.complete()) {
            throw new IllegalStateException("Calibration is incomplete.");
        }
        double u = axisAmount(x, width);
        double v = axisAmount(y, height);
        AimAngles top = lerpAngles(calibration.get(CalibrationPoint.TOP_LEFT).angles(), calibration.get(CalibrationPoint.TOP_RIGHT).angles(), u);
        AimAngles bottom = lerpAngles(calibration.get(CalibrationPoint.BOTTOM_LEFT).angles(), calibration.get(CalibrationPoint.BOTTOM_RIGHT).angles(), u);
        return lerpAngles(top, bottom, v);
    }

    public static AimAngles anglesTo(WorldPoint eye, WorldPoint target) {
        double dx = target.x() - eye.x();
        double dy = target.y() - eye.y();
        double dz = target.z() - eye.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
        float pitch = MathHelper.wrapDegrees((float) (-Math.toDegrees(Math.atan2(dy, horizontal))));
        return new AimAngles(yaw, pitch);
    }

    public static boolean withinTolerance(AimAngles current, AimAngles target, double toleranceDegrees) {
        float yawDelta = MathHelper.abs(MathHelper.wrapDegrees(current.yaw() - target.yaw()));
        float pitchDelta = MathHelper.abs(MathHelper.wrapDegrees(current.pitch() - target.pitch()));
        return yawDelta <= toleranceDegrees && pitchDelta <= toleranceDegrees;
    }

    private static WorldPoint lerp(WorldPoint a, WorldPoint b, double amount) {
        return new WorldPoint(
                MathHelper.lerp(amount, a.x(), b.x()),
                MathHelper.lerp(amount, a.y(), b.y()),
                MathHelper.lerp(amount, a.z(), b.z())
        );
    }

    private static AimAngles lerpAngles(AimAngles a, AimAngles b, double amount) {
        return new AimAngles(
                lerpYaw(a.yaw(), b.yaw(), amount),
                (float) MathHelper.lerp(amount, a.pitch(), b.pitch())
        );
    }

    private static float lerpYaw(float from, float to, double amount) {
        float delta = MathHelper.wrapDegrees(to - from);
        return MathHelper.wrapDegrees(from + (float) amount * delta);
    }

    private static double axisAmount(int index, int size) {
        if (size <= 1) {
            return 0.0D;
        }
        return (double) index / (double) (size - 1);
    }
}
