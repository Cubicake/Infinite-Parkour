package dev.efnilite.ip.schematic;

import dev.efnilite.vilib.vector.Vector3D;

public class VectorUtil {

    public static Vector3D parseVector(String vector) {
        String[] split = vector.replaceAll("[()]", "").split(",");
        return new Vector3D(Double.parseDouble(split[0]), Double.parseDouble(split[1]), Double.parseDouble(split[2]));
    }

    public static Vector3D rotateAround(Vector3D vector, SchematicAdjuster.RotationAngle rotation) {
        return switch (rotation) {
            case ANGLE_0 -> vector;
            case ANGLE_90 -> swapXZ(vector.multiply(-1, 1, 1));
            case ANGLE_180 -> vector.multiply(-1, 1, -1);
            case ANGLE_270 -> swapXZ(vector.multiply(1, 1, -1));
        };
    }

    // without swapping
    public static Vector3D defaultRotate(Vector3D vector, SchematicAdjuster.RotationAngle rotation) {
        return switch (rotation) {
            case ANGLE_0 -> vector;
            case ANGLE_90 -> swapXZ(vector.multiply(1, 1, -1));
            case ANGLE_180 -> vector.multiply(-1, 1, -1);
            case ANGLE_270 -> swapXZ(vector.multiply(-1, 1, 1));
        };
    }

    public static Vector3D swapXZ(Vector3D vector) {
        double x = vector.x;
        vector.x = vector.z;
        vector.z = x;
        return vector;
    }
}
