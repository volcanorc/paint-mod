package com.artmapcolorassistant;

import net.minecraft.util.math.Vec3d;

public record WorldPoint(double x, double y, double z) {
    public static WorldPoint from(Vec3d vec) {
        return new WorldPoint(vec.x, vec.y, vec.z);
    }

    public Vec3d toVec3d() {
        return new Vec3d(x, y, z);
    }
}
