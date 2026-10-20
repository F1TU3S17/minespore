// Independent static investigation. Never imported into the Minecraft implementation.
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.data.AbstractStringDataType;
import ghidra.program.util.DefinedDataIterator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CellStageProbe extends GhidraScript {
    @Override public void run() throws Exception {
        File out=new File(getScriptArgs()[0]);
        try(PrintWriter writer=new PrintWriter(out,StandardCharsets.UTF_8)) {
            writer.println("Static Ghidra probe; pseudocode is not original source. No game executed.");
            writer.println("Target: "+currentProgram.getExecutablePath());
            writer.println("MD5: "+currentProgram.getExecutableMD5());
            writer.println("Functions: "+currentProgram.getFunctionManager().getFunctionCount());
            Set<Function> candidates=new LinkedHashSet<>();
            for(Data data:DefinedDataIterator.byDataType(currentProgram,type->type instanceof AbstractStringDataType)) {
                monitor.checkCancelled();String value=String.valueOf(data.getValue());String lower=value.toLowerCase(Locale.ROOT);
                if(!lower.matches("(?s).*(cell.*(speed|health|food|turn|dna)|flagell|cilia).*"))continue;
                writer.println("STRING "+data.getAddress()+" "+value.replace('\n',' '));
                for(Reference ref:currentProgram.getReferenceManager().getReferencesTo(data.getAddress())) {
                    Function function=currentProgram.getFunctionManager().getFunctionContaining(ref.getFromAddress());
                    writer.println(" XREF "+ref.getFromAddress()+" FUNCTION "+(function==null?"unknown":function.getEntryPoint()));
                    if(function!=null)candidates.add(function);
                }
            }
            writer.flush();DecompInterface decompiler=new DecompInterface();decompiler.openProgram(currentProgram);
            try {
                int count=0;
                for(Function function:candidates) {
                    if(count++>=4)break;
                    writer.println("\nCANDIDATE "+function.getEntryPoint()+" "+function.getName());writer.flush();
                    DecompileResults result=decompiler.decompileFunction(function,60,monitor);
                    if(result.decompileCompleted())writer.println(result.getDecompiledFunction().getC());
                    else writer.println("UNKNOWN: "+result.getErrorMessage());
                    writer.flush();
                }
                writer.println("DONE. Candidate references establish anchors, not gameplay semantics.");
            } finally { decompiler.dispose(); }
        }
    }
}
