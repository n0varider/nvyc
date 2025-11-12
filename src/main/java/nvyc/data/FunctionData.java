package nvyc.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FunctionData {

    private static Map<String, NodeType> returnMap = new HashMap<>();
    private static Map<String, List<NodeType>> functionParameters = new HashMap<>();
    private static Map<String, List<String>> functionParamNames = new HashMap<>();
    private static Map<String, String> llvmReturnMap = new HashMap<>();
    private static FunctionData instance;

    public static FunctionData getInstance() {
        if(instance == null) {
            instance = new FunctionData();
            returnMap = new HashMap<>();
            functionParameters = new HashMap<>();
        }
        return instance;
    }

    public Map<String, String> getLlvmReturnMap() {
        return llvmReturnMap;
    }

    public void setLlvmReturnType(String name, String type) {
        llvmReturnMap.put(name, type);
    }

    public String getLlvmReturnType(String name) {
        return llvmReturnMap.get(name);
    }

    public boolean hasLlvmType(String name) {
        return llvmReturnMap.containsKey(name);
    }

    public Map<String, NodeType> getReturnMap() {
        return returnMap;
    }

    public Map<String, List<NodeType>> getFunctionParameters() {
        return functionParameters;
    }

    // returnMap

    public NodeType getReturnType(String function) {
        return returnMap.get(function);
    }

    public void setReturnType(String function, NodeType type) {
        returnMap.put(function, type);
    }

    // functionParameters

    public List<NodeType> getParameters(String function) {
        return functionParameters.get(function);
    }

    public NodeType getParameter(String function, int idx) {
        return functionParameters.get(function).get(idx);
    }

    public void addParameter(String function, NodeType type) {
        createIfEmpty(function);
        functionParameters.get(function).add(type);
    }

    private void create(String function) {
        functionParameters.put(function, new ArrayList<>());
    }

    private void createIfEmpty(String function) {
        if(!functionParameters.containsKey(function))
            create(function);
    }

    // param names

    private void createNamesIfEmpty(String function) {
        if(!functionParamNames.containsKey(function)) {
            functionParamNames.put(function, new ArrayList<>());
        }
    }

    public void addNamedParam(String function, String name) {
        createNamesIfEmpty(function);
        functionParamNames.get(function).add(name);
    }

    public boolean isNamedParam(String function, String name) {
        if(functionParamNames.get(function) == null) return false;
        return functionParamNames.get(function).contains(name);
    }
}
