import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import java.io.*;
import java.nio.charset.StandardCharsets;
public class SporeResolveEditorTransforms extends GhidraScript {
    @Override public void run() throws Exception {
        if(!"32aad7cad752048c9686ff9eb924263d".equals(currentProgram.getExecutableMD5()))throw new IllegalStateException("Wrong input binary");
        String[] addresses={"00440670","00483ce0","00482f30"};
        try(PrintWriter out=new PrintWriter(new FileOutputStream(getScriptArgs()[0]),true,StandardCharsets.UTF_8)) {
            for(String value:addresses){
                Function f=getFunctionContaining(toAddr(value));
                if(f!=null){out.println(f.getEntryPoint()+" transform_containing_"+value);println(value+" belongs to "+f.getEntryPoint()+" "+f.getName());}
                else println("Unresolved: "+value);
            }
        }
    }
}
