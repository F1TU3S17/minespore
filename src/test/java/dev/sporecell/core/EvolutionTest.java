package dev.sporecell.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvolutionTest {
    @Test void nativeProgressionKnotsAndCap() {
        assertEquals(0,Evolution.earnedDna(0));assertEquals(5,Evolution.earnedDna(50));
        assertEquals(10,Evolution.earnedDna(100));assertEquals(13,Evolution.earnedDna(150));
        assertEquals(65,Evolution.earnedDna(1000));assertEquals(65,Evolution.earnedDna(2000));
        int earned=0;
        for(int food=10;food<=1000;food+=10)earned+=Evolution.earnedDna(food)-Evolution.earnedDna(food-10);
        assertEquals(65,earned);
        assertEquals(1,Evolution.level(99));assertEquals(2,Evolution.level(100));assertEquals(10,Evolution.level(1000));
    }
    @Test void directionalDamageTablesUseRelativeSizeAndPlayerRole() {
        assertEquals(0,Evolution.category(1,10));assertEquals(2,Evolution.category(5,5));assertEquals(4,Evolution.category(10,1));
        assertEquals(6,Evolution.damage(true,false,2,0,false));
        assertEquals(3,Evolution.damage(true,false,2,2,false));
        assertEquals(2,Evolution.damage(true,false,2,2,true));
        assertEquals(1,Evolution.damage(true,false,2,4,false));
        assertEquals(6,Evolution.damage(false,true,4,2,false));
        assertEquals(0,Evolution.damage(false,false,0,2,false));
        assertEquals(6,Evolution.damage(false,false,3,1,false));
        assertEquals(2,Evolution.damage(false,false,3,3,false));
    }
    @Test void mouthCapabilitiesDefineDiet() {
        assertEquals(Evolution.Diet.HERBIVORE,Evolution.diet(true,false));
        assertEquals(Evolution.Diet.CARNIVORE,Evolution.diet(false,true));
        assertEquals(Evolution.Diet.OMNIVORE,Evolution.diet(true,true));
    }
}
