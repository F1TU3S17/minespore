package dev.sporecell.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CellScaleTest {
    @Test void nativeScaleKnotsAreCompressedButOrdered() {
        assertEquals(30,CellScale.nativeScale(1));assertEquals(100,CellScale.nativeScale(2));assertEquals(1000000,CellScale.nativeScale(10));
        assertEquals(1,CellScale.size(1));
        for(int i=2;i<=10;i++)assertTrue(CellScale.size(i)>CellScale.size(i-1));
        assertTrue(CellScale.size(10)<9);assertEquals(14*CellScale.size(5),CellScale.cameraHeight(CellScale.size(5)));
    }
    @Test void periodicBoundaryPreservesOvershootInBothDirections() {
        assertEquals(-9,CellField.wrap(11,-10,10));assertEquals(9,CellField.wrap(-11,-10,10));
        assertEquals(1,CellField.wrap(81,-10,10));assertEquals(-1,CellField.wrap(-81,-10,10));
        assertEquals(3,CellField.wrap(3,-10,10));
        assertThrows(IllegalArgumentException.class,()->CellField.wrap(1,0,0));
    }
}
