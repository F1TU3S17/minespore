package dev.sporecell;

import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Server-side timing/radius configuration, separate from numeric part characteristics. */
public final class CellAbilities {
    private CellAbilities(){}
    public static int poisonCooldown=60,poisonLifetime=80,poisonDuration=60,electricCooldown=50,stunDuration=10;
    public static double poisonRadius=1.8,electricRadius=2.4,jetBoost=1.5;
    public static void load(){
        var path=FabricLoader.getInstance().getConfigDir().resolve("sporecell-abilities.properties");
        try{
            var props=new Properties();
            try(var input=CellAbilities.class.getResourceAsStream("/abilities.properties")){props.load(input);}
            if(!Files.exists(path))try(var writer=Files.newBufferedWriter(path)){props.store(writer,"Cell ability balance");}
            try(var reader=Files.newBufferedReader(path)){props.load(reader);}
            poisonCooldown=integer(props,"poison.cooldown");poisonLifetime=integer(props,"poison.lifetime");poisonDuration=integer(props,"poison.duration");
            electricCooldown=integer(props,"electric.cooldown");stunDuration=integer(props,"electric.stun");
            poisonRadius=number(props,"poison.radius");electricRadius=number(props,"electric.radius");jetBoost=number(props,"jet.boost");
        }catch(IOException|RuntimeException ex){throw new IllegalStateException("Invalid ability config: "+path,ex);}
    }
    private static int integer(Properties p,String key){int value=Integer.parseInt(p.getProperty(key));if(value<1||value>1200)throw new IllegalArgumentException(key);return value;}
    private static double number(Properties p,String key){double value=Double.parseDouble(p.getProperty(key));if(!Double.isFinite(value)||value<=0||value>10)throw new IllegalArgumentException(key);return value;}
}
