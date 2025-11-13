package nvyc.utils;

import nvyc.data.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LLVMUtils {

    public static final int LEFT_PRECEDENCE = 0;
    public static final int RIGHT_PRECEDENCE = 1;
    public static final int EQUAL_PRECEDENCE = 2;

    public static final int STORETYPE_VARIABLE = 0;
    public static final int STORETYPE_REGISTER = 1;
    public static final int STORETYPE_LITERAL = 2;
    public static final int STORETYPE_GLOBALVAR = 3;
    public static final int STORETYPE_COPYFUNREGISTER = 4;
    public static final int STORETYPE_STRING = 5;

    public static final boolean CAST_FUN_TO_PTR = true;
    public static final boolean CAST_PTR_TO_FUN = false;

    public static final int ARITHMETIC_EXPR = 0;
    public static final int LOGICAL_EXPR = 1;

    private static int LAST_RESULT = 0;
    private static int floatTempValue = 0;

    public static final String FUNCTION_DECLARATION = "declare";
    public static final String FUNCTION_DEFINITION = "define";

    private VariableData vardata = VariableData.getInstance();
    private ScopeData scopedata = ScopeData.getInstance();
    private FunctionData fundata = FunctionData.getInstance();
    private NvyError err = new NvyError();

    private static int indexCounter = 1;
    private static int globalIndexCounter = 1;
    private static int loopDepth = 0;
    private static int conditionalDepth = 0;

    public int getLoopDepth() {
        return loopDepth;
    }

    public void resetLoopDepth() {
        loopDepth = 0;
    }

    public int getConditionalDepth() {
        return conditionalDepth;
    }

    public void increaseConditionalDepth() {
        conditionalDepth++;
    }

    public void decreaseConditionalDepth() {
        conditionalDepth--;
    }

    public int getLastResult() {
        return LAST_RESULT;
    }

    public void increaseLoopDepth() {
        loopDepth++;
    }

    public void decreaseLoopDepth() {
        loopDepth--;
    }

    public int getGlobalCounter() {
        return globalIndexCounter;
    }

    public int getAndIncrementGlobal() {
        LAST_RESULT = globalIndexCounter;
        return globalIndexCounter++;
    }

    public void resetGlobalCounter() {
        globalIndexCounter = 0;
    }

    public void incrementGlobal() {
        globalIndexCounter++;
    }

    public int getCounter() {
        return indexCounter;
    }

    public void increment() {
        LAST_RESULT = indexCounter;
        indexCounter++;
    }

    public int getAndIncrement() {
        LAST_RESULT = indexCounter;
        return indexCounter++;
    }

    public void setCounter(int i) {
        indexCounter = i;
    }

    public void resetCounter() {
        indexCounter = 0;
    }

    public static final Set<NodeType> TYPE_SYMBOLS = Set.of(
            NodeType.INT32_T, NodeType.INT64_T,
            NodeType.FP32_T, NodeType.FP64_T,
            NodeType.STRING_T, NodeType.CHAR_T,
            NodeType.VOID_T, NodeType.TYPE_T,
            NodeType.BOOL_T, NodeType.FUNCTION_T
    );
    public static final Set<NodeType> DELIMIT_SYMBOLS = Set.of(
            NodeType.COMMADELIMIT, NodeType.ENDOFLINE,
            NodeType.OPENPARENS, NodeType.CLOSEPARENS,
            NodeType.OPENBRACE, NodeType.CLOSEBRACE,
            NodeType.OPENBRKT, NodeType.CLOSEBRKT,
            NodeType.ASSIGN
    );
    public static final Set<NodeType> POINTER_SYMBOLS = Set.of(
            NodeType.INT32_STAR, NodeType.INT64_STAR,
            NodeType.CHAR_STAR, NodeType.STR_STAR,
            NodeType.VOID_STAR, NodeType.UNIFIED_STAR,
            NodeType.FP32_STAR, NodeType.FP64_STAR,
            NodeType.BOOL_STAR, NodeType.FUNCTION_STAR,
            NodeType.TYPE_STAR
    );
    public static final Set<NodeType> ARITH_SYMBOLS = Set.of(
            NodeType.ADD, NodeType.SUB,
            NodeType.MUL, NodeType.DIV
    );
    public static final Set<NodeType> BITWISE_SYMBOLS = Set.of(
            NodeType.BITAND, NodeType.BITOR,
            NodeType.BITXOR
    );
    public static final Set<NodeType> SHIFT_SYMBOLS = Set.of(
            NodeType.ARITHLEFTSHIFT,
            NodeType.ARITHRIGHTSHIFT, NodeType.LOGICRIGHTSHIFT
    );
    public static final Set<NodeType> LOGIC_SYMBOLS = Set.of(
            NodeType.LOGICAND, NodeType.LOGICOR,
            NodeType.LOGICXOR, NodeType.LT,
            NodeType.LTE, NodeType.GT,
            NodeType.GTE, NodeType.EQ,
            NodeType.NEQ, NodeType.NOT
    );
    public static final Set<NodeType> SKIPEXPR_SYMBOLS = Set.of(
            NodeType.FINDADDRESS, NodeType.STAR,
            NodeType.PTRDEREF
    );
    public static final Set<NodeType> CONDITIONAL_SYMBOLS = Set.of(
            NodeType.IF, NodeType.FORLOOP,
            NodeType.WHILELOOP
    );
    public static final Set<NodeType> LITERALVALUE_SYMBOLS = Set.of(
            NodeType.INT32, NodeType.INT64,
            NodeType.FP32, NodeType.FP64,
            NodeType.STR, NodeType.CHAR,
            NodeType.SHORT, NodeType.VOID,
            NodeType.BOOL, NodeType.FUNCTION
    );
    public static final Set<NodeType> NUMERIC_SYMBOLS = Set.of(
            NodeType.INT32, NodeType.INT64,
            NodeType.FP32, NodeType.FP64,
            NodeType.SHORT, NodeType.CHAR
    );


    /*

        Will need to resolve TYPE_T being i32
        instead of whatever its corresponding TYPE
        value is due to type codes being saved
        at runtime

     */
    public String nativeTypeToLLVM(NodeType t) {
        return switch(t) {
            case INT32, INT32_T -> "i32";
            case INT64, INT64_T -> "i64";
            case FP32, FP32_T -> "float";
            case FP64, FP64_T -> "double";
            case BOOL, BOOL_T -> "i1";
            case CHAR, CHAR_T -> "i8";
            // case BYTE -> "i8";
            case UNIFIED -> "...";
            case STR, STR_T -> "i8*";
            case VOID, VOID_T -> "void";
            case INT32_STAR -> "i32*";
            case INT64_STAR -> "i64*";
            case FP32_STAR -> "float*";
            case FP64_STAR -> "double*";
            case STR_STAR -> "i8**";
            case CHAR_STAR -> "i8*";
            case VOID_STAR -> "i8*";
            case BOOL_STAR -> "i1*";
            default -> "NULLTYPE";
        };
    }

    // Convert ptr to arith
    public NodeType arithmeticType(NodeType t) {
        return switch(t) {
            case INT32_STAR -> NodeType.INT32;
            case INT64_STAR -> NodeType.INT64;
            case FP32_STAR -> NodeType.FP32;
            case FP64_STAR -> NodeType.FP64;
            default -> NodeType.INVALID;
        };
    }

    public String loadVariable(NodeType t, String name, boolean scoped, Object aux1, Object aux2) {
        String type = nativeTypeToLLVM(t);

        if(aux2 != null && (boolean) aux2) {
            return String.format("\t%%%d = %s\n", (int) aux1, name);
        }
        return String.format("\t%%%d = load %s, %s* %s\n",
                (int) aux1,
                type,
                type,
                scoped ? "@global_" + name : "%" + name);
    }

    public boolean isLiteral(NASTNode n) {
        return Set.of(NodeType.INT32, NodeType.INT64, NodeType.FP32, NodeType.FP64, NodeType.CHAR, NodeType.SHORT).contains(n.getType());
    }

    public boolean isLiteralType(NodeType t) {
        t = normalize(t);
        return Set.of(NodeType.INT32, NodeType.INT64, NodeType.FP32, NodeType.FP64, NodeType.CHAR, NodeType.SHORT).contains(t);
    }

    public NodeType normalize(NodeType t) {
        return switch (t) {
            case FP64_T -> NodeType.FP64;
            case FP32_T -> NodeType.FP32;
            case INT64_T -> NodeType.INT64;
            case INT32_T -> NodeType.INT32;
            default -> t;
        };
    }

    public String typePrecedence(NodeType t1, NodeType t2) {
        t1 = normalize(t1);
        t2 = normalize(t2);

        if((t1 == NodeType.FP32 && t2 == NodeType.INT64) || (t1 == NodeType.INT64 && t2 == NodeType.FP32)) {
            return "float";
        }

        if (t1 == NodeType.FP64 || t2 == NodeType.FP64) {
            return "double";
        } else if (t1 == NodeType.FP32 || t2 == NodeType.FP32) {
            return "float";
        } else if (t1 == NodeType.INT64 || t2 == NodeType.INT64) {
            return "i64";
        } else {
            return "i32";
        }
    }

    public NodeType nvTypePrecedence(NodeType t1, NodeType t2) {
        t1 = normalize(t1);
        t2 = normalize(t2);

        if((t1 == NodeType.FP32 && t2 == NodeType.INT64) || (t1 == NodeType.INT64 && t2 == NodeType.FP32)) {
            return NodeType.FP64;
        }

        if (t1 == NodeType.FP64 || t2 == NodeType.FP64) {
            return NodeType.FP64;
        } else if (t1 == NodeType.FP32 || t2 == NodeType.FP32) {
            return NodeType.FP32;
        } else if (t1 == NodeType.INT64 || t2 == NodeType.INT64) {
            return NodeType.INT64;
        } else {
            return NodeType.INT32;
        }
    }

    public NodeType llvmToNative(String s) {
        return switch(s) {
            case "i32" -> NodeType.INT32;
            case "i64" -> NodeType.INT64;
            case "float" -> NodeType.FP32;
            case "double" -> NodeType.FP64;
            case "i1" -> NodeType.BOOL;
            case "i8*" -> NodeType.VOID_STAR;
            default -> NodeType.INVALID;
        };
    }

    public boolean shouldPromote(NodeType t) {
        return Set.of(NodeType.INT32, NodeType.FP32, NodeType.INT64).contains(t);
    }

    public int lrPrecedence(NodeType t1, NodeType t2) {
        t1 = normalize(t1);
        t2 = normalize(t2);

        if(t1 == t2) return EQUAL_PRECEDENCE;

        NodeType[] precedence = {NodeType.FP64, NodeType.FP32, NodeType.INT64, NodeType.INT32};
        for(NodeType type : precedence) {
            if(t1 == type) return LEFT_PRECEDENCE;
            if(t2 == type) return RIGHT_PRECEDENCE;
        }
        return EQUAL_PRECEDENCE;
    }

    public boolean isType(NodeType t) {
        return Set.of(NodeType.INT32_T, NodeType.INT64_T, NodeType.FP32_T, NodeType.FP64_T, NodeType.CHAR_T, NodeType.STRING_T, NodeType.VOID_T, NodeType.TYPE_T).contains(t);
    }

    public boolean isArith(NodeType t) {
        return Set.of(
                        NodeType.ADD, NodeType.SUB, NodeType.MUL, NodeType.DIV,
                        NodeType.BITOR, NodeType.BITAND, NodeType.BITXOR,
                        NodeType.ARITHLEFTSHIFT, NodeType.ARITHRIGHTSHIFT, NodeType.LOGICRIGHTSHIFT,
                        NodeType.MODULO)
                .contains(t);
    }

    public boolean isLogical(NodeType t) {
        return Set.of(
                        NodeType.LOGICAND, NodeType.LOGICOR,
                        NodeType.EQ, NodeType.NEQ,
                        NodeType.GTE, NodeType.GT,
                        NodeType.LTE, NodeType.LT)
                .contains(t);
    }

    public String llvmString(String s) {
        s = s.substring(0, s.length() - 1);
        s = s.replace("\\n", "\\0A");
        s = s + "\\00";
        s = s + "\"";
        return s;
    }

    public NodeType arithmeticPrecedence(NASTNode n, Map<String, NodeType> vmap, Map<String, NodeType> rmap) {
        if(n.getAllSubnodes().isEmpty()) {
            if(isLiteral(n) || isLiteralType(n.getType())) return n.getType();
            if(n.getType() == NodeType.FUNCTIONCALL) return rmap.get(n.getValue().toString());
            return vmap.get("%" + n.getValue().toString());
        }
        if(n.getType() == NodeType.VARIABLE && hasMembers(n)) {
            String variable = "%" + n.getValueString();
            String structType = vardata.getLlvmType(variable);
            String member = n.getSubnode(0).getValueString();
            return vardata.getStructMemberType(structType, member);
        }
        NodeType precedence = NodeType.INT32;
        for(NASTNode subnode : n.getAllSubnodes()) {
            precedence = nvTypePrecedence(precedence, arithmeticPrecedence(subnode, vmap, rmap));
        }
        return precedence;
    }

    public int llvmStringlen(String s) {
        int baseLength = s.length() - 2 + 1; // -2 for begin/end quote, +1 for null
        if(s.contains("\\n")) {
            baseLength--;
        }
        return baseLength;
    }

    public NodeType highestPrecedence(Map<String, NodeType> vardata, NASTNode n) {
        List<NodeType> types = hpHelper(vardata, n);
        int max = 0;
        int idx = 0;
        for(int i = 0; i < types.size(); i++) {
            int p = integerPrecedence(types.get(i));
            if(p > max) {
                max = p;
                idx = i;
            }
        }
        return types.get(idx);
    }

    public int integerPrecedence(NodeType t) {
        return switch(t) {
            case INT32, INT32_T -> 1;
            case INT64, INT64_T -> 2;
            case FP32, FP32_T -> 3;
            case FP64, FP64_T -> 4;
            default -> 0;
        };
    }

    public List<NodeType> hpHelper(Map<String, NodeType> vardata, NASTNode n) {
        List<NodeType> variables = new ArrayList<>();
        if(isArith(n.getType())) {
            variables.addAll(hpHelper(vardata, n.getSubnode(0)));
            variables.addAll(hpHelper(vardata, n.getSubnode(1)));
        }else if(isLiteral(n)) {
            variables.add(n.getType());
        }else if(n.getType() == NodeType.FUNCTIONCALL) {
            variables.add(NodeType.INT32);
        }else{
            variables.add(vardata.get(n.getValue().toString()));
        }
        return variables;
    }

    public boolean variableExists(String v, List<String> env) {
        return env.contains(v);
    }

    // -----------------------------------------
    // |               POINTERS                |
    // -----------------------------------------


    // TODO this only really works for variables and not temporaries
    public String dereferenceVariable(String ptr) {

        StringBuilder builder = new StringBuilder();
        String type = vardata.getLlvmType(ptr);

        // "\t%d = load type, type* %ptr
        builder
                .append("\t%")
                .append(getAndIncrement())
                .append(" = load ")
                .append(type)
                .append(", ")
                .append(type)
                .append("* ")
                .append(ptr)
                .append("\n");

        // Set the type of the register and load the pointer to the register
        initializeType("%" + getLastResult(), llvmToNative(type));

        vardata.setLlvmType("%" + getLastResult(), type);
        vardata.loadTo(ptr, getLastResult());

        return builder.toString();
    }

    public String dereferencePointer(String ptr) {
        StringBuilder builder = new StringBuilder();
        String type = vardata.getLlvmType(ptr);


        long starcount = type.chars().filter(c -> c == '*').count();
        if(starcount == 0) {
            err.NV_STDERRF("nvc > Internal error: Attempted to dereference %s but it is not a pointer (%s)\n", ptr, type);
            System.exit(1);
        }

        type = type.substring(0, type.length() - 1);

        // "\t%d = load type, type* %ptr
        builder
                .append("\t%")
                .append(getAndIncrement())
                .append(" = load ")
                .append(type)
                .append(", ")
                .append(type)
                .append("* ")
                .append(ptr)
                .append("\n");

        // Set the type of the register and load the pointer to the register
        initializeType("%" + getLastResult(), llvmToNative(type));

        vardata.setLlvmType("%" + getLastResult(), type);
        vardata.loadTo(ptr, getLastResult());

        return builder.toString();
    }

    public String extractMember(String variable, String struct, int pos, String aux) {
        StringBuilder builder = new StringBuilder();


        // %reg = extractvalue %struct %variable, pos
        builder
                .append("\t%")
                .append(getAndIncrement())
                .append(" = extractvalue ")
                .append(struct)
                .append(" ")
                .append(variable)
                .append(", ")
                .append(pos)
                .append("\t; ")
                .append("type = ")
                .append(aux)
                .append("\n");

        String member = vardata.getStructMemberFromPos(struct, pos);
        NodeType type = vardata.getStructMemberType(struct, member);

        vardata.setType("%" + getLastResult(), type);
        vardata.setLlvmType("%" + getLastResult(), Symbols.nativeTypeToLLVM(normalize(type)));

        return builder.toString();
    }

    public List<String> accessMemberNode(String variable, String struct, String memberName, int pos, NodeType type) {
        StringBuilder builder = new StringBuilder();

        // assuming type long { i32, i32 } for var x with names { a, b }
        // %x_long_a1_ptr = getelementptr %long, %long* %p, i32 0, i32 1

        builder
                .append(variable)
                .append("_")
                .append(struct.substring(1))
                .append("_")
                .append(memberName)
                .append(pos)
                .append("_ptr");
        String name = builder.toString();
        builder.setLength(0);

        builder
                .append("\t")
                .append(name)
                .append(" = getelementptr ")
                .append(struct)
                .append(", ")
                .append(struct)
                .append("* ")
                .append(variable)
                .append(", i32 0, i32 ")
                .append(pos)
                .append("\n");

        List<String> list = new ArrayList<>();
        list.add(name);                 // 0 -> name
        list.add(builder.toString());   // 1 -> llvm ir

        vardata.setType(name, type);
        vardata.setLlvmType(name, nativeTypeToLLVM(type));
        // TODO set type of new value

        return list;
    }

    public boolean isPointer(String variable) {
        return vardata.getLlvmType(variable).contains("*");
    }

    public String createStruct(NASTNode structNode) {
        /*
        TNODE(STRUCT, name)
        -- TNODE(INT32_T, a)
        -- TNODE(INT32_T, b)
        -- TNODE(INT32_T, c)
         */

        StringBuilder builder = new StringBuilder();
        String name = structNode.getValueString();
        int members = structNode.getAllSubnodes().size();


        builder
                .append("%")
                .append(name)
                .append(" = type { ");

        for(int i = 0; i < members; i++) {
            if(i > 0) builder.append(", ");
            builder.append(nativeTypeToLLVM(structNode.getSubnode(i).getType()));
        }

        builder
                .append(" }\n");

        return builder.toString();
    }




    // -----------------------------------------
    // |               RETURNS                 |
    // -----------------------------------------

    // Return a register
    public String returnValue(int rv) {
        NodeType type = vardata.getType(String.valueOf(rv));
        return returnValue(rv, type);
    }

    // Return a variable
    public String returnValue(String rv) {
        NodeType type = vardata.getType(rv);
        return returnValue(rv, type);
    }

    public String returnValue(int rv, NodeType type) {
        return returnValue(String.valueOf(rv), type);
    }

    public String returnValue(String rv, NodeType type) {
        StringBuilder builder = new StringBuilder();
        String llvmType = nativeTypeToLLVM(type);

        // If the variable is already loaded, get the index
        if(vardata.isLoaded(rv)) {
            rv = "%" + vardata.getLoadedIndex(rv);
            llvmType = vardata.getLlvmType(rv); // Skip '%'
        }

        // ret llvmType rv
        builder
                .append("\tret ")
                .append(llvmType)
                .append(" ")
                .append(rv)
                .append("\n");

        return builder.toString();
    }

    public String returnValue(String rv, String llvmType) {
        StringBuilder builder = new StringBuilder();

        builder
                .append("\tret ")
                .append(llvmType)
                .append(" ")
                .append(rv)
                .append("\n");

        return builder.toString();
    }

    public List<String> returnFromVariable(String variable) {
        List<String> result = new ArrayList<>();
        NodeType type = vardata.getType(variable);

        // Load variable first if its a pointer
        if(isPointer(variable)) result.add(dereferencePointer(variable));

        result.add(returnValue(getCounter(), type));

        return result;
    }


    // -----------------------------------------
    // |               VARIABLES               |
    // -----------------------------------------

    public String storeToVariable(String variable, int value, int aux) {
        return storeToVariable(variable, String.valueOf(value), aux);
    }

    public String getAddressInRegister(String variable) {
        StringBuilder builder = new StringBuilder();
        String type = vardata.getLlvmType(variable);

        // %0 = ptrtoint type* %variable to i64
        builder
                .append("\t%")
                .append(getAndIncrement())
                .append(" = ptrtoint ")
                .append(type)
                .append("* ")
                .append(variable)
                .append(" to i64\n");

        return builder.toString();
    }

    public String movePointerToVariable(String variable, int register) {
        StringBuilder builder = new StringBuilder();
        String type = vardata.getLlvmType(variable);

        // %variable = inttoptr i64 %register to type*
        builder
                .append("\t")
                .append(variable)
                .append(" = inttoptr i64 ")
                .append("%")
                .append(register)
                .append(" to ")
                .append(type)
                .append("\n");

        return builder.toString();
    }

    public String storeToPointer(String variable, String value, int aux) {
        StringBuilder builder = new StringBuilder();
        String type = vardata.getLlvmType(variable);

        if(aux == STORETYPE_REGISTER) value = "%" + value;
        if(aux == STORETYPE_STRING) value = value;
        if(aux == STORETYPE_LITERAL) value = value;
        if(aux == STORETYPE_VARIABLE) value = "%" + value; // TODO load "value" variable, store into "variable" variable
        if(aux == STORETYPE_GLOBALVAR) value = value;
        if(aux == STORETYPE_COPYFUNREGISTER) value = value;

        // TODO sign extension may be necessary for var to var storing

        // store type value, type* value


        builder
                .append("\tstore ")
                .append(type, 0, type.length()-1)
                .append(" ")
                .append(value)
                .append(", ")
                .append(type)
                .append(" ")
                .append(variable)
                .append("\n");

        if(aux == STORETYPE_REGISTER) vardata.loadTo(variable, Integer.parseInt(value.replace("%", "")));
        return builder.toString();

    }


    public String storeToVariable(String variable, String value, int aux) {
        StringBuilder builder = new StringBuilder();
        String type = vardata.getLlvmType(variable);

        if(aux == STORETYPE_REGISTER) value = "%" + value;
        if(aux == STORETYPE_STRING) value = value;
        if(aux == STORETYPE_LITERAL) value = value;
        if(aux == STORETYPE_VARIABLE) value = "%" + value; // TODO load "value" variable, store into "variable" variable
        if(aux == STORETYPE_GLOBALVAR) value = value;
        if(aux == STORETYPE_COPYFUNREGISTER) value = value;

        // TODO sign extension may be necessary for var to var storing

        // store type value, type* value


        builder
                .append("\tstore ")
                .append(type)
                .append(" ")
                .append(value)
                .append(", ")
                .append(type)
                .append("* ")
                .append(variable)
                .append("\n");

        if(aux == STORETYPE_REGISTER) vardata.loadTo(variable, Integer.parseInt(value.replace("%", "")));
        return builder.toString();

    }

    public String allocateSpace(String var, NodeType type) {
        StringBuilder builder = new StringBuilder();
        String llvmType = nativeTypeToLLVM(type);

        // %var = alloca type
        builder
                .append("\t")
                .append(var)
                .append(" = alloca ")
                .append(llvmType)
                .append("\n");

        return builder.toString();
    }

    public String allocateArray(String var, NASTNode array) {
        StringBuilder builder = new StringBuilder();

        int size = (int) array.getSubnode(0).getValue();
        NodeType type = (NodeType) array.getValue();
        String llvmType = nativeTypeToLLVM(type);

        // %var = alloca [size x llvmType]
        builder
                .append("\t")
                .append(var)
                .append(" = alloca [")
                .append(size)
                .append(" x ")
                .append(llvmType)
                .append("]")
                .append("\n");

        return builder.toString();
    }

    public String getArrayPtr(String var, String type, int size, String idx) {
        StringBuilder builder = new StringBuilder();

        Object tmp = null;
        if(!isNumeric(idx)) {
            tmp = idx;
        }else{
            tmp = Integer.parseInt(idx);
        }

        builder
                .append("[")
                .append(size)
                .append(" x ")
                .append(type)
                .append("]");

        String info = builder.toString();
        builder.setLength(0);

        // %i = getelementptr [size x type], [size x type]* %var, i32 0, i32 idx
        builder
                .append("\t%")
                .append(getAndIncrement())
                .append(" = getelementptr ")
                .append(info)
                .append(", ")
                .append(info)
                .append("* ")
                .append(var)
                .append(", i32 0, i32 ")
                .append(tmp)
                .append("\n");

        vardata.setLlvmType("%" + getLastResult(), type);
        return builder.toString();
    }

    public String loadFromArrayPtr(String ptr, String arrayVariable, String type) {
        StringBuilder builder = new StringBuilder();
        // %i = load type, type* %ptr
        builder
                .append("\t%")
                .append(getAndIncrement())
                .append(" = load ")
                .append(type)
                .append(", ")
                .append(type)
                .append("* ")
                .append(ptr)
                .append("\n");

        NodeType arrayType = vardata.getArrayType(arrayVariable);
        vardata.setLlvmType("%" + getLastResult(), vardata.getLlvmType(ptr));
        //initializeType("%" + getLastResult(), arrayType);

        return builder.toString();
    }

    public String storeToArrayPtr(String ptr, String type, String value) {
        StringBuilder builder = new StringBuilder();

        // store type val, type* ptr
        builder
                .append("\tstore ")
                .append(type)
                .append(" ")
                .append(value)
                .append(", ")
                .append(type)
                .append("* ")
                .append(ptr)
                .append("\n");

        return builder.toString();
    }



    public String allocateSpace(String var, String llvmType) {
        StringBuilder builder = new StringBuilder();

        // %var = alloca type
        builder
                .append("\t")
                .append(var)
                .append(" = alloca ")
                .append(llvmType)
                .append("\n");

        return builder.toString();
    }

    // -----------------------------------------
    // |               FUNCTIONS               |
    // -----------------------------------------

    public String buildParameters(String aux) {
        StringBuilder builder = new StringBuilder();
        List<String> functionVariables = vardata.getFunctionVariables();
        int length = functionVariables.size();

        for (int i = 0; i < length; i++) {
            String variable = functionVariables.get(i);
            String llvmType = vardata.getLlvmType(variable);
            if(i > 0) builder.append(", ");
            builder
                    .append(llvmType);

            // Only add the variable name if we're defining it. Declarations don't care about names
            if(aux.equals(FUNCTION_DEFINITION)) {
                builder
                        .append(" ")
                        .append(variable);
            }
        }

        return builder.toString();
    }

    public String buildFunctionPrototype(String name, String aux) {
        StringBuilder builder = new StringBuilder();
        String parameters = buildParameters(aux);

        // @name(params)
        builder
                .append("@")
                .append(name)
                .append("(")
                .append(parameters)
                .append(")");

        return builder.toString();
    }

    public String createFunction(String name, String aux) {
        StringBuilder builder = new StringBuilder();
        NodeType returnType = fundata.getReturnType(name);
        String llvmType = nativeTypeToLLVM(returnType);
        if(fundata.hasLlvmType(name)) llvmType = fundata.getLlvmReturnType(name);
        String prototype = buildFunctionPrototype(name, aux);

        // [declare/define] llvmType @name(params)
        builder
                .append(aux)
                .append(" ")
                .append(llvmType)
                .append(" ")
                .append(prototype);

        if(aux.equals(FUNCTION_DEFINITION))
            // [declare/define] llvmType @name(params) {
            builder.append(" {\n");

        return builder.toString();
    }

    // TODO in "input.nv" it attempts to promote int32 + int64 into a double for a variadic
    public List<String> promoteVariadicValue(String value, NodeType type) {
        StringBuilder builder = new StringBuilder();

        String promotionType = "";
        switch(type) {
            case INT32, CHAR -> promotionType = "i64";
            case FP32 -> promotionType = "double";
            default -> {
                err.NV_STDERR("nvc > Internal error: Attempted to promote " + type + " but it cannot be promoted\n");
                System.exit(0);
            }
        }

        String extension = promotionExtension(type, promotionType);
        String nativeType = nativeTypeToLLVM(type);

        String promotionValue = value.startsWith("%") ? "reg" + value.substring(1) : value;
        builder
                .append("%promote_")
                .append(promotionValue)
                .append("_to_")
                .append(promotionType);
        String vpromotionValue = builder.toString();
        builder.setLength(0);

        builder
                .append("")
                .append(vpromotionValue)
                .append(" = ")
                .append(extension)
                .append(" ")
                .append(nativeType)
                .append(" ")
                .append(value)
                .append(" to ")
                .append(promotionType)
                .append("");

        initializeType("%" + vpromotionValue, llvmToNative(promotionType));

        List<String> list = new ArrayList<>();
        list.add(vpromotionValue);      // 0 = new value
        list.add(promotionType);        // 1 = promotion type
        list.add(builder.toString());   // 2 = llvm code

        return list;
    }

    public String callFunction(String name, List<String> names, List<NodeType> types, List<String> llvmTypes, boolean storeReturn, boolean isVariadic) {

        StringBuilder builder = new StringBuilder();
        NodeType type = fundata.getReturnType(name);
        String llvmType = fundata.getLlvmReturnType(name);

        // TODO temporary
        if(type == null) {
            err.NV_STDERR("Function does not exist: " + name);
            System.exit(0);
        }

        //String llvmType = nativeTypeToLLVM(type);
        if(fundata.hasLlvmType(name)) llvmType = fundata.getLlvmReturnType(name);
        List<String> promotionLines = new ArrayList<>();


        int variadicIdx = 99999; // No function should ever have 99999 params, so this will (probably) never break
        if(isVariadic) variadicIdx = fundata.getFunctionParameters().get(name).indexOf(NodeType.UNIFIED);

        for(int i = 0; i < names.size(); i++) {
            if(i > 0) builder.append(", ");

            if(i >= variadicIdx && types.get(i) != NodeType.STR && !Symbols.POINTER_SYMBOLS.contains(types.get(i))) {
                if(Symbols.isLiteral(types.get(i)) && types.get(i) != NodeType.INT64 && types.get(i) != NodeType.FP64) {
                    List<String> promotion = promoteVariadicValue(names.get(i), types.get(i));
                    promotionLines.add(promotion.get(2));

                    // type %promoted
                    builder
                            .append(promotion.get(1))
                            .append(" ")
                            .append(promotion.get(0));
                }else{
                    builder
                            .append(llvmTypes.get(i))
                            .append(" ")
                            .append(names.get(i));
                }
            }else {
                builder
                        .append(llvmTypes.get(i))
                        .append(" ")
                        .append(names.get(i));
            }
        }
        String resolvedParameters = builder.toString();

        builder.setLength(0);

        // Generate prototype to cast variadic functions
        if(isVariadic) {
            List<NodeType> prototypes = fundata.getFunctionParameters().get(name);
            for (int i = 0; i < prototypes.size(); i++) {
                if (i > 0) builder.append(", ");
                builder.append(nativeTypeToLLVM(prototypes.get(i)));
            }
        }

        String prototype = builder.toString();
        builder.setLength(0);

        // Join every parameter into a single string
        /*String resolvedParameters = parameters.entrySet()
                .stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.joining(", "));
*/
        for(String line : promotionLines) {
            builder.append("\t").append(line).append("\n");
        }
        if(storeReturn) {
            // %x =
            builder
                    .append("\t%")
                    .append(getAndIncrement())
                    .append(" = ");
        }

        // call llvmType @name(args)
        if(!storeReturn) builder.append("\t");

        builder
                .append("call ")
                .append(llvmType);

        if(isVariadic) {
            builder
                    .append(" ")
                    .append("(")
                    .append(prototype)
                    .append(")");
        }

        builder
                .append(" @")
                .append(name)
                .append("(")
                .append(resolvedParameters)
                .append(")\n");

        initializeType("%" + getLastResult(), type);
        vardata.setLlvmType("%" + getLastResult(), llvmType);
        if(fundata.hasLlvmType(name)) vardata.setLlvmType("%" + getLastResult(), fundata.getLlvmReturnType(name));
        return builder.toString();
    }

    public List<String> loadString(String value) {
        StringBuilder builder = new StringBuilder();
        List<String> result = new ArrayList<>();
        LLVMString llvmString = new LLVMString(value);
        int size = llvmString.length();

        // @.str_x = private constant [size x i8] c"value" ; value
        builder
                .append("@.str_")
                .append(getGlobalCounter())
                .append(" = private constant [")
                .append(size)
                .append(" x i8] c")
                .append(llvmString)
                .append("\t; ")
                .append(llvmString)
                .append("\n");
        result.add(builder.toString());
        builder.setLength(0);

        // %.str_x = getelementptr [size x i8], [size xi8]* @.str_x, i32 0, i32 0   ; value
        builder
                .append("\t%.str_")
                .append(getGlobalCounter())
                .append(" = getelementptr [")
                .append(size)
                .append(" x i8], [")
                .append(size)
                .append(" x i8]* @.str_")
                .append(getAndIncrementGlobal())
                .append(", i32 0, i32 0\t;")
                .append(llvmString)
                .append("\n");
        result.add(builder.toString());

        return result;
    }


    public String bitcastFunctionToPointer(String name, boolean aux) {
        StringBuilder builder = new StringBuilder();

        List<NodeType> prototypes = fundata.getFunctionParameters().get(name);
        String returnType = nativeTypeToLLVM(fundata.getReturnType(name));
        for (int i = 0; i < prototypes.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append(nativeTypeToLLVM(prototypes.get(i)));
        }

        String prototype = builder.toString();
        builder.setLength(0);

        String alpha = "";
        String beta = "";

        if(aux == CAST_PTR_TO_FUN) {
            alpha = "i8*";
            beta = returnType + " " + prototype + "*";
        }else if(aux == CAST_FUN_TO_PTR) {
            beta = "i8*";
            alpha = returnType + " " + prototype + "*";
        }

        // %name_toptr = bitcast i32 (i32, i32)* @fadd to i8*
        // %name_toptr = bitcast i8* @fadd to i32 (i32, i32)*
        builder
                .append("\t%")
                .append(name)
                .append("toptr = bitcast ")
                .append(alpha)
                .append(" @")
                .append(name)
                .append(" to ")
                .append(beta)
                .append("\n");

        return builder.toString();
    }


    // -----------------------------------------
    // |               LOOPS                   |
    // -----------------------------------------

    public String jumpDirectly(String label) {
        return "\tbr label %" + label + "\n";
    }

    public String jumpConditional(String label1, String label2, int conditionIndex) {
        StringBuilder builder = new StringBuilder();

        // br i1 %x, label %label1y, label %label2y
        builder
                .append("\tbr i1 %")
                .append(conditionIndex)
                .append(", label %")
                .append(label1)
                .append(", label %")
                .append(label2)
                .append("\n");
        return builder.toString();
    }

    public String loopcheck() {
        return "loop_condition" + loopDepth;
    }

    public String loopbody() {
        return "loop_body" + loopDepth;
    }

    public String loopexit() {
        return "loop_exit" + loopDepth;
    }


    // -----------------------------------------
    // |         ARITHMETIC & LOGIC            |
    // -----------------------------------------

    public List<String> computeGenericExpr(String lhs, String rhs, NodeType lhsType, NodeType rhsType, String operation, String comingFrom) {
        List<String> result = new ArrayList<>();
        StringBuilder builder = new StringBuilder();

        String precedence = typePrecedence(lhsType, rhsType);
        int lrPrecedence = lrPrecedence(lhsType, rhsType);
        boolean requiresPromotion = lrPrecedence != 2;

        if(requiresPromotion) {
            String promoteeValue = lrPrecedence == LEFT_PRECEDENCE ? rhs : lhs;
            NodeType promoteeType = lrPrecedence == LEFT_PRECEDENCE ? rhsType : lhsType;
            NodeType promotionType = lrPrecedence == LEFT_PRECEDENCE ? lhsType : rhsType;

            lhsType = normalize(lhsType);
            rhsType = normalize(rhsType);
            String llvmPromoteeType = nativeTypeToLLVM(promoteeType);
            String llvmPromotionType = nativeTypeToLLVM(promotionType);
            String promoteMethod = promotionExtension(promoteeType, llvmPromotionType);

            // %bcast_[arith/logic]_regx_from_y
            builder
                    .append("%bcast_")
                    .append(comingFrom)
                    .append("_")
                    .append(getCounter())
                    .append("_from_")
                    .append(promoteeValue.replace("%", ""));

            String promotedValue = builder.toString();
            builder.setLength(0);

            // %bcast_arith_x_from_y = ext promoteeType %val to promotionType
            builder
                    .append("\t")
                    .append(promotedValue)
                    .append(" = ")
                    .append(promoteMethod)
                    .append(" ")
                    .append(llvmPromoteeType)
                    .append(" ")
                    .append(promoteeValue)
                    .append(" to ")
                    .append(llvmPromotionType)
                    .append("\n");

            // Compiler gets outplayed. There's probably a better way to do this
            String UNUSED = promoteeValue.equals(lhs) ? (lhs = promotedValue) : (rhs = promotedValue);
        }

        if(lhsType == rhsType && lhsType == NodeType.BOOL) {
            precedence = "i1";
        }


        // TODO might have issues with literal values
        builder
                .append("\t%")
                .append(getAndIncrement())
                .append(" = ")
                .append(operation)
                .append(" ")
                .append(precedence)
                .append(" ")
                //.append("%")
                .append(lhs)
                //.append(", %")
                .append(", ")
                .append(rhs)
                .append("\n");

        if(comingFrom.equals("logic") && !operation.equals("and") && !operation.equals("or"))
            precedence = "i1";


        initializeType("%" + getLastResult(), llvmToNative(precedence));
        result.add(builder.toString());
        return result;
    }

    public List<String> computeArith(String lhs, String rhs, NodeType lhsType, NodeType rhsType, NodeType operation) {
        StringBuilder builder = new StringBuilder();
        List<String> result = new ArrayList<>();

        String precedence = typePrecedence(lhsType, rhsType);
        String arithop = "";

        // Floating point operations
        if(precedence.equals("float") || precedence.equals("double")) {
            switch (operation) {
                case ADD       -> arithop = "fadd";
                case SUB       -> arithop = "fsub";
                case MUL       -> arithop = "fmul";
                case DIV       -> arithop = "fdiv";
                case MODULO    -> arithop = "frem";
                default -> {
                    err.NV_STDERR("ERROR > Invalid operation for a floating point: " + operation);
                    System.exit(1);
                }
            }
        }

        // Integer operations
        else {
            switch (operation) {
                case ADD -> arithop = "add";
                case SUB -> arithop = "sub";
                case MUL -> arithop = "mul";
                case DIV -> arithop = "sdiv";
                case BITAND -> arithop = "and";
                case BITOR -> arithop = "or";
                case BITXOR -> arithop = "xor";
                case LOGICRIGHTSHIFT -> arithop = "ashr";
                case ARITHRIGHTSHIFT -> arithop = "lshr";
                case ARITHLEFTSHIFT -> arithop = "shl";
                case MODULO -> arithop = "srem";
                case EQ -> arithop = "icmp eq";
                default -> {
                    err.NV_STDERR("ERROR > Invalid operation: " + operation);
                    System.exit(1);
                }
            }
        }

        return computeGenericExpr(lhs, rhs, lhsType, rhsType, arithop, "arith");
    }

    private String promotionExtension(NodeType promoteeType, String type) {
        if(promoteeType == NodeType.INT64) return "sitofp";
        if(promoteeType == NodeType.INT32 && (type.equals("float") || type.equals("double"))) return "sitofp";
        if(promoteeType == NodeType.FP32 && type.equals("double")) return "fpext";
        if(type.equals("float")) return "fpext";
        return "sext";
    }

    public List<String> computeLogic(String lhs, String rhs, NodeType lhsType, NodeType rhsType, NodeType operation) {
        StringBuilder builder = new StringBuilder();

        String precedence = typePrecedence(lhsType, rhsType);
        String logicop = "";

        // Floating point operations
        if(precedence.equals("float") || precedence.equals("double")) {
            switch (operation) {
                case LOGICAND -> logicop = "and";
                case LOGICOR -> logicop = "or";
                case EQ -> logicop = "fcmp oeq";
                case NEQ -> logicop = "fcmp one";
                case LT -> logicop = "fcmp olt";
                case GT -> logicop = "fcmp ogt";
                case LTE -> logicop = "fcmp ole";
                case GTE -> logicop = "fcmp oge";
                default -> {
                    err.NV_STDERR("ERROR > Invalid operation for a floating point: " + operation);
                    System.exit(1);
                }
            }
        }

        // Integer operations
        else {
            switch (operation) {
                case LOGICAND -> logicop = "and";
                case LOGICOR -> logicop = "or";
                case EQ -> logicop = "icmp eq";
                case NEQ -> logicop = "icmp ne";
                case LT -> logicop = "icmp slt";
                case GT -> logicop = "icmp sgt";
                case LTE -> logicop = "icmp sle";
                case GTE -> logicop = "icmp sge";
                default -> {
                    err.NV_STDERR("ERROR > Invalid operation: " + operation);
                    System.exit(1);
                }
            }
        }

        return computeGenericExpr(lhs, rhs, lhsType, rhsType, logicop, "logic");
    }

    public String computeInequality(int operand) {
        return computeInequality(String.valueOf(operand));
    }

    public String computeInequality(String operand) {
        StringBuilder builder = new StringBuilder();
        String llvmType = vardata.getLlvmType(operand);
        String zeroValue = "";
        String compareOperator = "";
        NodeType type = vardata.getType(operand);

        if(type == NodeType.FP32 || type == NodeType.FP64) {
            zeroValue = "0.0";
            compareOperator = "fcmp one";
        }

        else {
            zeroValue = "0";
            compareOperator = "icmp ne";
        }

        // %d = cmp ne type %operand, 0
        builder
                .append("\t%")
                .append(getAndIncrement())
                .append(" = ")
                .append(compareOperator)
                .append(" ")
                .append(llvmType)
                //.append(" %")
                .append(" ")
                .append(operand)
                .append(", ")
                .append(zeroValue)
                .append("\n");

        return builder.toString();
    }



    // -----------------------------------------
    // |             CONDITIONALS              |
    // -----------------------------------------

    public String iftrue() {
        return "iftrue" + conditionalDepth;
    }

    public String iffalse() {
        return "iffalse" + conditionalDepth;
    }

    public boolean isNonZeroLiteral(NASTNode node) {
        NodeType type = node.getType();
        String value = node.getValueString();
        switch(type) {
            case INT32 -> {
                int x = Integer.parseInt(value);
                return x != 0;
            }
            case INT64 -> {
                long x = Long.parseLong(value);
                return x != 0L;
            }
            case FP32 -> {
                float x = Float.parseFloat(value);
                return x != 0.0F;
            }
            case FP64 -> {
                double x = Double.parseDouble(value);
                return x != 0.0D;
            }
            case SHORT -> {
                short x = Short.parseShort(value);
                return x != (short) 0;
            }
            case CHAR -> {
                char x = value.toCharArray()[0];
                return x != (char) 0;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean isNumericLiteral(String s) {
        try {
            Integer.parseInt(s);
            return true;
        }catch (NumberFormatException e) {
            try {
                Long.parseLong(s);
                return true;
            }catch (NumberFormatException e2) {
                try {
                    Float.parseFloat(s);
                    return true;
                }catch (NumberFormatException e3) {
                    try {
                        Double.parseDouble(s);
                        return true;
                    }catch (NumberFormatException e4) {
                        return false;
                    }
                }
            }
        }
    }

    public void initializeType(int variable, NodeType type) {
        initializeType(String.valueOf(variable), type);
    }

    public void initializeLlvmType(String variable, String llvmType) {
        vardata.setLlvmType(variable, llvmType);
    }

    public void initializeType(String variable, NodeType type) {
        vardata.setType(variable, type);
        vardata.setLlvmType(variable, nativeTypeToLLVM(type));
    }

    public boolean hasConditionals(NASTNode n) {
        boolean result = false;
        Set<NodeType> set = Set.of(NodeType.IF, NodeType.FORLOOP, NodeType.WHILELOOP);
        for(NodeType t : set) {
            if(n.contains(t)) result = true;
        }
        return result;
    }

    public String allocateGlobal(String name, NodeType type, String value, String comment) {
        StringBuilder builder = new StringBuilder();
        String llvmType = nativeTypeToLLVM(type);

        builder
                .append("@global_")
                .append(name)
                .append(" = global ")
                .append(llvmType)
                .append(" ")
                .append(value)
                .append("\t; ")
                .append(comment)
                .append("\n");

        return builder.toString();
    }


    public String bitcastIntToFloat(String reg) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("\t%inttof32_")
                .append(floatTempValue++)
                .append(" = bitcast i32 ")
                .append(reg)
                .append(" to float\n");

        return builder.toString();
    }

    public String bitcastVariableI32ToF32() {
        return "inttof32_" + floatTempValue;
    }

    public String floatToHex(float f) {
        int hex = Float.floatToIntBits(f);
        return String.format("0x%08x", hex);
    }

    public String doubleToHex(double f) {
        return "0x" + Long.toHexString(Double.doubleToLongBits(f));
    }

    public String floatToIntString(float f) {
        return String.valueOf(Float.floatToRawIntBits(f));
    }

    public long floatToLong(double f) {
        return (long) f;
    }

    public int longToInt(long l) {
        return (int) l;
    }

    public boolean hasMembers(NASTNode node) {
        return node.contains(NodeType.MEMBER);
    }

    public String getScopedVariable(String var) {
        // %x_1 -> var x in scope 1
        return "%" + var + "_" + scopedata.get(var);
    }

    public String stripScopedVariable(String var) {
        return var.substring(1, var.lastIndexOf("_"));
    }

    public boolean hasMembersS(String s) {
        return s.contains(".");
    }

    public boolean isNumeric(String s) {
        try {
            Integer.parseInt(s);
            return true;
        }catch (NumberFormatException e) {
            return false;
        }
    }
}
