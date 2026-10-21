package dev.sporecell.core;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintTransformTest {
    private Blueprint.Piece core(){return new Blueprint.Piece(0,0,Part.CORE,0);}
    @Test void attachmentsKeepIndependentScaleRotationAndAnchorThroughJson(){
        var p=Blueprint.Piece.attachment(0,0,Part.EYE,.25,0,-.2,37,1.6).orient(37,20,-15);
        var b=new Blueprint(List.of(core(),p));b.validate();
        var restored=new Gson().fromJson(new Gson().toJson(b),Blueprint.class);
        assertEquals(b,restored);assertEquals(37,restored.pieces().get(1).angle());
        assertEquals(1.6,restored.pieces().get(1).size());assertEquals(20,restored.pieces().get(1).pitch());assertEquals(-15,restored.pieces().get(1).roll());
        assertEquals(20,p.mirror().pitch());assertEquals(15,p.mirror().roll());
        assertEquals(-.25,p.mirror().dx());assertEquals(-37,p.mirror().angle());assertEquals(p,p.mirror().mirror());
    }
    @Test void rejectsFloatingAttachmentsAndInvalidTransforms(){
        assertThrows(IllegalArgumentException.class,()->new Blueprint(List.of(core(),Blueprint.Piece.attachment(1,0,Part.EYE,0,0,0,0,1))).validate());
        for(double scale:new double[]{0,.49,2.01,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->new Blueprint(List.of(core(),Blueprint.Piece.attachment(0,0,Part.EYE,0,0,0,0,scale))).validate());
    }
    @Test void oldBlueprintsLoadAtUnitScale(){
        var b=new Gson().fromJson("{\"pieces\":[{\"x\":0,\"z\":0,\"part\":\"CORE\",\"direction\":0}]}",Blueprint.class);
        b.validate();assertEquals(1,b.pieces().getFirst().size());assertFalse(b.pieces().getFirst().mounted());
    }
    @Test void arbitraryAngleDrivesDirectionalContact(){
        assertTrue(Motion.facesAngle(1,-1,45));assertFalse(Motion.facesAngle(-1,1,45));
    }
}
