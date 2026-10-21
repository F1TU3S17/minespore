package dev.sporecell.core;

/** Periodic visual field: native particle integration e59ee0 preserves the overshoot at each edge. */
public final class CellField {
    private CellField() {}
    public static double wrap(double value,double min,double max) {
        double width=max-min;
        if(!Double.isFinite(value)||!Double.isFinite(width)||width<=0)throw new IllegalArgumentException("Invalid periodic field");
        return min+((value-min)%width+width)%width;
    }
}
