package dev.sporecell.core;

/** Native scale knots (01483b10), compressed for Minecraft world units. */
public final class CellScale {
    private CellScale() {}
    private static final double[] NATIVE={10,30,100,300,1000,3000,10000,30000,100000,300000,1000000};
    public static double nativeScale(int level) {return NATIVE[Math.clamp(level,0,10)];}
    public static double size(int level) {return Math.pow(nativeScale(level)/30,.20);}
    public static double cameraHeight(double size) {return 14*size;}
}
