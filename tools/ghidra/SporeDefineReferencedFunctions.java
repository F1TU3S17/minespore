import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Define only requested instruction entries corroborated by native calls or data pointers. */
public class SporeDefineReferencedFunctions extends GhidraScript {
    @Override public void run() throws Exception {
        String[] args=getScriptArgs();
        try(PrintWriter w=new PrintWriter(new File(args[0]),StandardCharsets.UTF_8)) {
            w.println("address\tstatus\tnative_reference\tbody_bytes");
            for(String line:Files.readAllLines(Path.of(args[1]),StandardCharsets.UTF_8)) {
                if(line.isBlank()||line.startsWith("#"))continue;
                String[] fields=line.trim().split("\\s+");
                Address entry=toAddr(fields[0]);
                Function f=getFunctionAt(entry);
                if(f!=null){w.println(entry+"\texisting\t\t"+f.getBody().getNumAddresses());continue;}
                if(getFunctionContaining(entry)!=null) {
                    w.println(entry+"\tunknown: overlaps another function\t\t0");continue;
                }
                String evidence=null;
                for(Reference r:currentProgram.getReferenceManager().getReferencesTo(entry)) {
                    var block=currentProgram.getMemory().getBlock(r.getFromAddress());
                    if(r.getReferenceType().isCall()||(r.getReferenceType().isData()&&block!=null&&!block.isExecute())) {
                        evidence=r.getFromAddress()+":"+r.getReferenceType();break;
                    }
                }
                if(evidence==null&&fields.length>=3&&fields[2].matches("[0-9a-fA-F]{8}")) {
                    Address pointer=toAddr(fields[2]);var block=currentProgram.getMemory().getBlock(pointer);
                    if(block!=null&&block.isInitialized()&&!block.isExecute()&&
                        Integer.toUnsignedLong(currentProgram.getMemory().getInt(pointer))==entry.getOffset())
                        evidence=pointer+":verified raw data pointer";
                }
                if(evidence==null){w.println(entry+"\tunknown: no corroborating reference\t\t0");continue;}
                if(getInstructionAt(entry)==null)disassemble(entry);
                if(getInstructionAt(entry)==null){w.println(entry+"\tunknown: disassembly failed\t"+evidence+"\t0");continue;}
                f=createFunction(entry,null);
                w.println(entry+"\t"+(f==null?"creation failed":"created")+"\t"+evidence+"\t"+(f==null?0:f.getBody().getNumAddresses()));
            }
        }
    }
}
