package nvyc.data;

import java.util.List;
import java.util.Set;

public class Symbols {

    // Sets to use for type checks

    public static final Set<NodeType> LITERAL_SYMBOLS = Set.of(
            NodeType.INT32, NodeType.INT64, NodeType.FP32,
            NodeType.FP64, NodeType.STR, NodeType.CHAR,
            NodeType.SHORT
    );

    public static final Set<NodeType> UNARY_SYMBOLS = Set.of(
            NodeType.SWITCHSIGN, NodeType.PTRDEREF, NodeType.FINDADDRESS
    );

    public static final Set<NodeType> START_SYMBOLS = Set.of(
            NodeType.VARDEF, NodeType.FUNCTION, NodeType.IF,
            NodeType.ELSE, NodeType.FORLOOP, NodeType.WHILELOOP,
            NodeType.NATIVE, NodeType.PUBLIC, NodeType.PRIVATE,
            NodeType.FINAL, NodeType.CONSTANT, NodeType.STRUCT
    );

    public static final Set<NodeType> TYPE_SYMBOLS = Set.of(
            NodeType.INT32_T, NodeType.INT64_T, NodeType.FP32_T,
            NodeType.FP64_T, NodeType.STRING_T, NodeType.CHAR_T,
            NodeType.VOID_T, NodeType.TYPE_T, NodeType.BOOL_T,
            NodeType.FUNCTION_T
    );

    public static final Set<NodeType> MEMORY_SYMBOLS = Set.of(
            NodeType.FINDADDRESS, NodeType.PTRDEREF, NodeType.VARIABLE
    );

    public static final Set<NodeType> ARITH_SYMBOLS = Set.of(
            NodeType.ADD, NodeType.SUB, NodeType.MUL,
            NodeType.DIV, NodeType.MODULO
    );

    public static final Set<NodeType> BITWISE_SYMBOLS = Set.of(
            NodeType.BITAND, NodeType.BITOR, NodeType.BITXOR,
            NodeType.ARITHLEFTSHIFT, NodeType.ARITHRIGHTSHIFT, NodeType.LOGICRIGHTSHIFT
    );

    public static final Set<NodeType> LOGIC_SYMBOLS = Set.of(
            NodeType.LOGICAND, NodeType.LOGICOR, NodeType.LOGICXOR,
            NodeType.LT, NodeType.LTE, NodeType.GT,
            NodeType.GTE, NodeType.EQ, NodeType.NEQ,
            NodeType.NOT
    );

    public static final Set<NodeType> DELIMIT_SYMBOLS = Set.of(
            NodeType.COMMADELIMIT, NodeType.ENDOFLINE, NodeType.OPENPARENS,
            NodeType.CLOSEPARENS, NodeType.OPENBRACE, NodeType.CLOSEBRACE,
            NodeType.OPENBRKT, NodeType.CLOSEBRKT, NodeType.ASSIGN
    );

    public static final Set<NodeType> POINTER_SYMBOLS = Set.of(
            NodeType.INT32_STAR, NodeType.INT64_STAR, NodeType.CHAR_STAR,
            NodeType.STR_STAR, NodeType.VOID_STAR, NodeType.UNIFIED_STAR,
            NodeType.FP32_STAR, NodeType.FP64_STAR, NodeType.BOOL_STAR,
            NodeType.FUNCTION_STAR, NodeType.TYPE_STAR, NodeType.STAR
    );

    public static final Set<NodeType> MEMORY_CANDIDATE_SYMBOLS = Set.of(
            NodeType.MUL, NodeType.BITAND
    );

    public static boolean isLiteral(NodeType type) {
        return LITERAL_SYMBOLS.contains(type);
    }

    public static boolean isArithmetic(NodeType type) {
        return ARITH_SYMBOLS.contains(type);
    }

    public static boolean isLogical(NodeType type) {
        return LOGIC_SYMBOLS.contains(type);
    }

    public static boolean isOperator(NodeType type) {
        return isArithmetic(type) || isLogical(type) || BITWISE_SYMBOLS.contains(type);
    }

