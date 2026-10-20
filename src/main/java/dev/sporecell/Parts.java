package dev.sporecell;

import dev.sporecell.core.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Parts {
    public static Map<Part, PartSpec> SPECS;
    private Parts() {}
    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("sporecell-parts.properties");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                try (InputStream defaults = Parts.class.getResourceAsStream("/parts.properties")) {
                    Files.copy(Objects.requireNonNull(defaults),path);
                }
            }
            Properties props = new Properties(); try (Reader r=Files.newBufferedReader(path)) { props.load(r); }
            EnumMap<Part,PartSpec> result = new EnumMap<>(Part.class);
            for (Part part : Part.values()) {
                String[] v = props.getProperty(part.name(),"").split(",");
                if (v.length != 7) throw new IllegalArgumentException("Expected 7 values for "+part);
                result.put(part,new PartSpec(Integer.parseInt(v[0].trim()),Double.parseDouble(v[1]),Double.parseDouble(v[2]),
                        Double.parseDouble(v[3]),Double.parseDouble(v[4]),Double.parseDouble(v[5]),Integer.parseInt(v[6].trim(),16)));
            }
            if (result.get(Part.CORE).cost()!=0) throw new IllegalArgumentException("CORE cost must be zero");
            SPECS = Collections.unmodifiableMap(result);
        } catch (IOException | RuntimeException e) { throw new IllegalStateException("Invalid parts config: "+path,e); }
    }
}
