package nvyc.internal;

import nvyc.data.FunctionData;
import nvyc.data.NASTNode;
import nvyc.data.NodeStream;
import nvyc.data.NodeType;
import nvyc.generation.LLVMGenerator;
import nvyc.generation.Lexer;
import nvyc.generation.Parser;
import nvyc.processing.ASTCleanup;
import nvyc.processing.ErrorChecker;
import nvyc.processing.Preprocess;
import nvyc.utils.NvyError;

import java.io.*;
import java.util.*;

public class Test {


    private static final boolean CHECKPOINTS = false;

    static Lexer lex = Lexer.getInstance();

    static LLVMGenerator llvm = new LLVMGenerator();
    static NvyError err = new NvyError();
    static Parser parse = new Parser();
    static ErrorChecker errcheck = new ErrorChecker();
    static Preprocess pre = new Preprocess();
    static ASTCleanup cleanup = new ASTCleanup();
    //static ValidationPass pass = new ValidationPass();

    private static boolean flatten = false;

    static FunctionData fdata = FunctionData.getInstance();

    static String input;
    static String output;

    public static void main(String[] args) throws IOException {
        String dir = "./";

        // nvyc <input> <flags> -o <output>
        input = args[0];
        output = args[1];

        if(output.endsWith(".tr")) NvyError.FINAL_TREE = true;
        if(output.endsWith(".flat")) flatten = true;

        if(!err.NV_FILE_IS_SOURCE(input)) {
            err.NV_STDERRF("%s is not an Nvy source file! Expected %s.nvy%n", input, input);
            System.exit(1);
        }

        String fullPath = dir + input;
        if(!err.NV_FILE_EXISTS(fullPath)) {
            err.NV_STDERRF("File %s does not exist%n", fullPath);
            System.exit(1);
        }

        // Generate LLVM code
        String target_triple = "target triple = \"x86_64-pc-linux-gnu\"\n\n";
        List<String> llvmir = generateLLVM(input, dir);
        llvmir.add(0, target_triple);

        writeToFile(output + "_nvy_tmp.ll", llvmir, "");

    }

    static void writeToFile(String path, List<String> out, String aux) throws IOException {
        File f = new File(path);
        BufferedWriter writer = new BufferedWriter(new FileWriter(f));
        for(String s : out) {
            writer.write(s + aux);
        }
        writer.close();
    }

    static List<String> generateLLVM(String inputPath, String dir) throws FileNotFoundException {
        File f = new File(dir + System.getProperty("file.separator") + inputPath);
        BufferedReader reader = new BufferedReader(new FileReader(f));
        List<String> list = new ArrayList<>(reader.lines().toList());

        long start = 0, end = 0;
        // Preprocessor pass
        if(NvyError.PROFILING) start = System.nanoTime();
        pre.removeInlineComments(list);
        list = pre.resolveImports(inputPath, list);

        if(output.endsWith("nvss")) {
            try {
                writeToFile(output, list, "\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.exit(0);
        }

        if(CHECKPOINTS) err.NV_TMP("Passed preprocessor");
        Map<String, String> fmap = pre.getFunctionNameMap();
        if(NvyError.PROFILING) end = System.nanoTime();
        if(NvyError.PROFILING) System.out.println("Preprocessor phase: " + (end - start)/1_000_000.0);

        if(NvyError.PROFILING) start = System.nanoTime();
        NodeStream in = lex.lex(list);
        pre.preprocess(in);
        pre.resolveFunCalls(in);

        if(NvyError.PROFILING) end = System.nanoTime();
        if(NvyError.PROFILING) System.out.println("Lexer2 phase: " + (end - start)/1_000_000.0);
        if(CHECKPOINTS) err.NV_TMP("Passed lexer");


        // Parser pass
        if(NvyError.PROFILING) start = System.nanoTime();
        List<NodeStream> parseList = parse.parseList(in);

        List<NASTNode> nn = new ArrayList<>();
        for(NodeStream l : parseList) {
            //   NASTNode parsedNode = parse.parse(l, -1);
            NASTNode parsedNode = parse.parse(l);
            nn.add(parsedNode);
        }
        if(NvyError.PROFILING) end = System.nanoTime();
        if(NvyError.PROFILING) System.out.println("Parser phase: " + (end - start)/1_000_000.0);
        if(CHECKPOINTS) err.NV_TMP("Passed parser");


        // TODO only do this during compilation, not beforehand, bc of duplicate variables in other funcs
        //err.auto("Doing type resolution");
        /*TypeAnalysis an = new TypeAnalysis();
        for(NASTNode nnode : nn) {
            an.buildTypes(nnode);
        }*/
        //err.auto(VariableData.getInstance().getTypeMap());
        //err.auto("Resolved types");

        // At this point, read flags and clean tree

        if(NvyError.PROFILING) start = System.nanoTime();
        cleanup.resolveMangledNames(nn, fmap);

        // Validate tree
        errcheck.validateTree(nn);

        // For single reference enforcer
        // if(!enf.checkReferences(nn)) System.exit(1);

        if(NvyError.PROFILING) end = System.nanoTime();
        if(NvyError.PROFILING) System.out.println("Validation phase: " + (end - start)/1_000_000.0);
        if(CHECKPOINTS) err.NV_TMP("Passed validation");

        if(flatten) {
            for(NASTNode node : nn) {
                List<String> flat = node.flatten();
                flat.forEach(System.out::println);
            }
            System.exit(0);
        }


        if(NvyError.FINAL_TREE) {
            System.out.println("---- FINAL TREE ----");
            System.out.println(nn);
        }

        /*Map<String, NodeType> fumap = parse.getReturnMap();
        for(String s : fumap.keySet()) {
            fdata.setReturnType(s, fumap.get(s));
        }*/

        // LLVM pass
        if(NvyError.PROFILING) start = System.nanoTime();
        //llvm.setReturnMap(parse.getReturnMap());
        //llvm.setFunctionParameters(parse.getFunctionParameters());
        List<String> ll = llvm.compileLLVM(nn);

        for(String s : llvm.getGlobalValues()) {
            ll.add(0, s);
        }

        /*List<String> ll = llvm.compileLLVM(nn);
        for(String s : llvm.getGlobals()) {
            ll.add(0, s);
        }*/

        if(NvyError.PROFILING) end = System.nanoTime();
        if(NvyError.PROFILING) System.out.println("LLVM Generation phase: " + (end - start)/1_000_000.0);
        if(CHECKPOINTS) err.NV_TMP("Passed codegen");

        return ll;
    }
}