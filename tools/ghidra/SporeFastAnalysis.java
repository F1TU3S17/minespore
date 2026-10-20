import ghidra.app.script.GhidraScript;
import ghidra.framework.options.Options;
import ghidra.program.model.listing.Program;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Retains code/data/reference analysis; skips costly speculative whole-program analyzers. */
public class SporeFastAnalysis extends GhidraScript {
    @Override public void run() throws Exception {
        Options options=currentProgram.getOptions(Program.ANALYSIS_PROPERTIES);
        File report=new File(getScriptArgs()[0]);
        try(PrintWriter writer=new PrintWriter(report,StandardCharsets.UTF_8)) {
            for(String name:options.getOptionNames()) {
                String lower=name.toLowerCase(Locale.ROOT);
                if(!name.contains(".") && (lower.contains("callee purge") || lower.contains("constant reference")
                        || lower.contains("decompiler switch") || lower.equals("function id")
                        || lower.contains("stack") || lower.contains("parameter id") || lower.contains("embedded media"))) {
                    try { options.setBoolean(name,false);writer.println("DISABLED\t"+name); }
                    catch(Exception e) { writer.println("UNKNOWN\t"+name+"\t"+e.getMessage()); }
                }
            }
        }
    }
}
