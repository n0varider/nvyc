package nvyc.utils;

import nvyc.data.NASTNode;
import nvyc.data.NodeStream;
import nvyc.data.NodeType;
import nvyc.data.Symbols;

import java.util.List;
import java.util.Stack;

public class ParserUtils {

    private NvyError err = new NvyError();

    // Forward values
    public static final int FUNCTION_FORWARD_NAME = 1;
    public static final int FUNCTION_FORWARD_FIRSTARG = 2;
    public static final int FUNCTION_FORWARD_NEXTARG = 2;
    public static final int FUNCTION_FORWARD_RETURNTYPE = 3;
    public static final int FUNCTION_FORWARD_FIRSTEXPR = 2;
    public static final int STRUCT_FORWARD_NEXTARG = 3;
    public static final int VARDEF_FORWARD_EXPR = 3;

    // Function positions
    private static final int FUNCTION_ARGS = 0;
    private static final int FUNCTION_RETURN = 1;
    private static final int FUNCTION_BODY = 2;

    private static final int CONDITIONAL_COND = 0;
    private static final int CONDITIONAL_BODY = 1;
    private static final int CONDITIONAL_ELSE = 2;

    private static final int FORLOOP_DEFINITION = 0;
    private static final int FORLOOP_CONDITION = 1;
    private static final int FORLOOP_ITERATION = 2;
    private static final int FORLOOP_BODY = 3;

    //
    private static int bodyDepth = 0;
    public static int BLANK_ARRAY = -1;


    public int getForwardDepth() {
        return bodyDepth;
    }

    public void resetForwardDepth() {
        bodyDepth = 0;
    }

    // -----------------------------------------
    // |               FUNCTION                |
    // -----------------------------------------
    public NASTNode createFunction(String name) {

        NASTNode funRoot = new NASTNode(NodeType.FUNCTION, name);
        NASTNode funArgs = new NASTNode(NodeType.FUNCTIONPARAM, NodeType.VOID);
        NASTNode funReturn = new NASTNode(NodeType.FUNCTIONRETURN, NodeType.VOID);
        NASTNode funBody = new NASTNode(NodeType.FUNCTIONBODY, NodeType.VOID);

        funRoot.addNode(funArgs, NASTNode.TAIL);
        funRoot.addNode(funReturn, NASTNode.TAIL);
        funRoot.addNode(funBody, NASTNode.TAIL);

        return funRoot;
    }

    public String getFunctionName(NASTNode function) {
        return function.getValueString();
    }

    public void addFunctionBody(NASTNode function, NASTNode bodyNode) {
        addBodyNode(function, bodyNode);
    }

    public void addFunctionArg(NASTNode function, NASTNode arg) {
        function.getSubnode(FUNCTION_ARGS).addNode(arg, NASTNode.TAIL);
    }

    public List<NASTNode> getFunctionArgs(NASTNode function) {
        return function.getSubnode(FUNCTION_ARGS).getAllSubnodes();
    }

    public void setReturnType(NASTNode function, NodeType returnType) {
        NASTNode subnode = function.getSubnode(FUNCTION_RETURN);
        if(!subnode.getAllSubnodes().isEmpty()) {
            subnode.getSubnode(0).setType(returnType);
            subnode.getSubnode(0).setValue(Symbols.nativeTypeToLLVM(returnType));
        }
        else
            subnode.addNode(new NASTNode(returnType, Symbols.nativeTypeToLLVM(returnType)), NASTNode.TAIL);
    }

    public NASTNode structReturnType(String struct) {
        return new NASTNode(NodeType.STRUCT, struct);
    }

    public NodeType getReturnType(NASTNode function) {
        return function.getSubnode(FUNCTION_RETURN).getType();
    }

    public NASTNode createArgument(NodeType type, String value) {
        return new NASTNode(type, value);
    }

    public NASTNode createFunctionCall(String function) {
        return new NASTNode(NodeType.FUNCTIONCALL, function);
    }

    public void addCallArg(NASTNode functionCall, NASTNode arg) {
        functionCall.addNode(arg, NASTNode.TAIL);
    }


    // -----------------------------------------
    // |             CONDITIONALS              |
    // -----------------------------------------

    public NASTNode createConditional() {

        NASTNode conditionalHead = new NASTNode(NodeType.IF, NodeType.VOID);
        NASTNode conditionalCondition = new NASTNode(NodeType.CONDITION, NodeType.VOID);
        NASTNode conditionalBody = new NASTNode(NodeType.FUNCTIONBODY, NodeType.VOID);
        NASTNode conditionalElse = new NASTNode(NodeType.ELSE, NodeType.VOID);

        conditionalHead.addNode(conditionalCondition, NASTNode.TAIL);
        conditionalHead.addNode(conditionalBody, NASTNode.TAIL);
        conditionalHead.addNode(conditionalElse, NASTNode.TAIL);

        return conditionalHead;
    }

