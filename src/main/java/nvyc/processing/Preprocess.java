package nvyc.processing;

import nvyc.data.NodeStream;
import nvyc.data.NodeType;
import nvyc.utils.NvyError;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Preprocess {

    NvyError err = new NvyError();
    Map<String, String> functionNameMap = new HashMap<>();

    public Map<String, String> getFunctionNameMap() {
        return functionNameMap;
    }

    public List<String> resolveImports(String module, List<String> init) {
        module = module.substring(0, module.length() - 3);
        init = mangleFunctions(module, init);
        List<String> result = new ArrayList<>();
        for (String s : init) {
            if (s.startsWith("%import")) {
                String lib = s.substring(8).trim();
                String dir = "./nvylib/" + lib; // Hardcoded for testing

                List<String> importedLines;
                if(!err.NV_FILE_EXISTS(dir)) {
                    // Create proper error handling eventually
                    err.NV_STDERRF("Error while importing: could not find %s%n", lib);
                    System.exit(1);
                }
                try (BufferedReader reader = new BufferedReader(new FileReader(dir))) {
                    importedLines = reader.lines().toList();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                result.addAll(resolveImports(lib, importedLines));
            } else {
                result.add(s);
            }
        }
        return result;
    }

    private List<String> mangleFunctions(String module, List<String> init) {
        List<String> newlist = new ArrayList<>();
        for(int i = 0; i < init.size(); i++) {
            String line = init.get(i);
            String temp;
            String replacement;

            if(line.startsWith("func")) {
                // func name(...) -> ... { ... }
                // 01234567...
                temp = line.substring(5, line.indexOf('('));
                if(temp.equals("main")) {
                    newlist.add(line);
                    continue;
                }
                // _nvlang_nvstd_4free_5
                String moduleId = module.replaceAll("[^a-zA-Z0-9]", "");
                String newname = String.format("_nvlang_%s_%d%s_%d", moduleId, temp.length(), temp, module.length());
                replacement = line.replace(temp, newname);
                // All function names in a module will be "modulename_function"
                newlist.add(replacement);
                if(functionNameMap.containsKey(temp)) {
                    String secondModule = functionNameMap.get(temp);
                    secondModule = secondModule.substring(8);
                    secondModule = secondModule.substring(0, secondModule.indexOf("_"));
                    err.NV_STDOUTF(
                            "WARNING: Name collision found for function \"%s\" from modules \"%s\" and \"%s\".%n",
                            temp,
                            module,
                            secondModule)
                    ;
                    err.NV_STDOUTF("Prefix calls with \"%s_\" or \"%s_\" to call their version.%n", module, secondModule);
                }
                // 0123456789
                //String newname = String.format("_nvlang_%s_%d%s_%d", module.length(), module, temp.length(), temp);
                functionNameMap.put(temp, newname);
                functionNameMap.put(module + "_" + temp, newname); // ensure namespaced version is also mapped
            }else{
                newlist.add(line);
            }
        }
        return newlist;
    }



    public void resolveFunCalls(NodeStream n) {
        while(n.getType() != NodeType.ENDOFSTREAM) {
            if(
                    n.getType() == NodeType.VARIABLE &&
                            n.next() != null &&
                            n.next().getType() == NodeType.OPENPARENS)
            {
                NodeStream fcall = new NodeStream(NodeType.FUNCTIONCALL, n.getValue().toString());

                fcall.setPrev(n.prev());
                fcall.setNext(n.next());
                n.remove();
                n = fcall;
                n.prev().setNext(fcall);
                n.next().setPrev(fcall);
            }else {
                n = n.next();
            }
        }
    }

    public void preprocess(NodeStream n) {
        boolean comment = false;
        while(n.getType() != NodeType.ENDOFSTREAM) {
            if(!comment) {
                if(n.getType() == NodeType.DIV && n.next().getType() == NodeType.MUL) {
                    comment = true;
                    n = n.next().next();
                    n.prev().prev().remove();
                    n.prev().remove();
                }else if(n.getType() == NodeType.BOOL_T || n.getType() == NodeType.BOOL_FA) {
                    NodeStream bool = new NodeStream(NodeType.INT32, n.getType() == NodeType.BOOL_T ? 1 : 0);
                    bool.setPrev(n.prev());
                    bool.setNext(n.next());
                    n.remove();
                    n = bool;
                    n.prev().setNext(bool);
                    n.next().setPrev(bool);
                }else{
                    n = n.next();
                }
            }else{
                if(n.getType() == NodeType.MUL && n.next().getType() == NodeType.DIV) {
                    comment = false;
                    n = n.next().next();
                    n.prev().prev().remove();
                    n.prev().remove();
                }else{
                    n = n.next();
                    n.prev().remove();
                }
            }
        }
    }



    public void removeInlineComments(List<String> pgm) {
        for(int i = 0; i < pgm.size(); i++) {
            String line = pgm.get(i);
            int idx = idxOfComment(line);
            if(idx != -1) {
                pgm.set(i, line.substring(0, idx));
            }
        }
    }

    public int idxOfComment(String s) {
        boolean inString = false;
        char stringChar = '\0'; // Tracks whether we're inside ' or "

        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);

            if (inString) {
                if (c == stringChar && s.charAt(i - 1) != '\\') {
                    inString = false;
                }
            } else {
                if (c == '"' || c == '\'') {
                    inString = true;
                    stringChar = c;
                } else if (c == '/' && s.charAt(i + 1) == '/') {
                    return i;
                }
            }
        }

        return -1; // No comment found
    }

    /*

    Resolve function args such that no cross-file
    conflicts occur such as

    # f1.nv
    let a = 12;
    let b = 13;

    # f2.nv
    let a = 5;

    func add(int32 x) -> int32 {
        return a + x;
    }

    This should resolve to

    # f1.nv
    let f1_a = 12;
    let f1_b = 13;

    # f2.nv
    let f2_a = 5;

    func add(int32 f2_x) -> int32 {
        return f2_a + f2_x;
    }

     */
}

