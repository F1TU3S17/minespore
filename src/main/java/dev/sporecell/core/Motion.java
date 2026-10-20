package dev.sporecell.core;

public final class Motion {
    private Motion() {}
    public static double steer(double current, double target, double maxTurn) {
        double delta = ((target-current+540)%360+360)%360-180;
        return current + Math.max(-maxTurn, Math.min(maxTurn,delta));
    }
    public static boolean faces(double dx, double dz, int direction, double yaw) {
        double angle = Math.toRadians(yaw+direction*90);
        double length = Math.hypot(dx,dz);
        return length > .0001 && (dx*Math.sin(angle)-dz*Math.cos(angle))/length > .5;
    }
}