    public void setCondition(NASTNode conditional, NASTNode condition) {
        conditional.getSubnode(CONDITIONAL_COND).addNode(condition, NASTNode.TAIL);
    }

    public void addConditionalBody(NASTNode conditional, NASTNode bodyNode) {
        addBodyNode(conditional, bodyNode);
    }

    public void addConditionalElseBody(NASTNode conditional, NASTNode elseNode) {
        conditional.getSubnode(CONDITIONAL_ELSE).addNode(elseNode, NASTNode.TAIL);
    }

    // {{{}}} <- moves to matching outer brace
    // assumes stream is already at '{'
    public NodeStream moveToMatchingDelimiter(NodeStream stream, NodeType open, NodeType close) {
        Stack<Integer> stack = new Stack<>();
        NodeType type;

        stack.push(1);

        while(!stack.isEmpty() && stream != null) {
            type = stream.getType();

            if(type == open) stack.push(1);
            else if(type == close) stack.pop();

            stream = stream.next();
        }

        return stream;
    }


    // -----------------------------------------
    // |             BODY NODES                |
    // -----------------------------------------
    private void addBodyNode(NASTNode head, NASTNode bodyNode) {
        NodeType type = head.getType();

        switch (type) {
            case FUNCTION -> head.getSubnode(FUNCTION_BODY).addNode(bodyNode, NASTNode.TAIL);
            case IF -> head.getSubnode(CONDITIONAL_BODY).addNode(bodyNode, NASTNode.TAIL);
            case FORLOOP -> head.getSubnode(FORLOOP_BODY).addNode(bodyNode, NASTNode.TAIL);
            case VARDEF -> head.addNode(bodyNode, NASTNode.TAIL);
            case STRUCT -> head.addNode(bodyNode, NASTNode.TAIL);

            default -> {
                err.NV_STDERR("nvc > Internal error: Unknown body node header for type " + type);
                err.NV_STDERR("nvc > Node: " + head + "\n\n" + bodyNode);
                System.exit(1);
            }
        }
    }

    public int getDepth(NodeStream current, NodeType OPEN, NodeType CLOSE) {
        Stack<Integer> braces = new Stack<>();
        NodeStream cpy = current.cutheadAndReturn();
        int depth = 0;
        braces.push(1);
        cpy = cpy.next().next();
        depth += 2;
        while(!braces.isEmpty()) {
            NodeType type = cpy.getType();
            if (type == OPEN) {
                braces.push(1);
            } else if (type == CLOSE) {
                braces.pop();
            }
            if(cpy.next() != null && !braces.isEmpty()) {
                cpy = cpy.next();
                depth++;
            }
        }

        cpy = null;
        braces = null;
        return depth;
    }

    // -----------------------------------------
    // |              VARIABLES                |
    // -----------------------------------------

    public NASTNode defineVariable(String variableName) {
        return new NASTNode(NodeType.VARDEF, variableName);
    }

    public NASTNode declareVariable(String variableName) {
        return new NASTNode(NodeType.VARIABLE, variableName);
    }

    public NASTNode assign(NASTNode variable, NASTNode value) {
        NASTNode assign = new NASTNode(NodeType.ASSIGN, NodeType.VOID);

        assign.addNode(variable, NASTNode.TAIL);
        assign.addNode(value, NASTNode.TAIL);

        return assign;
    }

    public NASTNode assignVariable(String variableName, NASTNode value) {
        NASTNode assign = new NASTNode(NodeType.ASSIGN, NodeType.VOID);
        NASTNode variable = new NASTNode(NodeType.VARIABLE, variableName);

        assign.addNode(variable, NASTNode.TAIL);
        assign.addNode(value, NASTNode.TAIL);

        return assign;
    }


    public NASTNode derefAssign(String variableName, NASTNode value) {
        NASTNode assign = new NASTNode(NodeType.ASSIGN, NodeType.VOID);
        NASTNode variable = new NASTNode(NodeType.PTRDEREF, variableName);

        assign.addNode(variable, NASTNode.TAIL);
        assign.addNode(value, NASTNode.TAIL);

        return assign;
    }

    public NASTNode assignVariableWithMembers(NASTNode variable, NASTNode value) {
        NASTNode assign = new NASTNode(NodeType.ASSIGN, NodeType.VOID);
        assign.addNode(variable, NASTNode.TAIL);
        assign.addNode(value, NASTNode.TAIL);

        return assign;
    }

