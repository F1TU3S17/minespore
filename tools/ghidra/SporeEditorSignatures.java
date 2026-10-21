import ghidra.app.script.GhidraScript;
import ghidra.program.model.data.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.SourceType;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** One exact EXE/RET/copy-loop backed signature, without speculative global type imports. */
public class SporeEditorSignatures extends GhidraScript {
    @Override public void run() throws Exception {
        if(!"32aad7cad752048c9686ff9eb924263d".equals(currentProgram.getExecutableMD5()))throw new IllegalStateException("Wrong binary");
        var f=getFunctionAt(toAddr("004409b0"));
        if(f==null)throw new IllegalStateException("Missing verified matrix setter");
        StructureDataType matrix=new StructureDataType("SporeEvidenceMatrix3",0);
        for(int i=0;i<9;i++)matrix.add(FloatDataType.dataType,"m"+i,null);
        DataType type=currentProgram.getDataTypeManager().addDataType(matrix,DataTypeConflictHandler.REPLACE_HANDLER);
        f.setCallingConvention("__thiscall");f.setReturnType(VoidDataType.dataType,SourceType.USER_DEFINED);
        f.replaceParameters(Function.FunctionUpdateType.DYNAMIC_STORAGE_ALL_PARAMS,true,SourceType.USER_DEFINED,new ParameterImpl("rotation",type,currentProgram));
        f.setStackPurgeSize(36);
        try(PrintWriter out=new PrintWriter(new File(getScriptArgs()[0]),StandardCharsets.UTF_8)){out.println("address\tsignature\tevidence");out.println("004409b0\t"+f.getSignature()+"\tREP MOVSD count 9 to this+f0; RET 24h");}
    }
}
