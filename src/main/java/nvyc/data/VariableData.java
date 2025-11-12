package nvyc.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VariableData {

    private static Map<String, Integer> varMap;
    private static List<String> usedRegisters = new ArrayList<>();
    private static Map<String, NodeType> arrayTypeMap;
    private static Map<String, TypeDescriptor> arrayMap;
    private static Map<String, NodeType> typeMap;
    private static Map<String, String> llvmMap;
    private static List<String> functionVariables;
    private static List<String> allocationList;
    private static final int STRUCT_VARIABLES = 0;
    private static final int STRUCT_TYPES = 1;

    private static Map<String, List<Map<String, Object>>> structMap;

    private static ScopeData scopeData = ScopeData.getInstance();

    private static VariableData instance;

    public static VariableData getInstance() {
        if(instance == null) {
            instance = new VariableData();
            varMap = new HashMap<>();
            typeMap = new HashMap<>();
            llvmMap = new HashMap<>();
            structMap = new HashMap<>();
            functionVariables = new ArrayList<>();
            allocationList = new ArrayList<>();
            usedRegisters = new ArrayList<>();
            arrayTypeMap = new HashMap<>();
            arrayMap = new HashMap<>();
        }
        return instance;
    }

    public void initializeNativeType(String variable, NodeType type) {
        typeMap.put(variable, type);
        llvmMap.put(variable, Symbols.nativeTypeToLLVM(type));
    }

    public void initializeStructType(String variable, String struct) {
        typeMap.put(variable, NodeType.STRUCT);
        llvmMap.put(variable, struct);
    }

    public void allocateRegister(String name) {
        usedRegisters.add(name);
    }

    public void clearRegisters() {
        usedRegisters.clear();
    }

    public boolean isAllocatedRegister(String name) {
        return usedRegisters.contains(name);
    }

    /*public void setArrayType(String variable, NodeType type) {
        arrayTypeMap.put(variable, type);
    }

    public NodeType getArrayType(String variable) {
        return arrayTypeMap.getOrDefault(variable, NodeType.INVALID);
    }*/

    public Map<String, TypeDescriptor> ad() {
        return arrayMap;
    }

    public void createArray(String variable, int size, NodeType type) {
        TypeDescriptor descriptor = new TypeDescriptor(size, type);
        arrayMap.put(variable, descriptor);
    }

    public void setArrayType(String variable, NodeType type) {
        arrayMap.get(variable).get()[1] = type;
    }

    public NodeType getArrayType(String variable) {
        return (NodeType) arrayMap.get(variable).get()[1];
    }

    public int getArraySize(String variable) {
        return (int) arrayMap.get(variable).get()[0];
    }

    public void setArraySize(String variable, int size) {
        arrayMap.get(variable).get()[0] = size;
    }

    public void clearArrayTypes() {
        arrayTypeMap.clear();;
    }

    private void createStruct(String struct) {
        if(!structMap.containsKey(struct)) {
            structMap.put(struct, new ArrayList<>());
            structMap.get(struct).add(new HashMap<>());
            structMap.get(struct).add(new HashMap<>());
        }
    }

    public void addStructMember(String struct, String member, int pos, NodeType type) {
        if(!structMap.containsKey(struct)) createStruct(struct);
        structMap.get(struct).get(STRUCT_VARIABLES).put(member, pos);
        structMap.get(struct).get(STRUCT_TYPES).put(member, type);
    }


    public int getStructMemberIndex(String struct, String member) {
        if(!structMap.containsKey(struct)) return -1;
        return (int) structMap.get(struct).get(STRUCT_VARIABLES).get(member);
    }

    public NodeType getStructMemberType(String struct, String member) {
        if(!structMap.containsKey(struct)) return NodeType.INVALID;
        return (NodeType) structMap.get(struct).get(STRUCT_TYPES).get(member);
    }

    // Expensive
    public String getStructMemberFromPos(String struct, int pos) {
        if(!structMap.containsKey(struct)) return "NO STRUCT FOUND";
        Map<String, Object> map = structMap.get(struct).get(STRUCT_VARIABLES);
        for(String s : map.keySet()) {
            if((int) map.get(s) == pos) {
                return s;
            }
        }
        return "INVALID POSITION";
    }

    public void clearStructs() {
        structMap.clear();
    }

    public void addAllocation(String name) {
        allocationList.add(name);
    }

    public void removeAllocation(String name) {
        allocationList.remove(name);
    }

    public void clearAllocations() {
        allocationList.clear();;
    }

    public boolean isAllocated(String name) {
        return allocationList.contains(name);
    }

    public List<String> getFunctionVariables() {
        return functionVariables;
    }

    public Map<String, String> getLlvmMap() {
        return llvmMap;
    }

    public Map<String, Integer> getVarMap() {
        return varMap;
    }

    public Map<String, NodeType> getTypeMap() {
        return typeMap;
    }

    public void emptyFunctionVariables() {
        functionVariables.clear();
    }

    public void emptyVarMap() {
        varMap.clear();
    }

    public void emptyLlvmMap() {
        llvmMap.clear();
    }

    public void emptyTypeMap() {
        typeMap.clear();
    }

    public void removeLocals() {
        for(String s : scopeData.getAll().keySet()) {
            if(scopeData.isLocal(s)) {
                s = "%" + s;
                varMap.remove(s);
                llvmMap.remove(s);
                typeMap.remove(s);
            }
        }
    }

    public boolean isFunctionVariable(String var) {
        return functionVariables.contains(var);
    }

    public void addFunctionVariable(String var) {
        functionVariables.add(var);
    }

    public int getLoadedIndex(String var) {
        return varMap.get(var);
    }

    public NodeType getType(String var) {
        return typeMap.get(var);
    }

    public void loadTo(String var, int idx) {
        varMap.put(var, idx);
    }

    public void setType(String var, NodeType type) {
        typeMap.put(var, type);
    }

    public void unload(String var) {
        varMap.remove(var);
    }

    public void removeType(String var) {
        typeMap.remove(var);
    }

    public boolean isLoaded(String var) {
        return varMap.containsKey(var);
    }

    public boolean hasType(String var) {
        return typeMap.containsKey(var);
    }

    public void setLlvmType(String var, String type) {
        llvmMap.put(var, type);
    }

    public String getLlvmType(String var) {
        return llvmMap.get(var);
    }
}