    // This should ALWAYS be called before setVariableValue when used
    public void castVariable(NASTNode variable, NodeType cast) {
        NASTNode castNode = new NASTNode(NodeType.CAST, cast);
        addBodyNode(variable, castNode);
    }

    public void castVariableToStruct(NASTNode variable, String structName) {
        // TNODE(CAST, STRUCT)
        // -- TNODE(STRUCT, structName)
        NASTNode cast = new NASTNode(NodeType.CAST, NodeType.STRUCT);
        cast.addNode(new NASTNode(NodeType.STRUCT, structName), NASTNode.TAIL);
        addBodyNode(variable, cast);
    }

    public void setVariableValue(NASTNode variable, NASTNode value) {
        addBodyNode(variable, value);
    }

    public NASTNode createReturn(NASTNode value) {
        NASTNode returnNode = new NASTNode(NodeType.RETURN, NodeType.VOID);
        returnNode.addNode(value, NASTNode.TAIL);
        return returnNode;
    }

    public NASTNode createStruct(String name) {
        return new NASTNode(NodeType.STRUCT, name);
    }

    public void addStructNode(NASTNode struct, NASTNode node) {
        addBodyNode(struct, node);
    }

    public NASTNode accessMember(String variable) {
        String[] accesses = variable.split("\\.");
        String variableName = accesses[0];
        NASTNode root = new NASTNode(NodeType.VARIABLE, variableName);
        NASTNode sentinel = root;

        for(int i = 1; i < accesses.length; i++) {
            root.addNode(new NASTNode(NodeType.MEMBER, accesses[i]), NASTNode.TAIL);
            root = root.getSubnode(0);
        }

        return sentinel;
    }

    // -----------------------------------------
    // |                 LOOPS                 |
    // -----------------------------------------

    public NASTNode createForLoop() {
        NASTNode loopHead = new NASTNode(NodeType.FORLOOP, NodeType.VOID);

        NASTNode definition = new NASTNode(NodeType.LOOPDEF, NodeType.VOID);
        NASTNode condition = new NASTNode(NodeType.LOOPCOND, NodeType.VOID);
        NASTNode iteration = new NASTNode(NodeType.LOOPITERATION, NodeType.VOID);
        NASTNode body = new NASTNode(NodeType.FUNCTIONBODY, NodeType.VOID);

        loopHead.addNode(definition, NASTNode.TAIL);
        loopHead.addNode(condition, NASTNode.TAIL);
        loopHead.addNode(iteration, NASTNode.TAIL);
        loopHead.addNode(body, NASTNode.TAIL);

        return loopHead;
    }

    public void setLoopDefinition(NASTNode loopHead, NASTNode definition) {
        loopHead.getSubnode(FORLOOP_DEFINITION).addNode(definition, NASTNode.TAIL);
    }

    public void setLoopCondition(NASTNode loopHead, NASTNode condition) {
        loopHead.getSubnode(FORLOOP_CONDITION).addNode(condition, NASTNode.TAIL);
    }

    public void setLoopIteration(NASTNode loopHead, NASTNode iteration) {
        loopHead.getSubnode(FORLOOP_ITERATION).addNode(iteration, NASTNode.TAIL);
    }

    public void addLoopBody(NASTNode loopHead, NASTNode body) {
        addBodyNode(loopHead, body);
    }

    public NodeType processStar(String s) {
        String baseType = s.replace("*", "");
        NodeType internal = NodeType.valueOf(baseType.toUpperCase().replace("_T", "") + "_STAR");
        return internal;
    }

    public String processLlvmStar(String s) {
        String stars = s.substring(s.indexOf("*"));
        String type =  s.replace("_t", "").replace("*","").toUpperCase();
        String llvmType = Symbols.nativeTypeToLLVM(NodeType.valueOf(type));
        llvmType = llvmType + stars;
        return llvmType;
    }

    public boolean hasMembers(String s) {
        return s.contains(".");
    }

    public NASTNode createArray(NodeType type, int size) {

        // Type
        NASTNode node = new NASTNode(NodeType.ARRAY, type);

        // Size
        node.addNode(new NASTNode(NodeType.INT32, size), NASTNode.TAIL);
        return node;
    }

    public NASTNode accessArray(String name, Object index) {
        NASTNode node = new NASTNode(NodeType.ARRAY_ACCESS, NodeType.VOID);

        NASTNode arrayName = new NASTNode(NodeType.ARRAY, name);
        NASTNode arrayIndex = new NASTNode(NodeType.ARRAY_INDEX, index);

        node.addNode(arrayName, NASTNode.TAIL);
        node.addNode(arrayIndex, NASTNode.TAIL);

        return node;
    }
}
