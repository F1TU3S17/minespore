package dev.sporecell.core;

import java.util.*;

/** Six collectible cell-stage organs; construction and initial organs remain available. */
public final class CellUnlocks {
    public static final List<Part> COLLECTIBLE=List.of(Part.SPIKE,Part.CILIA,Part.OMNIVORE_MOUTH,Part.POISON,Part.JET,Part.ELECTRIC);
    private CellUnlocks(){}
    public static EnumSet<Part> initial(){var parts=EnumSet.allOf(Part.class);parts.removeAll(COLLECTIBLE);return parts;}
    public static EnumSet<Part> migrate(Blueprint body,int progress){
        var parts=initial();body.pieces().forEach(p->parts.add(p.part()));
        if(progress>=100)parts.add(Part.OMNIVORE_MOUTH);return parts;
    }
    public static int count(Set<Part> parts){return (int)COLLECTIBLE.stream().filter(parts::contains).count();}
    public static boolean permits(Set<Part> parts,Blueprint body){return body.pieces().stream().allMatch(p->parts.contains(p.part()));}
}