    public static boolean isExpression(NodeStream stream) {
        return stream.contains(LITERAL_SYMBOLS)
                || stream.contains(MEMORY_SYMBOLS)
                || stream.contains(ARITH_SYMBOLS)
                || stream.contains(LOGIC_SYMBOLS)
                || stream.contains(BITWISE_SYMBOLS)
                || stream.contains(POINTER_SYMBOLS)
                || stream.contains(NodeType.ARRAY_TYPE)
                || stream.contains(NodeType.ARRAY_ACCESS);
    }

    public static int operatorPrecedence(NodeType op) {
        return switch (op) {
            case LOGICOR -> 3;
            case LOGICAND -> 4;
            case BITOR -> 5;
            case BITXOR -> 6;
            case BITAND -> 7;
            case EQ, NEQ -> 8;
            case LT, LTE, GT, GTE -> 9;
            case ARITHLEFTSHIFT, ARITHRIGHTSHIFT, LOGICRIGHTSHIFT -> 10;
            case ADD, SUB -> 11;
            case MUL, DIV, MODULO -> 12;
            case BITNEGATE, NOT -> 13;
            case ATTRIB -> // struct.member
                    14;
            default -> 0;
        };
    }

    public static NodeType normalize(NodeType t) {
        return switch (t) {
            case FP64_T -> NodeType.FP64;
            case FP32_T -> NodeType.FP32;
            case INT64_T -> NodeType.INT64;
            case INT32_T -> NodeType.INT32;
            default -> t;
        };
    }

    public static String nativeTypeToLLVM(NodeType t) {
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

    public static boolean isType(NodeType t) {
        return TYPE_SYMBOLS.contains(t) || t == NodeType.VARIABLE || POINTER_SYMBOLS.contains(t);
    }

    public static boolean isBuiltinType(NodeType t) {
        return TYPE_SYMBOLS.contains(t) || POINTER_SYMBOLS.contains(t);
    }

    public static boolean isFloatingPoint(NodeType t) {
        t = normalize(t);
        return t == NodeType.FP32 || t == NodeType.FP64;
    }

    public static boolean isInteger(NodeType t) {
        t = normalize(t);
        return t== NodeType.INT32 || t == NodeType.INT64;
    }

    public static boolean isStarterSymbol(NodeType t) {
        return START_SYMBOLS.contains(t) || t == NodeType.ENDOFLINE;
    }

    public static boolean isPointer(NodeType t) {
        return POINTER_SYMBOLS.contains(t) || t == NodeType.STRUCT_STAR;
    }

    public static boolean isMemorySymbolCandidate(NodeType t) {
        return MEMORY_CANDIDATE_SYMBOLS.contains(t);
    }

    public static boolean isPrefixOperator(NodeType t) {
        return Set.of(NodeType.MUL, NodeType.BITAND, NodeType.SUB).contains(t);
    }

    public static boolean isBinaryOperator(NodeType t) {
        return ARITH_SYMBOLS.contains(t) || LOGIC_SYMBOLS.contains(t) || BITWISE_SYMBOLS.contains(t);
    }

    public static NodeType mapUnaryOperator(NodeType t) {
        return switch (t) {
            case MUL -> NodeType.PTRDEREF;
            case BITAND -> NodeType.FINDADDRESS;
            case SUB -> NodeType.SWITCHSIGN;
            case BITNEGATE -> NodeType.BITNEGATE;
            default -> NodeType.INVALID;
        };
    }

    public static boolean isUnaryOperator(NodeType t) {
        return UNARY_SYMBOLS.contains(t);
    }

    public static int sizeof(NodeType type) {
        return switch (type) {
            case INT32 -> 4;
            case FP32 -> 4;
            case INT64 -> 8;
            case FP64 -> 8;

            default -> -1;
            // special cases, are here just for display but are handled via the caller
            // case STR -> -1;
            // case STRUCT -> -1;
        };
    }
}
