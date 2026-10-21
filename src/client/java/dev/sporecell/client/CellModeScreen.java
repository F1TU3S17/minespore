package dev.sporecell.client;

import dev.sporecell.core.Evolution;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.text.Text;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** A separate single-player save, with its own progress and cell-only entry flow. */
final class CellModeScreen extends Screen {
    private final Screen parent;
    CellModeScreen(Screen parent) {super(Text.literal("Spore: стадия клетки"));this.parent=parent;}
    @Override protected void init() {
        int y=height/2-32;
        addDrawableChild(ButtonWidget.builder(Text.literal("Травоядная клетка"),b->start(Evolution.Diet.HERBIVORE)).dimensions(width/2-105,y,210,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Хищная клетка"),b->start(Evolution.Diet.CARNIVORE)).dimensions(width/2-105,y+25,210,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Продолжить: выбор сохранения"),b->client.setScreen(new SelectWorldScreen(this))).dimensions(width/2-105,y+55,210,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Назад"),b->close()).dimensions(width/2-105,y+80,210,20).build());
    }
    private void start(Evolution.Diet diet) {
        CellClient.pendingDiet=diet;
        LevelInfo info=new LevelInfo("Spore Cell — "+(diet==Evolution.Diet.HERBIVORE?"Травоядное":"Хищник"),GameMode.SPECTATOR,false,Difficulty.NORMAL,false,new GameRules(),DataConfiguration.SAFE_MODE);
        client.createIntegratedServerLoader().createAndStart("spore-cell-"+System.currentTimeMillis(),info,new GeneratorOptions(System.nanoTime(),false,false),
            registry->registry.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder(),this);
    }
    @Override public void render(DrawContext draw,int mx,int my,float delta) {
        draw.fill(0,0,width,height,0xFF102A35);
        draw.drawCenteredTextWithShadow(textRenderer,"SPORE · СТАДИЯ КЛЕТКИ",width/2,35,0x95E5C9);
        draw.drawCenteredTextWithShadow(textRenderer,"Отдельный мир и собственная эволюция",width/2,55,0xD4E8EB);
        draw.drawCenteredTextWithShadow(textRenderer,"Всеядность открывается по мере роста. Редактор: G",width/2,height/2-52,0xD4E8EB);
        super.render(draw,mx,my,delta);
    }
    @Override public void close() {client.setScreen(parent);}
}
