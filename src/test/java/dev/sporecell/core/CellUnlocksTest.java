package dev.sporecell.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CellUnlocksTest {
    @Test void sixCollectiblesRequireDiscoveryButStarterRemainsPlayable(){
        var initial=CellUnlocks.initial();assertEquals(0,CellUnlocks.count(initial));
        assertTrue(CellUnlocks.permits(initial,Blueprint.starter(Evolution.Diet.HERBIVORE)));
        assertTrue(CellUnlocks.permits(initial,Blueprint.starter(Evolution.Diet.CARNIVORE)));
        assertEquals(6,CellUnlocks.COLLECTIBLE.size());
        assertFalse(initial.contains(Part.SPIKE));assertFalse(initial.contains(Part.OMNIVORE_MOUTH));
    }
    @Test void legacyMigrationRetainsInstalledOrgansAndOldOmnivoreUnlock(){
        var pieces=new ArrayList<>(Blueprint.starter().pieces());pieces.add(Blueprint.Piece.attachment(0,0,Part.JET,0,0,0,0,1));
        var unlocked=CellUnlocks.migrate(new Blueprint(pieces),100);
        assertTrue(unlocked.contains(Part.JET));assertTrue(unlocked.contains(Part.OMNIVORE_MOUTH));assertFalse(unlocked.contains(Part.ELECTRIC));
        assertTrue(CellUnlocks.permits(unlocked,new Blueprint(pieces)));
    }
    @Test void permissionDependsOnPartRegardlessOfTransformOrBudget(){
        var pieces=new ArrayList<>(Blueprint.starter().pieces());pieces.add(Blueprint.Piece.attachment(0,0,Part.POISON,.2,0,0,30,.5));
        var body=new Blueprint(pieces);var unlocked=CellUnlocks.initial();
        assertFalse(CellUnlocks.permits(unlocked,body));unlocked.add(Part.POISON);
        assertTrue(CellUnlocks.permits(unlocked,body));assertEquals(1,CellUnlocks.count(unlocked));
    }
}
