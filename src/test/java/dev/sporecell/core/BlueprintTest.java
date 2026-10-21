package dev.sporecell.core;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintTest {
    private Map<Part,PartSpec> specs() {
        EnumMap<Part,PartSpec> map=new EnumMap<>(Part.class);
        for(Part p:Part.values())map.put(p,new PartSpec(p==Part.CORE?0:4,2,0,0,0,0,0));
        map.put(Part.CORE,new PartSpec(0,12,0,0,0,0,0));
        map.put(Part.FLAGELLUM,new PartSpec(5,1,.12,0,0,0,0));
        map.put(Part.CILIA,new PartSpec(4,1,0,3,0,0,0));
        map.put(Part.ARMOR,new PartSpec(5,9,0,0,0,0,0));return map;
    }
    @Test void rejectsDisconnectedBodyAndMissingCore() {
        assertThrows(IllegalArgumentException.class,()->new Blueprint(List.of(new Blueprint.Piece(0,0,Part.CORE,0),new Blueprint.Piece(2,0,Part.BODY,0))).validate());
        assertThrows(IllegalArgumentException.class,()->new Blueprint(List.of(new Blueprint.Piece(0,0,Part.BODY,0))).validate());
    }
    @Test void rejectsOverlapsExtraCoresAndInvalidRotations() {
        for(Blueprint.Piece p:List.of(new Blueprint.Piece(0,0,Part.BODY,0),new Blueprint.Piece(1,0,Part.CORE,0),new Blueprint.Piece(1,0,Part.BODY,4),new Blueprint.Piece(5,0,Part.BODY,0)))
            assertThrows(IllegalArgumentException.class,()->new Blueprint(List.of(new Blueprint.Piece(0,0,Part.CORE,0),p)).validate());
    }
    @Test void removingBridgeDisconnectsAppendage() {
        Blueprint b=new Blueprint(List.of(new Blueprint.Piece(0,0,Part.CORE,0),new Blueprint.Piece(1,0,Part.BODY,0),new Blueprint.Piece(2,0,Part.EYE,0)));
        b.validate();assertThrows(IllegalArgumentException.class,()->new Blueprint(b.pieces().stream().filter(p->p.x()!=1).toList()).validate());
    }
    @Test void editingRoundTripCannotMintDna() {
        var map=specs();Blueprint a=Blueprint.starter();var pieces=new ArrayList<>(a.pieces());pieces.add(new Blueprint.Piece(1,0,Part.ARMOR,0));Blueprint b=new Blueprint(pieces);
        int spent=Blueprint.purchaseCost(a,b,map);assertEquals(5,spent);assertEquals(0,spent+Blueprint.purchaseCost(b,a,map));
        assertEquals(0,Blueprint.purchaseCost(b,b,map));assertEquals(6,b.stats(map).health());
    }
    @Test void propulsionMountingAndManeuveringMatter() {
        var map=specs();Blueprint core=new Blueprint(List.of(new Blueprint.Piece(0,0,Part.CORE,0)));
        Blueprint forward=new Blueprint(List.of(core.pieces().getFirst(),new Blueprint.Piece(0,1,Part.FLAGELLUM,2)));
        Blueprint sideways=new Blueprint(List.of(core.pieces().getFirst(),new Blueprint.Piece(0,1,Part.FLAGELLUM,1)));
        assertTrue(forward.stats(map).speed()>core.stats(map).speed());assertTrue(forward.stats(map).speed()>sideways.stats(map).speed());
        Blueprint cilia=new Blueprint(List.of(core.pieces().getFirst(),new Blueprint.Piece(1,0,Part.CILIA,1)));
        assertTrue(cilia.stats(map).turn()>core.stats(map).turn());
    }
    @Test void blueprintJsonRoundTripRetainsPartsAndStatistics() {
        Gson gson=new Gson();Blueprint b=Blueprint.starter();Blueprint restored=gson.fromJson(gson.toJson(b),Blueprint.class);
        assertEquals(b,restored);assertEquals(b.stats(specs()),restored.stats(specs()));
    }
    @Test void steeringWrapsAndDirectionalAttacksHaveAnArc() {
        assertEquals(361,Motion.steer(359,2,2));assertTrue(Motion.faces(0,-1,0,0));
        assertFalse(Motion.faces(0,1,0,0));assertTrue(Motion.faces(1,0,1,0));assertFalse(Motion.faces(0,0,0,0));
    }
}
