package dev.sporecell.client;

import dev.sporecell.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** End-to-end visual smoke test in an isolated, newly created single-player save. */
public final class WorldRenderTest implements ClientModInitializer {
    public static int renderedFrames;
    public static boolean cullingAccepted;
    private int phase,ticks,modeTicks;
    private int returnTicks;
    private final String imageTag=Long.toString(System.currentTimeMillis());
    private final long started=System.nanoTime();
    private Vec3d start;
    private int initialDna;
    @Override public void onInitializeClient() {
        if(!Boolean.getBoolean("sporecell.test.world"))return;
        ClientTickEvents.END_CLIENT_TICK.register(client-> {
            if((System.nanoTime()-started)/1e9>240)throw new AssertionError("World visual test timed out at phase "+phase+", mode tick "+modeTicks);
            if(client.getOverlay()!=null)return;
            if(phase==0 && ++ticks==20) {
                phase=1;
                client.options.getViewDistance().setValue(4);client.options.getSimulationDistance().setValue(4);client.options.getMaxFps().setValue(60);
                LevelInfo info=new LevelInfo("Cell visual test",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),DataConfiguration.SAFE_MODE);
                client.createIntegratedServerLoader().createAndStart("cell-qa-"+System.currentTimeMillis(),info,new GeneratorOptions(42L,false,false),
                        registry->registry.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder(),new TitleScreen());
            }
            if(phase==1 && client.player!=null && client.world!=null) {
                client.player.networkHandler.sendChatCommand("cell enter");phase=2;
            }
            if(phase==2 && CellClient.active && client.getCameraEntity() instanceof CellEntity cell) {
                modeTicks++;
                if(modeTicks==10) { start=cell.getPos();client.options.forwardKey.setPressed(true); }
                if(modeTicks==40)client.options.forwardKey.setPressed(false);
                if(modeTicks==65) {
                    SporeCell.LOGGER.info("Render diagnostics: frames={}, culling={}, thirdPerson={}, {}",renderedFrames,cullingAccepted,
                            client.gameRenderer.getCamera().isThirdPerson(),client.worldRenderer.getEntitiesDebugString());
                    if(renderedFrames==0)throw new AssertionError("Controlled cell was never rendered; culling="+cullingAccepted);
                    if(cell.getPos().squaredDistanceTo(start)<.1)throw new AssertionError("Client WASD input did not move the cell");
                    ScreenshotRecorder.saveScreenshot(client.runDirectory,"cell-world-"+imageTag+".png",client.getFramebuffer(),t->SporeCell.LOGGER.info("World screenshot: {}",t.getString()));
                }
                if(modeTicks==80) {
                    initialDna=CellClient.dna;
                    net.minecraft.client.option.KeyBinding.onKeyPressed(net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(org.lwjgl.glfw.GLFW.GLFW_KEY_G));
                }
                if(modeTicks==100) {
                    if(!(client.currentScreen instanceof CellEditor screen))throw new AssertionError("Network action did not open editor");
                    int step=Math.max(8,Math.min(27,Math.min((screen.height-96)/9,(screen.width-270)/9)));
                    screen.mouseClicked(132+5*step+step/2d,46+4*step+step/2d,0);
                    screen.mouseClicked(screen.width/2d-56,screen.height-19,0);
                }
                if(modeTicks==135) {
                    if(client.currentScreen instanceof CellEditor || CellClient.body.pieces().size()!=4 || CellClient.dna!=initialDna-2)
                        throw new AssertionError("Editor changes did not return to the world with the correct DNA budget");
                    ScreenshotRecorder.saveScreenshot(client.runDirectory,"cell-evolved-"+imageTag+".png",client.getFramebuffer(),t->SporeCell.LOGGER.info("Evolved screenshot: {}",t.getString()));
                }
                if(modeTicks==160) { client.player.networkHandler.sendChatCommand("cell leave");phase=3; }
            }
            if(phase==3 && !CellClient.active && client.currentScreen==null && client.world!=null
                    && !client.world.getRegistryKey().equals(SporeCell.POND)) {
                if(client.getCameraEntity()!=client.player)throw new AssertionError("Leaving did not restore camera");
                net.minecraft.client.option.KeyBinding.onKeyPressed(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(client.options.inventoryKey));
                phase=4;
            }
            if(phase==4 && ++returnTicks==10) {
                if(!(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)
                        && !(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen))
                    throw new AssertionError("Vanilla inventory binding stopped working after cell mode: "+client.currentScreen);
                SporeCell.LOGGER.info("WORLD VISUAL TEST PASSED: movement, rendered cell, editor hotkey, DNA, return and vanilla inventory");
                client.setScreen(null);client.scheduleStop();phase=5;
            }
        });
    }
}
