import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.*;
import ghidra.program.model.data.*;
import ghidra.program.model.symbol.SourceType;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Analyst type annotations verified from stack accesses, call sites and RET in this exact EXE. */
public class SporeVerifiedCellSignatures extends GhidraScript {
    @Override public void run() throws Exception {
        if(!"32aad7cad752048c9686ff9eb924263d".equals(currentProgram.getExecutableMD5()))
            throw new IllegalStateException("Verified annotations apply only to the recorded Spore EXE MD5");
        try(PrintWriter w=new PrintWriter(new File(getScriptArgs()[0]),StandardCharsets.UTF_8)) {
            w.println("address\tbefore\tafter\tbasis");
            annotate(w,"00e837c0",FloatDataType.dataType,
                new DataType[]{UnsignedIntegerDataType.dataType,FloatDataType.dataType},new String[]{"propertyId","x"},
                "00e83809 property stack access; float pair interpolation; CALL sites push property ID and float; RET without immediate");
            annotate(w,"00e7ce10",VoidDataType.dataType,
                new DataType[]{IntegerDataType.dataType,BooleanDataType.dataType},new String[]{"amount","foodKind"},
                "00e7ce70 loads amount into EDI, integer ADD at 00e7ce74; low-byte flag at 00e7ce97; RET without immediate");
        }
    }
    private void annotate(PrintWriter w,String entry,DataType result,DataType[] types,String[] names,String basis) throws Exception {
        Function f=getFunctionAt(toAddr(entry));if(f==null)throw new IllegalStateException("Missing function "+entry);
        String before=f.getPrototypeString(false,false);
        f.setCallingConvention("__cdecl");f.setReturnType(result,SourceType.USER_DEFINED);
        Parameter[] parameters=new Parameter[types.length];
        for(int i=0;i<types.length;i++)parameters[i]=new ParameterImpl(names[i],types[i],currentProgram);
        f.replaceParameters(Function.FunctionUpdateType.DYNAMIC_STORAGE_ALL_PARAMS,true,SourceType.USER_DEFINED,parameters);
        w.println(entry+"\t"+before+"\t"+f.getPrototypeString(false,false)+"\t"+basis);
    }
}
