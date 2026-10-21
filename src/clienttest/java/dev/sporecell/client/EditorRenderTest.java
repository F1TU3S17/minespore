package dev.sporecell.client;

import dev.sporecell.*;
import dev.sporecell.core.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;

/** Real rendered editor with mouse-driven attachment, geometry transforms and history. */
public final class EditorRenderTest implements ClientModInitializer {
    private int ticks;
    private final String imageName="cell-editor-3d-"+System.currentTimeMillis()+".png";
    @Override public void onInitializeClient(){
        if(Boolean.getBoolean("sporecell.test.world"))return;
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.getOverlay()!=null)return;ticks++;
            if(ticks==20){
                CellClient.body=Blueprint.starter();CellClient.dna=12;CellClient.specs=Parts.SPECS;
                CellClient.progress=200;CellClient.revision=0;CellClient.active=false;CellClient.message="";
                client.setScreen(new CellEditor());
            }
            if(ticks==40&&client.currentScreen instanceof CellEditor screen){
                screen.keyPressed(GLFW.GLFW_KEY_S,0,0);
                var bodyPoint=screen.view.project(.65,0,0);
                screen.mouseClicked(bodyPoint.x,bodyPoint.y,0);screen.mouseReleased(bodyPoint.x,bodyPoint.y,0);
                if(screen.draft().pieces().size()!=5)throw new AssertionError("Symmetric body placement failed: "+screen.draft());
                screen.choose(Part.EYE);
                var eyePoint=screen.view.project(.65,.42,0);
                screen.mouseClicked(eyePoint.x,eyePoint.y,0);screen.mouseReleased(eyePoint.x,eyePoint.y,0);
                screen.mouseScrolled(eyePoint.x,eyePoint.y,0,5);
                screen.keyPressed(GLFW.GLFW_KEY_R,0,0);screen.keyPressed(GLFW.GLFW_KEY_R,0,0);
                screen.mouseClicked(screen.width-70,95,0);screen.keyPressed(GLFW.GLFW_KEY_R,0,0);
                screen.mouseClicked(screen.width-70,95,0);screen.keyPressed(GLFW.GLFW_KEY_R,0,0);
                var selected=screen.draft().pieces().get(screen.selectedIndex());
                var from=screen.view.project(selected.px(),selected.py()+.48*selected.size(),selected.pz());
                var to=screen.view.project(.15,.42,0);
                screen.mouseClicked(from.x,from.y,0);screen.mouseDragged(to.x,to.y,0,to.x-from.x,to.y-from.y);screen.mouseReleased(to.x,to.y,0);
                Blueprint draft=screen.draft();draft.validate();
                if(draft.pieces().stream().filter(Blueprint.Piece::mounted).anyMatch(p->p.x()!=0||p.pitch()!=15||Math.abs(p.roll())!=15))throw new AssertionError("3-axis rotation or dragging lost attachment pose: "+draft);
                long attachments=draft.pieces().stream().filter(Blueprint.Piece::mounted).count();
                if(draft.pieces().size()!=7||attachments!=2||draft.pieces().stream().filter(Blueprint.Piece::mounted).anyMatch(p->Math.abs(p.size()-1.5)>.001||Math.abs(Math.abs(p.angle())-30)>.001))
                    throw new AssertionError("Surface placement, mirrored rotation or scale failed: "+draft);
                if(Blueprint.purchaseCost(CellClient.body,draft,CellClient.specs)!=10)throw new AssertionError("Transform changed part price");
                screen.keyPressed(GLFW.GLFW_KEY_DELETE,0,0);
                if(screen.draft().pieces().size()!=5)throw new AssertionError("Mirrored delete failed");
                screen.keyPressed(GLFW.GLFW_KEY_Z,0,0);
                if(!screen.draft().equals(draft))throw new AssertionError("Undo lost transforms");
                screen.keyPressed(GLFW.GLFW_KEY_Y,0,0);screen.keyPressed(GLFW.GLFW_KEY_Z,0,0);
                if(!screen.draft().equals(draft))throw new AssertionError("Redo lost transforms");
                SporeCell.LOGGER.info("3D EDITOR TEST PASSED: voxel body, surface attachment, symmetry, scaling, rotation, undo, redo, DNA");
            }
            if(ticks==65)ScreenshotRecorder.saveScreenshot(client.runDirectory,imageName,client.getFramebuffer(),text->SporeCell.LOGGER.info("Editor screenshot: {}",text.getString()));
            if(ticks==90)client.scheduleStop();
        });
    }
}
