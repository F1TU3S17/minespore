import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.*;
import ghidra.program.model.data.AbstractStringDataType;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.util.DefinedDataIterator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local research export only. Decompiled game code is never part of the Minecraft mod. */
public class SporeResearchExport extends GhidraScript {
    private File root;
    private PrintWriter writer(String name) throws Exception { return new PrintWriter(new File(root,name),StandardCharsets.UTF_8); }
    private String clean(Object value) { return String.valueOf(value).replace("\t","\\t").replace("\r","\\r").replace("\n","\\n"); }
    private Address address(String value) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(Long.parseUnsignedLong(value.replace("0x",""),16)); }
    private String owner(Address address) {
        Function f=currentProgram.getFunctionManager().getFunctionContaining(address);
        return f==null?"unknown":f.getEntryPoint().toString();
    }
    @Override public void run() throws Exception {
        String[] args=getScriptArgs();root=new File(args[0]);root.mkdirs();
        try(PrintWriter w=writer("identity.txt")) {
            w.println("executable="+currentProgram.getExecutablePath());w.println("md5="+currentProgram.getExecutableMD5());
            w.println("functions="+currentProgram.getFunctionManager().getFunctionCount());
            w.println("mode="+args[1]);w.println("Static observations; indirect targets and inferred types may be incomplete.");
        }
        if(args[1].equals("index"))index();
        else if(args[1].equals("decompile"))decompile(args[2]);
        else if(args[1].equals("xref"))xrefs(args[2]);
        else if(args[1].equals("data"))data(args[2]);
        else throw new IllegalArgumentException("Expected index, decompile, xref or data");
    }
    private void data(String request) throws Exception {
        try(PrintWriter w=writer("memory-words.tsv")) {
            w.println("address\tlabel\thex_bits\tint32\tfloat32\tinitialized");
            for(String line:Files.readAllLines(Path.of(request),StandardCharsets.UTF_8)) {
                if(line.isBlank()||line.startsWith("#"))continue;
                String[] fields=line.trim().split("\\s+",3);Address start=address(fields[0]);
                int count=Integer.parseInt(fields[1]);if(count<1||count>256)throw new IllegalArgumentException("Word count must be 1..256");
                for(int i=0;i<count;i++) {
                    Address a=start.add(i*4);var block=currentProgram.getMemory().getBlock(a);
                    boolean initialized=block!=null&&block.isInitialized()&&block.contains(a.add(3));
                    if(!initialized) { w.println(a+"\t"+(fields.length>2?fields[2]:"")+"\tunknown\tunknown\tunknown\tfalse");continue; }
                    int bits=currentProgram.getMemory().getInt(a);
                    w.println(a+"\t"+(fields.length>2?fields[2]:"")+"\t"+String.format("%08x",bits)+"\t"+bits+"\t"+Float.intBitsToFloat(bits)+"\ttrue");
                }
            }
        }
    }
    private void index() throws Exception {
        try(PrintWriter w=writer("functions.tsv")) {
            w.println("address\tname\tbytes\tparameters\tcalling_convention");
            for(Function f:currentProgram.getFunctionManager().getFunctions(true)) {
                monitor.checkCancelled();w.println(f.getEntryPoint()+"\t"+clean(f.getName(true))+"\t"+f.getBody().getNumAddresses()+"\t"+f.getParameterCount()+"\t"+f.getCallingConventionName());
            }
        }
        try(PrintWriter all=writer("strings.tsv");PrintWriter strings=writer("cell-anchors.tsv");PrintWriter refs=writer("cell-anchor-xrefs.tsv")) {
            all.println("address\tvalue");
            strings.println("address\tvalue");refs.println("anchor\tfrom\tfunction\ttype");
            for(Data data:DefinedDataIterator.byDataType(currentProgram,t->t instanceof AbstractStringDataType)) {
                monitor.checkCancelled();String value=String.valueOf(data.getValue()),lower=value.toLowerCase(Locale.ROOT);
                all.println(data.getAddress()+"\t"+clean(value));
                if(!(lower.contains("cell")||lower.contains("flagell")||lower.contains("cilia")||lower.contains("herbiv")||lower.contains("carniv")))continue;
                strings.println(data.getAddress()+"\t"+clean(value));
                for(Reference r:currentProgram.getReferenceManager().getReferencesTo(data.getAddress()))
                    refs.println(data.getAddress()+"\t"+r.getFromAddress()+"\t"+owner(r.getFromAddress())+"\t"+r.getReferenceType());
            }
        }
        println("Spore index exported to "+root);
    }
    private void xrefs(String request) throws Exception {
        try(PrintWriter w=writer("requested-xrefs.tsv")) {
            w.println("target\tfrom\tfunction\ttype\tinstruction");
            for(String line:Files.readAllLines(Path.of(request),StandardCharsets.UTF_8)) {
                if(line.isBlank()||line.startsWith("#"))continue;
                Address target=address(line.trim().split("\\s+")[0]);
                for(Reference r:currentProgram.getReferenceManager().getReferencesTo(target))
                    w.println(target+"\t"+r.getFromAddress()+"\t"+owner(r.getFromAddress())+"\t"+r.getReferenceType()+"\t"+clean(currentProgram.getListing().getInstructionAt(r.getFromAddress())));
            }
        }
    }
    private void decompile(String request) throws Exception {
        DecompInterface decompiler=new DecompInterface();decompiler.openProgram(currentProgram);
        try(PrintWriter status=writer("decompile-status.tsv")) {
            status.println("address\tlabel\tstatus\tmessage");
            for(String line:Files.readAllLines(Path.of(request),StandardCharsets.UTF_8)) {
                monitor.checkCancelled();if(line.isBlank()||line.startsWith("#"))continue;
                String[] fields=line.trim().split("\\s+",2);Address entry=address(fields[0]);
                Function f=currentProgram.getFunctionManager().getFunctionAt(entry);
                String label=fields.length>1?fields[1]:"unclassified";
                if(f==null) { status.println(entry+"\t"+label+"\tUNKNOWN\tNo defined function");continue; }
                String stem=f.getEntryPoint().toString();
                DecompileResults result=decompiler.decompileFunction(f,90,monitor);
                AddressSet exportedBody=new AddressSet(f.getBody());
                if(result.decompileCompleted()&&result.getHighFunction()!=null)
                    for(var block:result.getHighFunction().getBasicBlocks())
                        if(block.getStart()!=null&&block.getStop()!=null&&block.getStart().compareTo(block.getStop())<=0)
                            exportedBody.addRange(block.getStart(),block.getStop());
                try(PrintWriter c=writer(stem+".c");PrintWriter asm=writer(stem+".asm");PrintWriter links=writer(stem+"-links.tsv")) {
                    c.println("/* Ghidra pseudocode; not original source. Analyst label (hypothesis): "+label+" */");
                    c.println("/* Entry: "+entry+"; body bytes: "+f.getBody().getNumAddresses()+"; MD5: "+currentProgram.getExecutableMD5()+" */");
                    c.println("/* ASM/xrefs include decompiler-reached blocks; address coverage bytes: "+exportedBody.getNumAddresses()+" */");
                    if(result.decompileCompleted())c.println(result.getDecompiledFunction().getC());
                    else c.println("/* UNKNOWN: "+result.getErrorMessage()+" */");
                    links.println("direction\tfrom\tto\ttype\tfunction");
                    for(Reference r:currentProgram.getReferenceManager().getReferencesTo(entry))
                        links.println("incoming\t"+r.getFromAddress()+"\t"+entry+"\t"+r.getReferenceType()+"\t"+owner(r.getFromAddress()));
                    for(Instruction instruction:currentProgram.getListing().getInstructions(exportedBody,true)) {
                        asm.println(instruction.getAddress()+"\t"+instruction);
                        for(Reference r:instruction.getReferencesFrom())
                            links.println("outgoing\t"+instruction.getAddress()+"\t"+r.getToAddress()+"\t"+r.getReferenceType()+"\t"+owner(r.getToAddress()));
                    }
                }
                status.println(entry+"\t"+label+"\t"+(result.decompileCompleted()?"OK":"UNKNOWN")+"\t"+clean(result.getErrorMessage()));status.flush();
                println("Exported "+entry+" "+label);
            }
        } finally { decompiler.dispose(); }
    }
}
