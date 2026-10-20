import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Bounded metadata repair from actual RET immediates; never guesses a purge size. */
public class SporeRepairStackPurge extends GhidraScript {
    @Override public void run() throws Exception {
        String[] args=getScriptArgs();Set<Function> selected=new LinkedHashSet<>();
        for(String path:args[1].split(";"))for(String line:Files.readAllLines(Path.of(path),StandardCharsets.UTF_8)) {
            if(line.isBlank()||line.startsWith("#"))continue;
            long value=Long.parseUnsignedLong(line.trim().split("\\s+")[0],16);
            Function f=currentProgram.getFunctionManager().getFunctionAt(currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(value));
            if(f!=null)selected.add(f);
        }
        Set<Function> scope=new LinkedHashSet<>(selected);
        for(Function f:selected)scope.addAll(f.getCalledFunctions(monitor));
        try(PrintWriter w=new PrintWriter(new File(args[0]),StandardCharsets.UTF_8)) {
            w.println("address\told_purge\tnew_purge\tstatus");
            for(Function f:scope) {
                if(f.isExternal())continue;
                Set<Integer> sizes=new HashSet<>();
                for(Instruction ins:currentProgram.getListing().getInstructions(f.getBody(),true))
                    if(ins.getMnemonicString().equalsIgnoreCase("RET"))sizes.add(ins.getNumOperands()==0?0:(int)ins.getScalar(0).getUnsignedValue());
                if(sizes.size()!=1) { w.println(f.getEntryPoint()+"\t"+f.getStackPurgeSize()+"\tunknown\tAmbiguous or no RET");continue; }
                int size=sizes.iterator().next(),old=f.getStackPurgeSize();f.setStackPurgeSize(size);
                w.println(f.getEntryPoint()+"\t"+old+"\t"+size+"\tRET verified");
            }
        }
    }
}
