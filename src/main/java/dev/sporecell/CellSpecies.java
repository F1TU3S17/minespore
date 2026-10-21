package dev.sporecell;

import dev.sporecell.core.*;
import java.util.*;

/** Connected silhouettes with surface organs; deterministic variants keep species recognizable. */
public final class CellSpecies {
    public static final int COUNT=12;
    private CellSpecies(){}
    public static Blueprint create(int variant,boolean hunter){
        int v=Math.floorMod(variant,COUNT);
        int[][] body=switch(v%4){
            case 0->new int[][]{{0,0},{0,-1},{0,1},{0,2}};
            case 1->new int[][]{{0,0},{-1,0},{1,0},{0,-1},{0,1}};
            case 2->new int[][]{{0,0},{-1,0},{1,0},{-1,1},{1,1},{0,1}};
            default->new int[][]{{0,0},{0,-1},{0,-2},{-1,0},{1,0},{0,1},{-1,1},{1,1}};
        };
        var pieces=new ArrayList<Blueprint.Piece>();
        for(var p:body)pieces.add(new Blueprint.Piece(p[0],p[1],p[0]==0&&p[1]==0?Part.CORE:Part.BODY,0));
        int front=Arrays.stream(body).mapToInt(p->p[1]).min().orElse(0),rear=Arrays.stream(body).mapToInt(p->p[1]).max().orElse(0);
        Part mouth=hunter?Part.CARNIVORE_MOUTH:v%3==2?Part.OMNIVORE_MOUTH:Part.HERBIVORE_MOUTH;
        pieces.add(Blueprint.Piece.attachment(0,front,mouth,0,0,-.2,0,1));
        pieces.add(Blueprint.Piece.attachment(0,rear,v%3==0?Part.JET:Part.FLAGELLUM,0,0,.2,180,1));
        Part eye=switch(v%3){case 0->Part.EYE;case 1->Part.STALK_EYE;default->Part.BUTTON_EYE;};
        pieces.add(Blueprint.Piece.attachment(0,0,eye,-.2,.13,-.08,0,.8));
        pieces.add(Blueprint.Piece.attachment(0,0,eye,.2,.13,-.08,0,.8));
        if(v%2==0)for(int sign:new int[]{-1,1})pieces.add(Blueprint.Piece.attachment(0,0,Part.CILIA,sign*.3,0,.1,sign*90,.8));
        Part defense=switch(v/3){case 0->Part.SPIKE;case 1->Part.POISON;case 2->Part.ELECTRIC;default->Part.JET;};
        for(int sign:new int[]{-1,1})pieces.add(Blueprint.Piece.attachment(0,0,defense,sign*.28,0,.15,sign*90,.85));
        var result=new Blueprint(pieces);result.validate();return result;
    }
}
