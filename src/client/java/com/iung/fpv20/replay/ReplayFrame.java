package com.iung.fpv20.replay;

public final class ReplayFrame {
    public final long timeNanos;
    public final double x;
    public final double y;
    public final double z;
    private final org.joml.Quaternionf rotation;
    public final double vx;
    public final double vy;
    public final double vz;
    public final float thrust;

    public ReplayFrame(long timeNanos, double x, double y, double z, org.joml.Quaternionf rotation,
                       double vx, double vy, double vz, float thrust) {
        this.timeNanos = timeNanos;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotation = new org.joml.Quaternionf(rotation);
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.thrust = thrust;
    }

    public ReplayFrame lerp(ReplayFrame to, float t) {
        double nx = lerpDouble(this.x, to.x, t);
        double ny = lerpDouble(this.y, to.y, t);
        double nz = lerpDouble(this.z, to.z, t);
        org.joml.Quaternionf nq = new org.joml.Quaternionf(this.rotation).slerp(to.rotation, t);
        double nvx = lerpDouble(this.vx, to.vx, t);
        double nvy = lerpDouble(this.vy, to.vy, t);
        double nvz = lerpDouble(this.vz, to.vz, t);
        float nthrust = lerpFloat(this.thrust, to.thrust, t);
        long ntime = this.timeNanos + (long) ((to.timeNanos - this.timeNanos) * t);
        return new ReplayFrame(ntime, nx, ny, nz, nq, nvx, nvy, nvz, nthrust);
    }

    public ReplayFrame lerpSpline(ReplayFrame p0, ReplayFrame p1, ReplayFrame p2, ReplayFrame p3, float t) {
        double nx = catmullRom(p0.x, p1.x, p2.x, p3.x, t);
        double ny = catmullRom(p0.y, p1.y, p2.y, p3.y, t);
        double nz = catmullRom(p0.z, p1.z, p2.z, p3.z, t);
        org.joml.Quaternionf nq = new org.joml.Quaternionf(p1.rotation).slerp(p2.rotation, t);
        double nvx = lerpDouble(p1.vx, p2.vx, t);
        double nvy = lerpDouble(p1.vy, p2.vy, t);
        double nvz = lerpDouble(p1.vz, p2.vz, t);
        float nthrust = lerpFloat(p1.thrust, p2.thrust, t);
        long ntime = p1.timeNanos + (long) ((p2.timeNanos - p1.timeNanos) * t);
        return new ReplayFrame(ntime, nx, ny, nz, nq, nvx, nvy, nvz, nthrust);
    }

    public org.joml.Quaternionf getRotation() {
        return new org.joml.Quaternionf(rotation);
    }

    private static double lerpDouble(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpFloat(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static double catmullRom(double p0, double p1, double p2, double p3, float t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2.0 * p1) +
                (-p0 + p2) * t +
                (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 +
                (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

}
