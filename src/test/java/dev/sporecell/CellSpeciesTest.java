package dev.sporecell;

import dev.sporecell.core.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CellSpeciesTest {
    @Test void speciesAreConnectedDistinctAndCoverEveryCellOrgan(){
        var bodies=new HashSet<Blueprint>();var parts=EnumSet.noneOf(Part.class);
        for(int v=0;v<CellSpecies.COUNT;v++)for(boolean hunter:new boolean[]{false,true}){
            var body=CellSpecies.create(v,hunter);body.validate();bodies.add(body);body.pieces().forEach(p->parts.add(p.part()));
        }
        assertEquals(24,bodies.size());
        assertTrue(parts.containsAll(EnumSet.of(Part.JET,Part.POISON,Part.ELECTRIC,Part.EYE,Part.STALK_EYE,Part.BUTTON_EYE,Part.CILIA,Part.FLAGELLUM,Part.SPIKE,Part.HERBIVORE_MOUTH,Part.CARNIVORE_MOUTH,Part.OMNIVORE_MOUTH)));
    }
}
