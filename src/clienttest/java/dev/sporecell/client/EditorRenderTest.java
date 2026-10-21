package dev.sporecell.client;

import dev.sporecell.*;
import dev.sporecell.core.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;

/** Dedicated development run; no test fixture or initializer is shipped in the release jar. */
public final class EditorRenderTest implements ClientModInitializer {
    private int ticks;
    private final String imageName="cell-editor-"+System.currentTimeMillis()+".png";
    @Override public void onInitializeClient() {
        if(Boolean.getBoolean("sporecell.test.world"))return;
        ClientTickEvents.END_CLIENT_TICK.register(client-> {
            if(client.getOverlay()!=null)return;
            ticks++;
            if(ticks==20) {
                CellClient.body=Blueprint.starter();CellClient.dna=12;CellClient.specs=Parts.SPECS;
                CellClient.revision=0;CellClient.health=15;CellClient.active=false;CellClient.message="";
                client.setScreen(new CellEditor());
            }
            if(ticks==40 && client.currentScreen instanceof CellEditor screen) {
                int step=Math.max(8,Math.min(27,Math.min((screen.height-96)/9,(screen.width-270)/9)));
                double x=132+5*step+step/2d,z=46+4*step+step/2d;
                screen.mouseClicked(x,z,0);
                screen.keyPressed(GLFW.GLFW_KEY_R,0,0);
                screen.mouseClicked(x,z+step,0);
                screen.mouseClicked(x+step,z,0);
                screen.mouseClicked(x+step,z,1);
                Blueprint draft=screen.draft();draft.validate();
                if(draft.pieces().size()!=5 || Blueprint.purchaseCost(CellClient.body,draft,CellClient.specs)!=4
                        || draft.pieces().stream().noneMatch(p->p.x()==1&&p.z()==1&&p.direction()==1))
                    throw new AssertionError("Editor placement, rotation, removal or budget failed");
                screen.keyPressed(GLFW.GLFW_KEY_S,0,0);screen.mouseClicked(x,z,0);
                if(screen.draft().pieces().size()!=6)throw new AssertionError("Symmetry did not place mirrored part");
                screen.keyPressed(GLFW.GLFW_KEY_Z,0,0);
                if(!screen.draft().equals(draft))throw new AssertionError("Undo did not restore mirrored edit");
                if(!screen.getTitle().getString().contains("Редактор"))throw new AssertionError("Missing Russian title");
                SporeCell.LOGGER.info("Editor interaction assertions passed: place, rotate, remove, DNA, symmetry, undo, Russian");
            }
            if(ticks==65)ScreenshotRecorder.saveScreenshot(client.runDirectory,imageName,client.getFramebuffer(),text->SporeCell.LOGGER.info("Editor screenshot: {}",text.getString()));
            if(ticks==90)client.scheduleStop();
        });
    }
}
