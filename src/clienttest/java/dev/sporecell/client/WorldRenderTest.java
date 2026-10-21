package dev.sporecell.client;

import dev.sporecell.*;
import dev.sporecell.core.*;
import dev.sporecell.test.mixin.MouseInputProbe;
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
import org.lwjgl.glfw.GLFW;

/** Full real-client flow: keyboard, LMB steering, mating, evolution, transformed offspring and persistence. */
public final class WorldRenderTest implements ClientModInitializer {
    public static int renderedFrames;
    public static boolean cullingAccepted;
    public static final int[] playerFrames=new int[11],npcFrames=new int[11],fogFrames=new int[11];
    private int scaleLevel=6;
    private int phase,ticks,modeTicks,phaseTicks;
    private final String tag=Long.toString(System.currentTimeMillis());
    private final long started=System.nanoTime();
    private Vec3d start,mouseStart;
    private int budget;
    private final boolean dedicated=Boolean.getBoolean("sporecell.test.dedicated");
    @Override public void onInitializeClient(){
        if(!Boolean.getBoolean("sporecell.test.world"))return;
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if((System.nanoTime()-started)/1e9>240)throw new AssertionError("Client scenario timed out: phase "+phase+", tick "+modeTicks);
            if(client.getOverlay()!=null)return;
            if(phase==0&&++ticks==20){
                phase=1;client.options.getViewDistance().setValue(4);client.options.getSimulationDistance().setValue(5);client.options.getMaxFps().setValue(60);
                if(dedicated){
                    if(!(client.currentScreen instanceof TitleScreen))client.setScreen(new TitleScreen());
                    var button=net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(client.currentScreen).stream()
                        .filter(b->b.getMessage().getString().equals("Spore: стадия клетки")).findFirst().orElseThrow();
                    ((net.minecraft.client.gui.widget.ButtonWidget)button).onPress();
                    var menu=(CellModeScreen)client.currentScreen;menu.mouseClicked(menu.width/2d,menu.height/2d+3,0);
                }else{
                    var info=new LevelInfo("Cell visual test",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),DataConfiguration.SAFE_MODE);
                    client.createIntegratedServerLoader().createAndStart("cell-qa-"+System.currentTimeMillis(),info,new GeneratorOptions(42L,false,false),
                        registry->registry.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder(),new TitleScreen());
                }
            }
            if(phase==1&&client.player!=null&&client.world!=null){
                if(!dedicated)client.player.networkHandler.sendChatCommand("cell enter");phase=2;
            }
            if(phase==2&&CellClient.active&&client.getCameraEntity() instanceof CellEntity cell){
                modeTicks++;
                if(modeTicks==10){start=cell.getPos();client.options.forwardKey.setPressed(true);}
                if(modeTicks==40)client.options.forwardKey.setPressed(false);
                if(modeTicks==50){
                    if(cell.getZ()>=start.z-.2||renderedFrames==0||PondRenderer.renderedFrames==0)throw new AssertionError("Keyboard or world render failed");
                    client.getServer().execute(()->{
                        var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                        var profile=CellSave.get(client.getServer()).profile(player.getUuid());
                        profile.progress=200;profile.dna=Evolution.earnedDna(200);profile.eaten=5;profile.plantFood=dedicated?0:50;profile.animalFood=dedicated?50:0;
                        var controlled=(CellEntity)player.getCameraEntity();controlled.level(3);controlled.hurtCooldown=1000;
                        CellDiscovery.drop((net.minecraft.server.world.ServerWorld)controlled.getWorld(),controlled.getX()+.2,controlled.getZ(),3,Part.POISON,false);
                    });
                }
                if(modeTicks==65){
                    if(!CellClient.unlocked.contains(Part.POISON))throw new AssertionError("World discovery did not reach the client");screenshot(client,"discovery");
                    mouseStart=cell.getPos();
                    GLFW.glfwSetCursorPos(client.getWindow().getHandle(),client.getWindow().getWidth()*.82,client.getWindow().getHeight()*.5);
                    ((MouseInputProbe)client.mouse).sporetest$cursor(client.getWindow().getHandle(),client.getWindow().getWidth()*.82,client.getWindow().getHeight()*.5);
                    client.getServer().execute(()->{
                        var world=client.getServer().getWorld(SporeCell.POND);
                        var removed=new java.util.ArrayList<CellEntity>();
                        for(var entity:world.iterateEntities())if(entity instanceof CellEntity other&&other.owner==null&&other.kind()<CellEntity.MATE)removed.add(other);
                        removed.forEach(CellEntity::discard);
                    });
                }
                if(modeTicks==67)((MouseInputProbe)client.mouse).sporetest$button(client.getWindow().getHandle(),0,GLFW.GLFW_PRESS,0);
                if(modeTicks==80)SporeCell.LOGGER.info("Mouse diagnostics: held={}, locked={}, cursor={}/{}, direction={}, start={}, current={}",MouseSteering.held,client.mouse.isCursorLocked(),client.mouse.getX(),client.mouse.getY(),MouseSteering.direction(client,cell),mouseStart,cell.getPos());
                if(modeTicks==100){
                    ((MouseInputProbe)client.mouse).sporetest$button(client.getWindow().getHandle(),0,GLFW.GLFW_RELEASE,0);
                    if(cell.getX()<=mouseStart.x+.2||client.mouse.isCursorLocked())throw new AssertionError("Holding LMB did not steer right with an unlocked cursor");
                }
                if(modeTicks==110){
                    screenshot(client,"world");budget=CellClient.dna;
                    net.minecraft.client.option.KeyBinding.onKeyPressed(net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_G));phase=3;phaseTicks=0;
                }
            }
            if(phase==3&&client.currentScreen instanceof CellEvolutionScreen screen&&CellClient.mating==2&&++phaseTicks>=6){
                screenshot(client,"evolution");
                screen.mouseClicked(screen.width/2d,screen.height-48,0);phase=4;
            }
            if(phase==4&&client.currentScreen instanceof CellEditor screen){
                screen.mouseClicked(108,43+7*15+8,0);
                screen.mouseClicked(70,43+3*15+7,0);
                var point=screen.view.project(.2,.42,0);
                screen.mouseClicked(point.x,point.y,0);screen.mouseReleased(point.x,point.y,0);
                screen.mouseScrolled(point.x,point.y,0,5);screen.keyPressed(GLFW.GLFW_KEY_R,0,0);screen.keyPressed(GLFW.GLFW_KEY_R,0,0);
                screen.draft().validate();
                if(screen.draft().pieces().size()!=4||screen.draft().pieces().getLast().size()!=1.5)throw new AssertionError("3D editor did not create a transformed attachment");
                phase=5;phaseTicks=0;
            }
            if(phase==5&&++phaseTicks==8&&client.currentScreen instanceof CellEditor screen){
                screenshot(client,"editor-3d");
                screen.mouseClicked(screen.width/2d-56,screen.height-18,0);phase=6;
            }
            if(phase==6&&CellClient.generation==1&&client.currentScreen==null){
                var part=CellClient.body.pieces().getLast();
                if(!part.mounted()||part.size()!=1.5||part.angle()!=30||part.part()!=Part.POISON||CellClient.dna!=budget-10)throw new AssertionError("Offspring lost transforms or DNA");
                phase=7;phaseTicks=0;
            }
            if(phase==7&&++phaseTicks==15){
                screenshot(client,"offspring");
                client.getServer().execute(()->{
                    var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());var save=CellSave.get(client.getServer());
                    var restored=CellSave.TYPE.deserializer().apply(save.writeNbt(new net.minecraft.nbt.NbtCompound(),player.getRegistryManager()),player.getRegistryManager()).profile(player.getUuid());
                    if(restored.generation!=1||restored.history.size()!=1||restored.blueprint.pieces().getLast().size()!=1.5)throw new AssertionError("Persistent offspring data is incomplete");
                    if(dedicated){
                        if(!save.dedicatedMode||!restored.blueprint.stats(Parts.SPECS).carnivore())throw new AssertionError("Dedicated carnivore mode was lost");
                        CellGame.leave(player);if(!save.profile(player.getUuid()).active)throw new AssertionError("Dedicated mode escaped");
                        var actor=(CellEntity)player.getCameraEntity();actor.setPosition(0,66,0);actor.setYaw(0);actor.setVelocity(Vec3d.ZERO);
                        var prey=new CellEntity(SporeCell.CELL,actor.getWorld());prey.kind(CellEntity.GRAZER);prey.level(actor.level());
                        prey.blueprint(new Blueprint(java.util.List.of(new Blueprint.Piece(0,0,Part.CORE,0))));
                        prey.setPosition(0,66,-1.35*actor.size());actor.getWorld().spawnEntity(prey);
                    }
                });
                if(dedicated){phase=10;phaseTicks=0;}else{client.player.networkHandler.sendChatCommand("cell leave");phase=8;}
            }
            if(phase==10){
                if(++phaseTicks>100)throw new AssertionError("Attack target health was not synchronized");
                if(CellClient.targetId>=0&&CellClient.targetHealth<6){phase=11;phaseTicks=0;}
            }
            if(phase==11&&++phaseTicks==3){
                screenshot(client,"combat");
                SporeCell.LOGGER.info("DEDICATED MODE TEST PASSED: LMB, mating, evolution, 3D editor, offspring, DNA, persistence, target health");
                phase=12;phaseTicks=0;
            }
            if(phase==12){
                if(phaseTicks++==0){
                    int level=scaleLevel;playerFrames[level]=npcFrames[level]=fogFrames[level]=0;
                    client.getServer().execute(()->{
                        var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                        var actor=(CellEntity)player.getCameraEntity();actor.level(level);actor.hurtCooldown=10000;
                        CellSave.get(client.getServer()).profile(player.getUuid()).progress=(level-1)*100;
                        var other=new CellEntity(SporeCell.CELL,actor.getWorld());other.kind(CellEntity.GRAZER);other.level(level);
                        other.blueprint(CellSpecies.create(level,false));other.setPosition(actor.getX()+4*actor.size(),66.05,actor.getZ());actor.getWorld().spawnEntity(other);
                    });
                }
                if(phaseTicks==70){
                    if(playerFrames[scaleLevel]<5||npcFrames[scaleLevel]<5)throw new AssertionError("Level "+scaleLevel+" entities disappeared: player="+playerFrames[scaleLevel]+", NPC="+npcFrames[scaleLevel]);
                    if(fogFrames[scaleLevel]<5)throw new AssertionError("Vanilla fog obscures high-level cell colors: "+scaleLevel);
                    screenshot(client,"level-"+scaleLevel);
                    if(++scaleLevel>10){SporeCell.LOGGER.info("HIGH LEVEL VISIBILITY TEST PASSED: player and NPC rendered on every level 6–10");client.scheduleStop();phase=99;}else phaseTicks=0;
                }
            }
            if(phase==8&&!CellClient.active&&client.world!=null&&!client.world.getRegistryKey().equals(SporeCell.POND)){
                if(client.getCameraEntity()!=client.player)throw new AssertionError("Vanilla camera not restored");
                net.minecraft.client.option.KeyBinding.onKeyPressed(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(client.options.inventoryKey));phase=9;phaseTicks=0;
            }
            if(phase==9&&++phaseTicks==10){
                if(!(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen)&&!(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen))throw new AssertionError("Vanilla inventory broken after cell mode");
                SporeCell.LOGGER.info("WORLD TEST PASSED: keyboard, LMB, mating, evolution, 3D editor, offspring, persistence, vanilla return");
                client.setScreen(null);client.scheduleStop();phase=99;
            }
        });
    }
    private void screenshot(net.minecraft.client.MinecraftClient client,String name){
        ScreenshotRecorder.saveScreenshot(client.runDirectory,"cell-"+name+"-"+tag+".png",client.getFramebuffer(),t->SporeCell.LOGGER.info("Scenario screenshot: {}",t.getString()));
    }
}
