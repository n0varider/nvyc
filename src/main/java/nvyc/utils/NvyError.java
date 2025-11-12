package nvyc.utils;

import nvyc.data.ErrorType;
import nvyc.data.NodeStream;
import nvyc.data.NodeType;

import java.io.File;

public class NvyError {

    public static final boolean PROFILING = false;                // Show runtime for each compilation phase
    public static final boolean VERBOSE = false;                  // Show all steps
    public static boolean INIDE = false;                    // Runs inside the IDE
    public static boolean FINAL_TREE = false;               // Print the final parse tree before LLVM conversion
    public static final boolean FOLD_CONSTANTS = true;            // Fold constants, strictly for debugging and not production
    public static final boolean ENABLE_CHECKPOINTS = true;        // Checkpoints for debugging. See NV_CHECKPOINT and NV_RESET_CHECKPOINT
    private static int checkpoint = 0;                      // Counter for checkpoints

    public static final String FAILCOMPILE_NOAUX = null;

    public void NV_CHECKPOINT() {
        if(ENABLE_CHECKPOINTS) {
            NV_STDOUTF("Checkpoint %d%n", checkpoint++);
        }
    }

    public void NV_RESET_CHECKPOINT() {
        checkpoint = 0;
    }

    public void printNodeUntil(NodeStream node, NodeType n) {
        NodeStream s = node.cutheadAndReturn();
        s = s.forwardType(n);
        s = s.cutoffAndReturn();
        System.out.println(s);
    }

    /*

        Compiler print functions to avoid a bunch of
        System.out.println to improve readability
        and searchability

     */


    public void NV_STDOUT(Object o) {
        System.out.println(o);
    }

    public void NV_FAILCOMPILE(int code, String msg) {
        NV_STDERRF("Failed to compile: %s\n",
                msg != null ?
                        "unknown error" :
                        msg
        );
        System.exit(code);
    }

    public void NV_TMP(String s) {
        NV_STDOUT(s);
    }

    public void NV_STDERRF(String format, Object... args) {
        System.err.printf(format, args);
    }

    public void NV_STDERR(String s) {
        NV_STDERRF(s + "\n");
    }

    public void NV_STDOUTF(String format, Object... args) {
        System.out.printf(format, args);
    }

    public void NV_STDOUT(String s) {
        NV_STDOUTF(s + "\n");
    }

    public boolean NV_FILE_EXISTS(String s) {
        File f = new File(s);
        return f.exists();
    }

    public boolean NV_FILE_IS_SOURCE(String s) {
        return s.endsWith(".nv");
    }

    public int FAILCOMPILE(ErrorType error, String aux) {
        System.out.println("nvc > Compilation failed");
        System.out.println(error.getMessage());
        if(aux != null) System.out.println(aux);
        System.exit(0);
        return -1;
    }

    public void auto(Object... args) {
        StringBuilder b = new StringBuilder();
        b.append("AUTOPRINT >> ");
        for(int i = 0; i < args.length; i++) {
            b.append("%s ");
        }
        b.append("%n");
        System.out.printf(b.toString(), args);
    }
}
