package nvyc.generation;

import nvyc.data.*;
import nvyc.processing.ParserErrorChecker;
import nvyc.utils.LLVMUtils;
import nvyc.utils.NvyError;
import nvyc.utils.ParserUtils;

import javax.print.attribute.standard.MediaSize;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class Parser {

    private ParserUtils utils = new ParserUtils();
    private FunctionData fundata = FunctionData.getInstance();
    private VariableData vardata = VariableData.getInstance();
    private NvyError err = new NvyError();

    private static final boolean LOCAL_EXPRESSION = false;
    private static final boolean ENCLOSED_EXPRESSION = true;

    private boolean isNativeFunction = false;
    private int forwardDepth = 0;
    private String comingFrom = "";
    private Stack<NodeStream> forloopEnd = new Stack<>();


    public NASTNode parse(NodeStream stream) {
        NodeType type = stream.getType();

        return switch(type) {
            case NATIVE, STATIC, PUBLIC, PRIVATE, CONSTANT, FINAL, FINDADDRESS -> parseModifier(stream);
            case FUNCTION ->  parseFunction(stream);
            case VARDEF ->  parseVardef(stream);
            case FORLOOP ->  parseForLoop(stream);
            case WHILELOOP -> null; // parseWhileLoop(stream);
            case FUNCTIONCALL -> parseFunctionCall(stream);
            case IF -> parseConditional(stream);
            case RETURN -> parseReturn(stream);
            case STRUCT -> parseStruct(stream);
            case ARRAY_ACCESS -> {
                if(stream.next() != null && stream.next().getType() == NodeType.ASSIGN)
                    yield parseAssign(stream);
                else
                    yield parseArrayAccess(stream);
            }

            case VARIABLE -> {
                if(stream.next() != null && stream.next().getType() == NodeType.ASSIGN)
                    yield parseAssign(stream);
                else
                    yield new NASTNode(NodeType.VARIABLE, stream.getValue());
            }
            case PTRDEREF -> {
                if(stream.next() != null && stream.next().getType() == NodeType.ASSIGN)
                    yield parseAssign(stream);
                yield new NASTNode(NodeType.PTRDEREF, stream.getValue());
            }
            // TODO generics

            case MUL -> {
                stream.setType(NodeType.PTRDEREF);
                stream.setValue(stream.next().getValue());
                stream.next().remove();
                yield parseAssign(stream);
            }
            default -> throw new IllegalStateException("Encountered unknown pattern: \n" + stream);
        };
    }

    // -----------------------------------------
    // |               MODIFIERS               |
    // -----------------------------------------

    public NASTNode parseArrayAccess(NodeStream stream) {
        Tuple tuple = (Tuple) stream.getValue();
        String name = (String) tuple.get()[0];
        String index = String.valueOf(tuple.get()[1]);

        return utils.accessArray(name, index);
    }

    public NASTNode parseModifier(NodeStream stream) {
        NodeType type = stream.getType();
        stream = stream.next();

        NASTNode modifierNode = new NASTNode(type, NodeType.VOID);

        switch(type) {
            case NATIVE:
                isNativeFunction = true;
                modifierNode.addNode(parseFunction(stream), NASTNode.TAIL);
                isNativeFunction = false;
                break;

            default:
                break;
        }
        return modifierNode;
    }

    public NASTNode parseArray(NodeStream stream) {
        // (ARRAY_TYPE, int32) -> optional (ARRAY_SIZE, 5)

        NodeType arrayType = (NodeType) stream.getValue();
        NASTNode array;

        if(stream.next().getType() != NodeType.ARRAY_SIZE) {
            array = utils.createArray(arrayType, ParserUtils.BLANK_ARRAY);
        }else{
            int size = Integer.parseInt(stream.next().getValue().toString());
            array = utils.createArray(arrayType, size);
        }

        return array;
    }


    // -----------------------------------------
    // |               STRUCTS                 |
    // -----------------------------------------

    public NASTNode parseStruct(NodeStream stream) {

        // Pointing at either '}' or the first member
        String name = stream.next().getValue().toString();
        NodeType type;
        NASTNode structNode = utils.createStruct(name);
        stream = stream.forwardType(NodeType.OPENBRACE).next();

        int index = 0;
        while((type = stream.getType()) != NodeType.CLOSEBRACE) {
            String memberName = stream.next().getValue().toString();

            if(type == NodeType.STAR) {
                String value = stream.getValue().toString();
                value = value.substring(0, value.length() - 1);
                type = NodeType.valueOf(value.toUpperCase().replace("_T", "") + "_STAR");
            }
            NASTNode memberNode = new NASTNode(type, name);

            utils.addStructNode(structNode, memberNode);

            // struct long { int32 a, int32 b };
            // long, {a: 0, b: 1}, {a: int32, b: int32}
            vardata.addStructMember("%"+name, memberName, index, type);

            index++;

            if(stream.next().getType() == NodeType.CLOSEBRACE) {
                stream = stream.next();
            }else {
                stream = stream.forward(ParserUtils.STRUCT_FORWARD_NEXTARG);
            }
        }

        return structNode;

    }

    // -----------------------------------------
    // |            BLOCK-STATEMENTS           |
    // -----------------------------------------

    public NASTNode parseFunction(NodeStream stream) {

        comingFrom = "parsefunction";

        // func name(type val, type2 val2) -> rtype {...}
        // H    N   NN    N  N ....        NN N     N...

        // Steam is at function name
        stream = stream.forward(ParserUtils.FUNCTION_FORWARD_NAME);
        String name = stream.getValue().toString();
        NASTNode function = utils.createFunction(name);
        //function.setLine(stream.getLine());

        // Will point at either a type or ')'
        stream = stream.forward(ParserUtils.FUNCTION_FORWARD_FIRSTARG);

        // If we don't encounter a close parens, there are arguments to parse in form "type val[,]"
        if(stream.getType() != NodeType.CLOSEPARENS) {

            while(stream.getType() != NodeType.CLOSEPARENS) {

                // Pointing at a type
                NodeType varType = stream.getType();

                if(stream.getType() == NodeType.STAR) {
                    String varLiteral = stream.getValue().toString();
                    varLiteral = varLiteral.substring(0, varLiteral.length() - 1);
                    varType = NodeType.valueOf(varLiteral.toUpperCase().replace("_T", "") + "_STAR");
                }


                String varName = stream.next().getValue().toString();
                utils.addFunctionArg(function, utils.createArgument(varType, varName));
                fundata.addNamedParam(name, "%" + varName);
                fundata.addParameter(name, varType);

                // Moves past value to either ')' or ','
                stream = stream.forward(ParserUtils.FUNCTION_FORWARD_NEXTARG);
                if(stream.getType() == NodeType.COMMADELIMIT) stream = stream.next();
            }
        }

        // The stream is now pointing at the return type
        stream = stream.forward(ParserUtils.FUNCTION_FORWARD_RETURNTYPE);

        if(stream.getType() == NodeType.STAR) {
            utils.setReturnType(function, utils.processStar(stream.getValue().toString()));
            fundata.setReturnType(name, utils.processStar(stream.getValue().toString()));
            fundata.setLlvmReturnType(name, utils.processLlvmStar(stream.getValue().toString()));
        }

        // Struct returns
        // TODO ensure that the struct actually exists, otherwise this allows for any variable to be counted as a struct,
        else if(stream.getType() == NodeType.VARIABLE) {
            utils.setReturnType(function, NodeType.STRUCT);
            fundata.setReturnType(name, NodeType.STRUCT);
            fundata.setLlvmReturnType(name, "%" + stream.getValue().toString());
        }

        else {
            utils.setReturnType(function, stream.getType());
            fundata.setReturnType(name, stream.getType());
            fundata.setLlvmReturnType(name, Symbols.nativeTypeToLLVM(stream.getType()));
        }
        // Parse the body only if it isn't native
        if(!isNativeFunction) {
            stream = stream.forward(ParserUtils.FUNCTION_FORWARD_FIRSTEXPR);
            List<NASTNode> bodyNodes = parseBodyNodes(stream);
            for (NASTNode subnode : bodyNodes) {
                utils.addFunctionBody(function, subnode);
            }
        }

        return function;
    }

    public NASTNode parseFunctionCall(NodeStream stream) {

        comingFrom = "parsefunctioncall";

        NASTNode call = utils.createFunctionCall(stream.getValue().toString());

        for(NodeStream substream : getFunctionCallArgs(stream)) {
            if(substream.getType() == NodeType.COMMADELIMIT) {
                substream = substream.next();
            }
            utils.addCallArg(call, parseExpression(getExpression(substream, ENCLOSED_EXPRESSION)));
        }

        return call;
    }

    public List<NodeStream> getFunctionCallArgs(NodeStream stream) {

        comingFrom = "getfunccallargs";

        NodeType type;
        List<NodeStream> list = new ArrayList<>();

        // Empty call -> return nothing
        // func()
        // H   NN

        stream = stream.forward(2);

        if(stream.getType().equals(NodeType.CLOSEPARENS)) {
            return list;
        }

        // Cut at first argument
        NodeStream copy = stream.cutheadAndReturn();

        /*

            Cases

            functioncall    f(x)
            arith           1 + 2
            logic           1 > 2
            variable        x

         */

        Stack<Integer> args = new Stack<>();
        args.push(1);

        while(!args.isEmpty()) {

            type = copy.getType();

            // Do not split if a parameter has open parens. There is a subexpr to evaluate first
            if(type == NodeType.OPENPARENS) {
                args.push(1);
            }

            // Either closing a subexpr or closing the function call
            else if(type == NodeType.CLOSEPARENS) {
                args.pop();
                if(args.isEmpty()) list.add(copy.prev().cutoffAndReturn());
            }

            // If the size of the stack is 1, we can split at the last argument
            else if (args.size() == 1 && type == NodeType.COMMADELIMIT) {
                list.add(copy.prev().cutoffAndReturn());
                copy = copy.cutheadAndReturn();
            }

            // Move to the next node
            if(!args.isEmpty() && copy.next() != null) {
                copy = copy.next();
            }

        }

        return list;
    }

    public NASTNode parseConditional(NodeStream stream) {

        comingFrom = "parseconditional";

        NASTNode conditional = utils.createConditional();

        // Pointing at start of expression
        stream = stream.forward(2);

        NodeStream copy = stream.copyAtCurrent();
        //copy = copy.forwardType(NodeType.OPENBRACE).prev().cutoffAndReturn();
        copy = copy.cutheadAndReturn();
        copy = copy.forwardType(NodeType.OPENBRACE).prev().prev().cutoffAndReturn();
        NodeStream expression = getExpression(copy.backtrack(), ENCLOSED_EXPRESSION);
        utils.setCondition(conditional, parseExpression(expression));

        stream = stream.forwardType(NodeType.OPENBRACE).next();
        List<NASTNode> bodyNodes = parseBodyNodes(stream);

        // TODO could create helper NASTNode.setSubnodeTree(#List<NASTNode>) to avoid iteration
        for(NASTNode subnode : bodyNodes) {
            utils.addConditionalBody(conditional, subnode);
        }

        // Initially pointing at if() {...} <-, next points it to whatever code is next or another '}'
        stream = utils.moveToMatchingDelimiter(stream, NodeType.OPENBRACE, NodeType.CLOSEBRACE);

        bodyNodes = parseBodyNodes(stream);
        for(NASTNode subnode : bodyNodes) {
            utils.addConditionalElseBody(conditional, subnode);
        }

        return conditional;

    }

    public List<NASTNode> parseBodyNodes(NodeStream stream) {

        comingFrom = "parsebodynodes";

        NodeType type;
        List<NASTNode> bodyNodes = new ArrayList<>();
        Stack<Integer> braces = new Stack<>();
        braces.push(1);

        while(!braces.isEmpty()) {
            type = stream.getType();

            switch(type) {
                case OPENBRACE -> braces.push(1);
                case CLOSEBRACE -> braces.pop();
                case ENDOFLINE -> stream = stream.next();
                default -> {
                    type = stream.getType();
                    NASTNode node = parse(stream);
                    bodyNodes.add(node);

                    if(type == NodeType.FORLOOP) {
                        stream = stream.forwardType(NodeType.OPENBRACE).next();
                        forwardDepth = utils.getDepth(stream, NodeType.OPENBRACE, NodeType.CLOSEBRACE) + 1;
                        stream = stream.forward(forwardDepth);
                    }
                    else if(type != NodeType.IF) stream = stream.forwardType(NodeType.ENDOFLINE);
                    else braces.pop();
                }
            }
        }

        return bodyNodes;
    }


    // -----------------------------------------
    // |              EXPRESSIONS              |
    // -----------------------------------------

    public NASTNode parseAssign(NodeStream stream) {

        comingFrom = "parseassign";

        // x = expr;
        String name = stream.getValue().toString();
        boolean ptrderef = stream.getType() == NodeType.PTRDEREF;
        boolean arrayAccess = stream.getType() == NodeType.ARRAY_ACCESS;
        NASTNode head = null;

        /*

            Head node for what to assign to
            Value node for result

         */

        // TODO member access

        if(arrayAccess) {
            Tuple tuple = (Tuple) stream.getValue();
            name = (String) tuple.get()[0];
            Object idx = tuple.get()[1];
            head = utils.accessArray(name, idx);
        }

        else if(ptrderef) {
            head = new NASTNode(NodeType.PTRDEREF, NodeType.VOID);
            head.addNode(new NASTNode(NodeType.VARIABLE, name), NASTNode.TAIL);
            //utils.derefAssign(name, value);
        }

        else if(name.contains(".")) {
            head = utils.accessMember(name);
            // head =  utils.assignVariableWithMembers(memberNode, value);
        }

        else {
            head = utils.declareVariable(name);
        }

        stream = stream.forward(2);
        NASTNode value = parseExpression(getExpression(stream, LOCAL_EXPRESSION));

        return utils.assign(head, value);


        //return utils.assignVariable(name, value);
    }

    public NASTNode parseVardef(NodeStream stream) {

        comingFrom = "parsevardef";

        // let name = ...;
        // H   N    N N...
        String name = stream.next().getValue().toString();
        NASTNode variable = utils.defineVariable(name);

        stream = stream.forward(ParserUtils.VARDEF_FORWARD_EXPR); // pointing at start of expression
        // Cast the variable if needed
        if(
                stream.getType() == NodeType.OPENPARENS
                        && Symbols.isType(stream.next().getType())
                        && stream.next().next().getType() == NodeType.CLOSEPARENS
        ) {

            if(stream.next().getType() == NodeType.VARIABLE) {
                utils.castVariableToStruct(variable, stream.next().getValue().toString());
            }

            else if(stream.next().getType() == NodeType.STAR) {
                String type = (stream.next().getValue().toString() + "_STAR").replace("_T", "");
                NodeType nodeType = NodeType.valueOf(type);
                utils.castVariable(variable, nodeType);
                vardata.setType("%" + name, nodeType);
                vardata.setLlvmType("%" + name, Symbols.nativeTypeToLLVM(nodeType));
            }
            else {
                utils.castVariable(variable, stream.next().getType());
                vardata.setLlvmType("%" + name, Symbols.nativeTypeToLLVM(stream.next().getType()));
                vardata.setType("%" + name, stream.next().getType());
            }
            stream = stream.next().next().next(); // pointing at expr
        }

        // Return early if just allocating space for type
        if(stream.getType() == NodeType.ENDOFLINE) return variable;


        // TODO fold constants
        NASTNode expression = parseExpression(getExpression(stream, LOCAL_EXPRESSION));

        // TODO arrays


        //if(expression.contains(NodeType.VARIABLE) || expression.contains(NodeType.FUNCTIONCALL))
        utils.setVariableValue(variable, expression);

        // stream = stream.next();
        return variable;
    }

    public NASTNode parseReturn(NodeStream stream) {

        comingFrom = "parsereturn";

        NASTNode value = parseExpression(getExpression(stream.next(), LOCAL_EXPRESSION));
        return utils.createReturn(value);

    }


    /*public NASTNode parseExpression(NodeStream stream) {

        // TODO this needs to be rewritten to be more generic.
        isUnary = true;
        comingFrom = "parseexpr";

        Stack<NASTNode> valueStack = new Stack<>();
        Stack<NodeType> operatorStack = new Stack<>();

        while(stream != null) {
            //isUnary = true;
            NodeType tokenType = stream.getType();

            // Member access

            if(tokenType == NodeType.VARIABLE && stream.getValue().toString().contains(".")) {
                valueStack.push(utils.accessMember(stream.getValue().toString()));
            }

            else if(tokenType == NodeType.ARRAY_TYPE) {
                valueStack.push(parseArray(stream));
            }

            else if(tokenType == NodeType.ARRAY_ACCESS) {
                valueStack.push(parseArrayAccess(stream));
            }

            else if(Symbols.MEMORY_SYMBOLS.contains(tokenType)) {
                valueStack.push(new NASTNode(stream.getStripped()));
            }

            // If its a literal or variable/pointer, it's a single operator
            else if(Symbols.isLiteral(tokenType) || tokenType == NodeType.VARIABLE) {
                isUnary = false;
                valueStack.push(new NASTNode(stream.getStripped()));
            }

            // If its a function call, it must first be parsed
            else if(tokenType == NodeType.FUNCTIONCALL) {
                int depth = utils.getDepth(stream, NodeType.OPENPARENS, NodeType.CLOSEPARENS);
                valueStack.push(parseFunctionCall(stream));
                stream = stream.forward(depth);
            }

            // If it's an operator, condense the LHS and RHS into a single node
            else if(Symbols.isOperator(tokenType)) {
                while(
                        // operatorstack isnt empty
                        // operatorstack isnt an open parens
                        // precedence is valid
                        !operatorStack.isEmpty()
                        && operatorStack.peek() != NodeType.OPENPARENS
                        && Symbols.operatorPrecedence(operatorStack.peek()) >= Symbols.operatorPrecedence(tokenType)
                ) {
                    processOperator(operatorStack, valueStack);
                }
                operatorStack.push(tokenType);
                isUnary = true;
            }

            // Open parens means a higher priority operation
            else if(tokenType == NodeType.OPENPARENS)
                operatorStack.push(tokenType);

            // Close parens means we keep evaluating everything in the stack until we reach the first open parens
            else if (tokenType == NodeType.CLOSEPARENS) {
                while (!operatorStack.isEmpty() && operatorStack.peek() != NodeType.OPENPARENS) {
                    processOperator(operatorStack, valueStack);
                }
                operatorStack.pop();
            }

            stream = stream.next();
        }

        // Process anything else inside
        while(!operatorStack.isEmpty()) {
            processOperator(operatorStack, valueStack);
        }

        // This should have a single tree with every operation combined into it
        return valueStack.pop();
    }

    private void processOperator(Stack<NodeType> operatorStack, Stack<NASTNode> valueStack) {

        comingFrom = "processoperator";



        // Combine operands and push back to stack
        NodeType operation = operatorStack.pop();

        if (Symbols.isPrefixOperator(operation) && isUnary) {
            NASTNode rhs = valueStack.pop();
            operation = Symbols.mapUnaryOperator(operation);
            NASTNode node = new NASTNode(operation, NodeType.VOID);
            node.addNode(rhs, NASTNode.TAIL);
            valueStack.push(node);
        } else {
            if (valueStack.size() < 2) {
                err.NV_STDERR("ERROR: Not enough values in stack for operator " + operatorStack.peek());
                err.NV_STDERR("Values: " + valueStack.toString());
                err.NV_STDERR("Operators: " + operatorStack.toString());
                System.exit(1);
            }
            NASTNode rhs = valueStack.pop();
            NASTNode lhs = valueStack.pop();
            NASTNode node = new NASTNode(operation, operation);
            node.addNode(lhs, NASTNode.TAIL);
            node.addNode(rhs, NASTNode.TAIL);
            valueStack.push(node);
        }
    }*/

    public NASTNode parseExpression(NodeStream stream) {

        comingFrom = "parseexpr";

        Stack<NASTNode> valueStack = new Stack<>();
        Stack<NodeType> operatorStack = new Stack<>();

        boolean expectUnary = true; // true when the next operator could be unary

        while (stream != null) {
            NodeType tokenType = stream.getType();

            // Member access
            if (tokenType == NodeType.VARIABLE && stream.getValue().toString().contains(".")) {
                valueStack.push(utils.accessMember(stream.getValue().toString()));
                expectUnary = false;
            }

            else if (tokenType == NodeType.ARRAY_TYPE) {
                valueStack.push(parseArray(stream));
                expectUnary = false;
            }

            else if (tokenType == NodeType.ARRAY_ACCESS) {
                valueStack.push(parseArrayAccess(stream));
                expectUnary = false;
            }

            else if (Symbols.MEMORY_SYMBOLS.contains(tokenType)) {
                valueStack.push(new NASTNode(stream.getStripped()));
                expectUnary = false;
            }

            // Literals and variables
            else if (Symbols.isLiteral(tokenType) || tokenType == NodeType.VARIABLE) {
                valueStack.push(new NASTNode(stream.getStripped()));
                expectUnary = false;
            }

            // Function calls
            else if (tokenType == NodeType.FUNCTIONCALL) {
                int depth = utils.getDepth(stream, NodeType.OPENPARENS, NodeType.CLOSEPARENS);
                valueStack.push(parseFunctionCall(stream));
                stream = stream.forward(depth);
                expectUnary = false;
            }

            // Operators
            else if (Symbols.isOperator(tokenType)) {
                NodeType actualOp = tokenType;

                // If we expect a unary and this is a prefix operator, convert it
                if (expectUnary && Symbols.isPrefixOperator(tokenType)) {
                    actualOp = Symbols.mapUnaryOperator(tokenType);
                }

                while (
                        !operatorStack.isEmpty()
                                && operatorStack.peek() != NodeType.OPENPARENS
                                && Symbols.operatorPrecedence(operatorStack.peek()) >= Symbols.operatorPrecedence(actualOp)
                ) {
                    processOperator(operatorStack, valueStack);
                }

                operatorStack.push(actualOp);
                expectUnary = true; // after an operator, the next token might be unary
            }

            // Parentheses
            else if (tokenType == NodeType.OPENPARENS) {
                operatorStack.push(tokenType);
                expectUnary = true;
            }

            else if (tokenType == NodeType.CLOSEPARENS) {
                while (!operatorStack.isEmpty() && operatorStack.peek() != NodeType.OPENPARENS) {
                    processOperator(operatorStack, valueStack);
                }
                operatorStack.pop(); // discard '('
                expectUnary = false;
            }

            stream = stream.next();
        }

        // Process any remaining operators
        while (!operatorStack.isEmpty()) {
            processOperator(operatorStack, valueStack);
        }

        return valueStack.pop();
    }

    // processOperator no longer uses isUnary — just applies based on operator type
    private void processOperator(Stack<NodeType> operatorStack, Stack<NASTNode> valueStack) {

        comingFrom = "processoperator";

        NodeType operation = operatorStack.pop();

        if (Symbols.isUnaryOperator(operation)) {
            // prefix unary operation (already mapped)
            NASTNode rhs = valueStack.pop();
            NASTNode node = new NASTNode(operation, NodeType.VOID);
            node.addNode(rhs, NASTNode.TAIL);
            valueStack.push(node);
        } else {
            // binary operator
            if (valueStack.size() < 2) {
                err.NV_STDERR("ERROR: Not enough values in stack for operator " + operation);
                err.NV_STDERR("Values: " + valueStack);
                err.NV_STDERR("Operators: " + operatorStack);
                System.exit(1);
            }

            NASTNode rhs = valueStack.pop();
            NASTNode lhs = valueStack.pop();
            NASTNode node = new NASTNode(operation, operation);
            node.addNode(lhs, NASTNode.TAIL);
            node.addNode(rhs, NASTNode.TAIL);
            valueStack.push(node);
        }
    }


    // TODO when called, always assumes whatever follows is an expression. Fix that
    private NodeStream getExpression(NodeStream stream, boolean enclosed) {
        comingFrom = "getexpr";

        NodeStream copy = stream.cutheadAndReturn();
        NodeType delimiter = enclosed ? NodeType.OPENPARENS : NodeType.ENDOFLINE;


        if(copy.length() == 1) return stream;

        if(!enclosed) {
            // If it isn't enclosed between (), its a regular line like "let x = expr;" ending in a semicolon
            while (
                    copy.next() != null &&
                            !Symbols.isStarterSymbol(copy.getType())
            ) {
                copy = copy.next();
            }

            copy = copy.prev();

            if (Symbols.isStarterSymbol(copy.getType())) {
                String fail = ParserErrorChecker.reconstruct(copy.cutoffAndReturn().backtrack());
                err.FAILCOMPILE(ErrorType.MISSING_SEMICOLON, fail);
            }

            copy.cutoff();
        }

        // enclosed == ENCLOSED_EXPRESSION
        // enclosed expressions exist inside bodies, such as if statement conditions or function call arguments
        //if(enclosed) {
        if (copy.getType() != NodeType.CLOSEPARENS && !Symbols.isExpression(copy)) {
            err.NV_STDERRF("ERROR > Expected expression but didn't find one at %s\n", copy.backtrack());
            System.exit(0);
        }
        //}

        return copy.backtrack();
    }



    public NASTNode parseForLoop(NodeStream stream) {

        comingFrom = "parseforloop";

        NASTNode loop = utils.createForLoop();
        NodeStream expression;
        NodeStream copy = stream.cutheadAndReturn();

        // for(let i = 0; i < 10; i+1) { ... }
        // copy is always pointing toward either a ';' or ')' during loop creation

        // Definition
        // let i = 0
        copy = copy.forwardType(NodeType.OPENPARENS).next();
        utils.setLoopDefinition(loop, parseVardef(copy));

        // Condition
        // i < 10
        copy = copy.next().forwardType(NodeType.ENDOFLINE).next();
        expression = getExpression(copy, LOCAL_EXPRESSION);
        utils.setLoopCondition(loop, parseExpression(expression));

        // Iteration
        // i + 1
        copy = copy.next().forwardType(NodeType.ENDOFLINE).next();
        copy.cuthead();
        copy = copy.forwardType(NodeType.OPENBRACE).prev().prev();
        expression = getExpression(copy.cutoffAndReturn(), ENCLOSED_EXPRESSION);
        utils.setLoopIteration(loop, parseExpression(expression));

        // copy now points to the body or a '}'
        copy = copy.next().next().next();



        List<NASTNode> bodyNodes = parseBodyNodes(copy);
        for(NASTNode subnode : bodyNodes) {
            utils.addLoopBody(loop, subnode);
        }

        return loop;
    }


    public List<NodeStream> parseList(NodeStream root) {
        root = resolveDoublesPass(root);

        List<NodeStream> nodes = new ArrayList<>();

        NodeType t;
        while(root != null) {
            t = root.getType();
            switch(t) {
                case FUNCTION:
                    if(root.prev().getType() == NodeType.NATIVE) {
                        root = root.prev();
                    }
                    NodeStream cpy = root.cutheadAndReturn();

                    if(root.getType() != NodeType.NATIVE) {
                        Stack<Integer> brace = new Stack<>();
                        cpy = cpy.forwardType(NodeType.OPENBRACE);
                        root = root.forwardType(NodeType.OPENBRACE);
                        brace.push(1);
                        while (!brace.isEmpty()) {
                            cpy = cpy.next();
                            root = root.next();
                            if (cpy.getType() == NodeType.OPENBRACE) {
                                brace.push(1);
                            } else if (cpy.getType() == NodeType.CLOSEBRACE) {
                                brace.pop();
                            }
                        }
                    }else{
                        cpy = cpy.forwardType(NodeType.ENDOFLINE);
                        root = root.next();
                    }

                    cpy = cpy.cutoffAndReturn();
                    nodes.add(cpy);
                    break;
                case VARDEF:
                    NodeStream cpy2 = root.cutheadAndReturn();
                    cpy2 = cpy2.cutheadAndReturn();
                    cpy2 = cpy2.forwardType(NodeType.ENDOFLINE);
                    root = root.forwardType(NodeType.ENDOFLINE);
                    cpy2 = cpy2.cutoffAndReturn();
                    cpy2 = cpy2.backtrack();
                    nodes.add(cpy2);
                    break;
                case STRUCT:
                    NodeStream cpy3 = root.cutheadAndReturn();
                    cpy3 = cpy3.forwardType(NodeType.CLOSEBRACE).next();
                    root = root.forwardType(NodeType.CLOSEBRACE).next();
                    cpy3 = cpy3.cutoffAndReturn();
                    cpy3 = cpy3.backtrack();
                    nodes.add(cpy3);
                    break;
                default:
                    break;
            }
            root = root.next();
        }
        return nodes;
    }

    public NodeStream resolveDoublesPass(NodeStream root) {
        while(root != null && root.getType() != NodeType.ENDOFSTREAM) {
            if(root.next() != null) {
                NodeType curr = root.getType();
                NodeType next = root.next().getType();

                if(LLVMUtils.TYPE_SYMBOLS.contains(curr) && root.next().getType() == NodeType.MUL) {
                    StringBuilder builder = new StringBuilder();
                    builder.append(curr.toString().toLowerCase());

                    NodeStream foot = root;

                    foot = foot.next();
                    while(foot.getType() == NodeType.MUL) {
                        builder.append("*");
                        foot = foot.next();
                    }

                    foot.setPrev(root);
                    root.setType(NodeType.STAR);
                    root.setValue(builder.toString());

                    // TODO yes this leaks memory, no i dont care right now. the JVM will deal with it
                    // the leak comes from head skipping to foot without deleting nodes in between the two
                    // for now, i can only hope the GC can find the dead references and clean them
                    root.setNext(foot);
                    foot.setPrev(root);

                }

                // int32 [ ]
                else if(Symbols.isType(curr)
                        && root.next().getType() == NodeType.OPENBRKT
                        && root.next().next().getType() == NodeType.CLOSEBRKT)
                {
                    NodeStream array = new NodeStream(NodeType.ARRAY_TYPE, curr);

                    /*

                           /   array           \
                        prev    root    [   ]   next

                        array.prev is root.prev
                        array.next is root.next.next.next
                     */

                    array.setPrev(root.prev());
                    array.setNext(root.next().next().next());
                    root = array;
                }

                // variable[index] = ... -> array access
                else if(curr == NodeType.VARIABLE
                        && root.next().getType() == NodeType.OPENBRKT
                        && (root.next().next().getType() == NodeType.INT32
                        || root.next().next().getType() == NodeType.VARIABLE)
                        && root.next().next().next().getType() == NodeType.CLOSEBRKT
                    //&& root.next().next().next().next().getType() == NodeType.ASSIGN
                )
                {
                    Object index = null;
                    if(root.next().next().getType() == NodeType.INT32) {
                        index = Integer.parseInt(root.next().next().getValue().toString());
                    }else{
                        index = root.next().next().getValue().toString();
                    }
                    String variable = root.getValue().toString();
                    Tuple data = new Tuple(variable, index);
                    NodeStream access = new NodeStream(NodeType.ARRAY_ACCESS, data);

                    access.setPrev(root.prev());
                    access.setNext(root.next().next().next().next());
                    root.prev().setNext(access);
                    root.next().next().next().next().setPrev(access);
                    root = access;
                }

                // int32 [ 5 ]
                else if(Symbols.isBuiltinType(curr)
                        && root.next().getType() == NodeType.OPENBRKT
                        && root.next().next().getType() == NodeType.INT32
                        && root.next().next().next().getType() == NodeType.CLOSEBRKT
                )
                {
                    NodeStream array = new NodeStream(NodeType.ARRAY_TYPE, curr);
                    NodeStream size = new NodeStream(NodeType.ARRAY_SIZE, root.next().next().getValue());

                    /*

                           /   array   size     \
                        prev    root    [  5   ]   next

                        array.prev is root.prev
                        array.next is root.next.next.next
                     */

                    root.prev().setNext(array);
                    array.setPrev(root.prev());
                    array.setNext(size);
                    size.setPrev(array);
                    size.setNext(root.next().next().next().next());
                    root.next().next().next().next().setPrev(size);
                    root = array;
                }

                /*else if(curr == NodeType.BITAND && root.next().getType() == NodeType.VARIABLE) {
                    NodeType prevType = root.prev().getType();
                    // Ensure previous symbol won't cause an issue
                    // let x = 1 + &b is valid
                    // let x = 1 &b is a BITAND, not a dereference
                    if(
                            LLVMUtils.ARITH_SYMBOLS.contains(prevType) ||
                            LLVMUtils.LOGIC_SYMBOLS.contains(prevType) ||
                            LLVMUtils.SHIFT_SYMBOLS.contains(prevType) ||
                            LLVMUtils.DELIMIT_SYMBOLS.contains(prevType)
                    ) {
                        root.setType(NodeType.FINDADDRESS);
                        root.setValue(root.next().getValue());
                        root.next().remove();
                    }
                }
                else if(curr == NodeType.MUL && root.next().getType() == NodeType.VARIABLE) {
                    NodeType prevType = root.prev().getType();
                    if(
                            LLVMUtils.ARITH_SYMBOLS.contains(prevType) ||
                            LLVMUtils.LOGIC_SYMBOLS.contains(prevType) ||
                            LLVMUtils.SHIFT_SYMBOLS.contains(prevType) ||
                            LLVMUtils.DELIMIT_SYMBOLS.contains(prevType)
                    ) {
                        root.setType(NodeType.PTRDEREF);
                        root.setValue(root.next().getValue());
                        root.next().remove();
                    }
                }*/
                else if(curr == next && !Set.of(NodeType.OPENBRACE, NodeType.CLOSEBRACE, NodeType.OPENPARENS, NodeType.CLOSEPARENS).contains(curr)) {
                    String resolve = "";

                    if(root.next().next() != null && root.next().next().getType() == root.getType())
                        resolve = root.getValue().toString() +  root.getValue().toString() +  root.getValue().toString();
                    else
                        resolve = root.getValue().toString() + root.next().getValue().toString();

                    NodeType t = strton(resolve);
                    if(t == null) {
                        err.NV_STDERR("nvc > Invalid operator: " + resolve);
                        System.exit(1);
                    }

                    NodeStream s = new NodeStream(strton(resolve), resolve);
                    root.prev().setNext(s);
                    root.next().next().setPrev(s);
                    s.setPrev(root.prev());
                    s.setNext(root.next().next());
                    root.next().remove();
                    root = s;
                }else if(Set.of(NodeType.LT, NodeType.GT, NodeType.NOT).contains(curr) && next == NodeType.ASSIGN) {
                    NodeStream s = new NodeStream(null, null);
                    switch(curr) {
                        case LT -> s = new NodeStream(NodeType.LTE, "<=");
                        case GT -> s = new NodeStream(NodeType.GTE, ">=");
                        case NOT -> s = new NodeStream(NodeType.NEQ, "!=");
                    }
                    root.prev().setNext(s);
                    root.next().next().setPrev(s);
                    s.setPrev(root.prev());
                    s.setNext(root.next().next());
                    root.next().remove();
                    root = s;
                }
            }
            root = root.next();
        }

        return root.backtrack();
    }

    public NodeType strton(String s) {
        return switch (s) {
            case "||" -> NodeType.LOGICOR;
            case "&&" -> NodeType.LOGICAND;
            case ">>" -> NodeType.ARITHRIGHTSHIFT;
            case "<<" -> NodeType.ARITHLEFTSHIFT;
            case ">>>" -> NodeType.LOGICRIGHTSHIFT;
            case "==" -> NodeType.EQ;
            default -> null;
        };
    }

}
