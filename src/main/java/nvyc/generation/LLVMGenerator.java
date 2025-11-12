package nvyc.generation;

import nvyc.data.*;
import nvyc.utils.LLVMUtils;
import nvyc.utils.NvyError;

import java.util.*;

public class LLVMGenerator {

    private final VariableData vardata = VariableData.getInstance();
    private final ScopeData scopedata = ScopeData.getInstance();
    private final FunctionData fundata = FunctionData.getInstance();
    private final LLVMUtils utils = new LLVMUtils();
    private final NvyError err = new NvyError();

    private boolean returnFromCall = true;          // Determines if function return value is stored or discarded
    private boolean hasReturnStatement = true;      // Assume no function has a return statement until proven otherwise
    private String currentFunction = "null";

    // TODO fails on strings. Need to reload any variable that is used inside a loop
    private boolean reloadReturnValue = false;

    private final List<String> globalValues = new ArrayList<>();
    private final List<String> variadicFunctions = new ArrayList<>();



    /*

        // TODO known issues

         func main() -> int32 {
        let x = 12;
        let y = &x;
        let z = *y;
        return y;
            }

        attempts to return "ret null %2" through double indirection after loading y to z,
        but the double indirection never has its type set to i32

     */

    public List<String> getGlobalValues() {
        return globalValues;
    }

    public List<String> compileLLVM(NASTNode n) {
        return compileLLVM(new ArrayList<>(Collections.singleton(n)));
    }

    public List<String> compileLLVM(List<NASTNode> nodes) {
        List<String> result = new ArrayList<>();
        NodeType t;
        for(NASTNode n : nodes) {
            t = n.getType();
            switch(t) {
                case FORLOOP -> result.addAll(llvmForLoop(n));
                case NATIVE -> result.add(llvmNative(n));
                case FUNCTIONCALL -> result.addAll(llvmFunctionCall(n));
                case ASSIGN -> result.addAll(llvmAssign(n));
                case VARDEF -> result.addAll(llvmVardefPrep(n));
                case FUNCTION -> result.addAll(llvmFunction(n));
                case RETURN -> result.addAll(llvmReturn(n));
                case STRUCT -> result.add(llvmStruct(n));
                case
                        ADD, SUB, MUL, DIV,
                                BITAND, BITOR, BITXOR,
                                ARITHLEFTSHIFT, LOGICRIGHTSHIFT, ARITHRIGHTSHIFT,
                                MODULO
                        -> result.addAll(llvmExpr(n, LLVMUtils.ARITHMETIC_EXPR));
                case LOGICAND, LOGICOR, EQ, NEQ, LT, LTE, GT, GTE -> result.addAll(llvmExpr(n, LLVMUtils.LOGICAL_EXPR));
                case IF -> result.addAll(llvmConditional(n));
            }
        }
        return result;
    }

    public String llvmStruct(NASTNode n) {
        String struct = utils.createStruct(n);
        return struct;
    }

    public String llvmNative(NASTNode node) {

        NASTNode function = node.getSubnode(0);
        NASTNode functionParameters = function.getSubnode(0);
        NASTNode functionReturn = function.getSubnode(1);

        String functionName = function.getValueString();
        NodeType returnType = functionReturn.getSubnode(0).getType();

        for(NASTNode param : functionParameters.getAllSubnodes()) {
            String paramName = param.getValueString();
            NodeType type = param.getType();

            if(type == NodeType.UNIFIED) variadicFunctions.add(functionName);

            utils.initializeType(paramName, type);
            vardata.addFunctionVariable(paramName);
            //fundata.addParameter(functionName, type);
        }

        fundata.setReturnType(functionName, returnType);
        String result = utils.createFunction(functionName, LLVMUtils.FUNCTION_DECLARATION) + "\n";
        vardata.emptyFunctionVariables();
        return result;
    }

    public List<String> llvmConditional(NASTNode node) {
        reloadReturnValue = true;
        List<String> result = new ArrayList<>();

        NASTNode condition = node.getSubnode(0);
        NASTNode bodyNode = node.getSubnode(1);
        NASTNode elseNode = node.getSubnode(2);

        NASTNode conditionBody = condition.getSubnode(0);
        NodeType conditionType = conditionBody.getType();

        String iftrue = utils.iftrue();
        String iffalse = utils.iffalse();
        boolean hasReturn = bodyNode.contains(NodeType.RETURN);
        utils.increaseConditionalDepth();

        /*

            expr -> evaluate, compare 0 to last result
            variable -> dereference, compare to 0
            literal -> can jump directly

        */

        // If it's a literal, immediately jump to true/false branch based on truthy/falsy-ness
        if(utils.isLiteral(conditionBody)) {
            if(utils.isNonZeroLiteral(conditionBody))
                result.add(utils.jumpDirectly(iftrue));
            else
                result.add(utils.jumpDirectly(iffalse));
        }

        // Same as expressions, but inequality is baked in
        else if(utils.isLogical(conditionType)) {
            result.addAll(compileLLVM(conditionBody));
            result.add(utils.jumpConditional(iftrue, iffalse, utils.getLastResult()));
        }

        // Expressions
        else if(utils.isArith(conditionType) || conditionType == NodeType.FUNCTIONCALL) {
            result.addAll(compileLLVM(conditionBody));
            result.add(utils.computeInequality("%" + utils.getLastResult()));
            result.add(utils.jumpConditional(iftrue, iffalse, utils.getLastResult()));

        }

        // Dereference and compare value to 0
        else if(conditionType == NodeType.VARIABLE) {
            String variable = conditionBody.getValueString();

            if(scopedata.isGlobal(variable)) variable = "@global_" + variable;
            else variable = "%" + variable;

            result.add(utils.dereferenceVariable(variable));
            result.add(utils.computeInequality("%" + utils.getLastResult()));
            result.add(utils.jumpConditional(iftrue, iffalse, utils.getLastResult()));
        }

        scopedata.increaseDepth();
        // Compile "if true" nodes
        result.add(iftrue + ":\n");
        for(NASTNode bodySubnode : bodyNode.getAllSubnodes()) {
            result.addAll(compileLLVM(bodySubnode));
        }

        if(!hasReturn)
            result.add(utils.jumpDirectly(iffalse));
            /*
                    if(!hasReturn)
            result.add(String.format("\tbr label %%ifelse%d\n", localcond));
             */

        // Compile "if false" nodes
        result.add(iffalse + ":\n");
        for(NASTNode elseSubnode : elseNode.getAllSubnodes()) {
            result.addAll(compileLLVM(elseSubnode));
        }

        scopedata.decreaseDepth();
        utils.decreaseConditionalDepth();
        return result;

    }

    public List<String> llvmExpr(NASTNode node, int exprType) {
        List<String> result = new ArrayList<>();
        returnFromCall = true;

        // Arithmetic operation to perform
        NodeType operation = node.getType();

        NASTNode lhs = node.getSubnode(0);
        NASTNode rhs = node.getSubnode(1);

        NodeType lhsType = lhs.getType();
        NodeType rhsType = rhs.getType();

        String lhsOperand = lhs.getValueString();
        String rhsOperand = rhs.getValueString();

        if(lhsType == NodeType.VARIABLE) {
            if(scopedata.isGlobal(lhsOperand)) lhsOperand = "@global_" + lhsOperand;
            else lhsOperand = "%" + lhsOperand;
        }
        if(rhsType == NodeType.VARIABLE) {
            if(scopedata.isGlobal(rhsOperand)) rhsOperand = "@global_" + rhsOperand;
            else rhsOperand = "%" + rhsOperand;
        }

        String[] operands = {lhsOperand, rhsOperand};
        NodeType[] types = {lhsType, rhsType};
        NASTNode[] sides = {lhs, rhs};

        /*

        It's most likely that for arithmetic,
        literals and variables will be used commonly.

        Check those first since they're the most
        common and cheapest. Function params go next

        Then the heaviest checks

         */


        for(int i = 0; i < 2; i++) {
            String operand = operands[i];
            NodeType type = types[i];
            NASTNode side = sides[i];

            /*

                Catch in order of priority
                Literals and function calls are separate entities so they can go first
                Arith and Logic take priority over loading since they need to be computed

             */
            // Move onto the next iteration if its a literal. Default value already handles this
            if(utils.isLiteral(side)) {
                if(type == NodeType.FP32 || type == NodeType.FP64) {
                    /*
                                    float tmp = Float.parseFloat(storedValue);
                floatval = storedValue;
                storedValue = String.valueOf(Float.floatToRawIntBits(tmp)); //utils.floatToHex(tmp);
                String globalName = utils.bitcastVariableI32ToF32();
                globalValues.add(utils.allocateGlobal(globalName, NodeType.INT32, storedValue, floatval));
                storedValue = "@global_" + globalName;
                utils.initializeType(storedValue, NodeType.INT32);
                result.add(utils.dereferenceVariable(storedValue));
                storedValue = "%" + utils.getLastResult();
                result.add(utils.bitcastIntToFloat(storedValue));
                storedValue = "%" + globalName;
                     */

                    float tmp = Float.parseFloat(operand);
                    String floatValue = utils.floatToIntString(tmp);
                    String globalName = utils.bitcastVariableI32ToF32();
                    String storedValue = "@global_" + globalName;

                    globalValues.add(utils.allocateGlobal(globalName, NodeType.INT32, floatValue, String.valueOf(tmp)));
                    utils.initializeType(storedValue, NodeType.INT32);
                    result.add(utils.dereferenceVariable(storedValue));

                    storedValue = "%" + utils.getLastResult();
                    result.add(utils.bitcastIntToFloat(storedValue));
                    operand = "%" + globalName;
                    utils.initializeType(operand, type);
                }else {
                    continue;
                }
            }

            // Struct access
            else if(utils.hasMembers(side)) {
                result.addAll(llvmStructAccessMember(side));
                operand = "%" + utils.getLastResult();

            }

            // CHeck if its already loaded
            else if(vardata.isLoaded(operand)) {
                operand = "%" + vardata.getLoadedIndex(operand);
            }

            // If its a function variable, just attach the register marker
            else if(vardata.isFunctionVariable(operand)) {
                operand = operand;
            }

            // Compile if subnode needs it
            else if(utils.isArith(type) || utils.isLogical(type) || type == NodeType.FUNCTIONCALL) {
                result.addAll(compileLLVM(side));
                operand = "%" + utils.getLastResult();
            }

            else if(utils.hasMembers(side)) {

                String llvmType = vardata.getLlvmType(operand);
                String memberName = side.getSubnode(0).getValueString();
                NodeType subtype = vardata.getStructMemberType(llvmType, memberName);
                int pos = vardata.getStructMemberIndex(llvmType, memberName);

                List<String> structInfo = utils.accessMemberNode(operand, llvmType, memberName, pos, subtype);

                if(!vardata.isAllocatedRegister(structInfo.get(0))) {
                    result.add(structInfo.get(1));
                    vardata.allocateRegister(structInfo.get(0));
                }

                result.add(utils.dereferenceVariable(structInfo.get(0)));
                operand = "%" + utils.getLastResult();
            }

            // Load if needed
            else if(!vardata.isLoaded(operand) || type == NodeType.PTRDEREF) {

                // let x = alloc(...) -> x is an i64*, internally alloca i64* returns i64**
                // removing 1 indirection leaves us with i64*, so we need to dereference twice to
                // do something equivalent to *x (1st dereference from stack, 2nd dereference from heap)
                if(type == NodeType.PTRDEREF) {
                    operand = "%" + operand;
                    result.add(utils.dereferenceVariable(operand));
                    result.add(utils.dereferencePointer("%" + utils.getLastResult()));
                    operand = "%" + utils.getLastResult();
                }else {
                    result.add(utils.dereferenceVariable(operand));
                    operand = "%" + utils.getLastResult();
                }
            }

            type = vardata.getType(operand);

            operands[i] = operand;
            types[i] = type;
        }

        if(exprType == LLVMUtils.ARITHMETIC_EXPR) {
            result.addAll(utils.computeArith(operands[0], operands[1], types[0], types[1], operation));
        } else
            result.addAll(utils.computeLogic(operands[0], operands[1], types[0], types[1], operation));

        return result;
    }

    public List<String> llvmReturn(NASTNode node) {
        List<String> result = new ArrayList<>();
        NASTNode returnNode = node.getSubnode(0);
        NodeType returnType = returnNode.getType();
        String returnValue = returnNode.getValue().toString();
        returnFromCall = true; // By default, assume all function calls are stored

        // If it's a literal, return it
        if(utils.isLiteral(returnNode)) {
            result.add(utils.returnValue(returnValue, returnType));
        }

        // Variables
        else if(returnType == NodeType.VARIABLE) {
            if(scopedata.isGlobal(returnValue)) returnValue = "@global_" + returnValue;
            else returnValue = "%" + returnValue;

            returnType = vardata.getType(returnValue);
            String llvmReturnType = vardata.getLlvmType(returnValue);

            // If it's a function parameter, it's directly on the stack
            // Do not need to reload since its SSA
            if(vardata.isFunctionVariable(returnValue)) {
                result.add(utils.returnValue(returnValue, returnType));
            }

            // If it's a regular variable, need to dereference from stack
            // TODO add scoping checks

            else if(utils.hasMembers(returnNode)) {
                String varValue = "%" + returnNode.getValueString();
                String structType = vardata.getLlvmType(varValue);
                String member = returnNode.getSubnode(0).getValueString();
                int pos = vardata.getStructMemberIndex(structType, member);
                NodeType type = vardata.getStructMemberType(structType, member);

                List<String> structAccessList = utils.accessMemberNode(varValue, structType, member, pos, type);

                result.add(utils.dereferenceVariable(structAccessList.get(0)));
                result.add(utils.returnValue("%" + utils.getLastResult(), type));

            }

            else if(reloadReturnValue) {
                result.add(utils.dereferenceVariable(returnValue));
                returnValue = "%" + utils.getLastResult();
                result.add(utils.returnValue(returnValue, returnType));
            }

            else if(vardata.isLoaded(returnValue)) {
                result.add(utils.returnValue(returnValue, returnType));
            }

            else{
                result.add(utils.dereferenceVariable(returnValue));
                result.add(utils.returnValue("%" + utils.getLastResult(), llvmReturnType));
            }
        }

        // Otherwise, compile it first then return
        else {
            result.addAll(compileLLVM(returnNode));
            result.add(utils.returnValue("%" + utils.getLastResult()));
        }

        return result;
    }

    public List<String> llvmFunction(NASTNode node) {
        List<String> result = new ArrayList<>();
        String functionName = node.getValue().toString();
        NASTNode functionArgs = node.getSubnode(0);
        NASTNode functionReturn = node.getSubnode(1);
        NASTNode functionBody = node.getSubnode(2);

        currentFunction = functionName;

        // If function body is empty, delete it
        if(functionBody.getAllSubnodes().isEmpty()) {
            return new ArrayList<>();
        }


        NodeType returnType = functionReturn.getSubnode(0).getType();
        fundata.setReturnType(functionName, returnType);

        if(!functionArgs.getAllSubnodes().isEmpty()) {
            for (NASTNode arg : functionArgs.getAllSubnodes()) {
                String argName = arg.getValueString();
                NodeType argType = arg.getType();

                vardata.addFunctionVariable("%" + argName);
                utils.initializeType("%" + argName, argType);
                //fundata.addParameter(functionName, argType);
                scopedata.set(argName, ScopeData.SCOPE_LOCAL);
            }
        }

        result.add(utils.createFunction(functionName, LLVMUtils.FUNCTION_DEFINITION));

        if(utils.hasConditionals(node)) {
            result.add("entry:\n");
            utils.setCounter(0);
        }

        // Move to a lower scope depth
        scopedata.increaseDepth();


        for(NASTNode bodyNode : functionBody.getAllSubnodes()) {
            result.addAll(compileLLVM(bodyNode));
        }

        // Assumes the return type is void. Semantic analysis should catch non-void return errors
        if(returnType == NodeType.VOID) {
            result.add("\tret void\n");
        }

        // Exit function
        result.add("}\n");

        // Move back up
        scopedata.decreaseDepth();
        scopedata.removeHigherDepth(); // Remove all locals from the higher depth scope
        vardata.emptyFunctionVariables();
        vardata.removeLocals();
        vardata.emptyVarMap();
        utils.resetLoopDepth();

        // Reset register indices
        utils.setCounter(1);

        // Reset return register reloading
        reloadReturnValue = false;

        return result;
    }

    // TODO promotion (for variadic, float->double, int->long)
    public List<String> llvmFunctionCall(NASTNode node) {

        List<String> result = new ArrayList<>();

        String functionName = node.getValue().toString();

        if(fundata.getReturnType(functionName) == NodeType.VOID) {
            returnFromCall = false;
        }

        List<NASTNode> parameters = node.getAllSubnodes();

        List<String> parameterNames = new ArrayList<>();
        List<NodeType> parameterTypes = new ArrayList<>();
        List<String> parameterLlvmTypes = new ArrayList<>();

        int pos = 0;

        for(NASTNode arg : parameters) {

            NodeType type = arg.getType();
            String value = arg.getValueString();

            /*
            Need something like
            call type @name(type1 arg1, type2 arg2)

            to do that, we have to evaluate each arg separately

            arg is in the form (TYPE, VALUE)
            X strings     -> load to global, pull to local
            X funcall     -> compile, pull resulting register
            X arith       -> compile, pull resulting register
            X logic       -> compile, pull resulting register
            X literal     -> literal, promote if needed
            X variable    -> dereference, pull resulting register
            X dereference -> dereference, pull resulting register
            X addressof   -> store register, pull resulting register
            X fun param   -> pass register directly
            X global      -> load global, pull resulting register
            expr        -> compile, pull resulting register
             */

            // If it's a literal, add it directly
            // Promote if necessary
            if(utils.isLiteral(arg)) {
                NodeType paramType = fundata.getFunctionParameters().get(functionName).get(pos);
                paramType = Symbols.normalize(paramType);
                if(type != paramType) {
                    List<String> stype = utils.promoteVariadicValue(value, type);

                    // If a literal is promoted twice, reuse old register
                    if(!parameterNames.contains(stype.get(0)))
                        result.add(stype.get(2));

                    parameterNames.add(stype.get(0));
                    parameterTypes.add(paramType);
                    parameterLlvmTypes.add(stype.get(1));
                    value = stype.get(0);
                    type = paramType;
                }
                //if(fundata.getFunctionParameters().get(currentFunction).get(pos))
                else {
                    parameterNames.add(value);
                    parameterTypes.add(type);
                    parameterLlvmTypes.add(utils.nativeTypeToLLVM(type));
                }
            }

            // Strings must be loaded from the global scope
            // utils.loadString(value) will automatically load to a register
            else if(type == NodeType.STR) {
                List<String> stringLoad = utils.loadString(value);
                globalValues.add(stringLoad.get(0));    // 0 = global string
                result.add(stringLoad.get(1));          // 1 = load from global
                utils.initializeType("%.str_" + utils.getLastResult(), type);
                parameterNames.add("%.str_" + utils.getLastResult());
                parameterTypes.add(type);
                parameterLlvmTypes.add(utils.nativeTypeToLLVM(type));
            }

            // Expressions all need to be compiled first
            else if(type == NodeType.FUNCTIONCALL || utils.isArith(type) || utils.isLogical(type)) {
                result.addAll(compileLLVM(arg));
                parameterNames.add("%" + utils.getLastResult());
                parameterTypes.add(vardata.getType("%" + utils.getLastResult()));
                parameterLlvmTypes.add(utils.nativeTypeToLLVM(vardata.getType("%" + utils.getLastResult())));
            }

            // TODO double, triple, etc indirection
            else if(type == NodeType.FINDADDRESS) {

                value = arg.getSubnode(0).getValueString();

                if(scopedata.isGlobal(value)) value = "@global_" + value;
                else value = "%" + value;

                parameterNames.add(value);
                NodeType subtype = vardata.getType(value);

                if(!subtype.toString().contains("_STAR"))
                    subtype = NodeType.valueOf(subtype.toString().replace("_T", "") + "_STAR");
                parameterTypes.add(subtype);
                parameterLlvmTypes.add(vardata.getLlvmType(value) + "*");
            }

            else if(type == NodeType.PTRDEREF) {

                err.auto("PTRDEREF", value);

                value = arg.getSubnode(0).getValueString();
            }
            /*

                    else if(type == NodeType.FINDADDRESS) {
            String variable = value.getValueString();
            if(scopedata.isGlobal(variable)) variable = "@global_" + variable;
            else variable = "%" + variable;


            name = "%" + name;
            llvmType = vardata.getLlvmType(variable);

            String starType = vardata.getType(variable).toString();
            if(!starType.contains("_STAR"))
                type = NodeType.valueOf(vardata.getType(variable).toString().replace("_T", "") + "_STAR");

            result.add(utils.allocateSpace(name, llvmType + "*"));
            vardata.setType(name, type);
            vardata.setLlvmType(name, llvmType + "*");
            result.add(utils.storeToVariable(name,  variable, LLVMUtils.STORETYPE_GLOBALVAR));
        }

             */

            // TODO loading globals
            else if(type == NodeType.VARIABLE) {

                /*

                // TODO implement function pointer table

                if(fptrs.contains(name) {
                    name = manglednames(name)
                    utils.castFunctionToPointer(name, CAST_FUN_TO_PTR)
                    value = ptrvalue;
                }

                 */


                if(scopedata.isGlobal(value)) value = "@global_" + value;
                else value = "%" + value;

                if(utils.hasMembers(arg)) {
                    NASTNode structMember = arg.getSubnode(0);
                    String structType = vardata.getLlvmType(value);
                    String member = structMember.getValueString();

                    int memberPos = vardata.getStructMemberIndex(structType, member);
                    NodeType memberType = vardata.getStructMemberType(structType, member);

                    // Load if necessary
                    if(vardata.isLoaded(value)) value = "%" + vardata.getLoadedIndex(value);
                    else {
                        result.add(utils.dereferenceVariable(value));
                        value = "%" + utils.getLastResult();
                    }

                    //result.add(utils.extractMember(value, structType, memberPos, memberType.toString()));
                    result.addAll(llvmStructAccessMember(arg));
                    parameterNames.add("%" + utils.getLastResult());
                    parameterTypes.add(memberType);
                    parameterLlvmTypes.add(Symbols.nativeTypeToLLVM(memberType));
                }

                // If the variable is already loaded, use it again
                else if(vardata.isLoaded(value)) {
                    value = "%" + vardata.getLoadedIndex(value);
                    parameterNames.add(value);
                    parameterTypes.add(vardata.getType(value));
                    parameterLlvmTypes.add(vardata.getLlvmType(value));
                }

                // If the variable is a function parameter, it's already in a register
                else if(vardata.isFunctionVariable(value)) {
                    parameterNames.add(value);
                    parameterTypes.add(vardata.getType(value));
                    parameterLlvmTypes.add(vardata.getLlvmType(value));
                }

                // Load the variable
                else {
                    result.add(utils.dereferenceVariable(value));
                    parameterNames.add("%" + utils.getLastResult());
                    parameterTypes.add(vardata.getType("%" + utils.getLastResult()));
                    parameterLlvmTypes.add(vardata.getLlvmType("%" + utils.getLastResult()));
                }

            }

            // Load the last register as the parameter if it wasn't a literal value
            else if(!utils.isLiteral(arg)) {
                String idx = "%" + utils.getLastResult();
                parameterNames.add(idx);
                parameterTypes.add(vardata.getType(idx));
            }

            pos++;
        }

        result.add(utils.callFunction(
                functionName,
                parameterNames,
                parameterTypes,
                parameterLlvmTypes,
                returnFromCall,
                variadicFunctions.contains(functionName)));
        /*utils.initializeType("%" + utils.getLastResult(), fundata.getReturnType(functionName));
        if(fundata.hasLlvmType(functionName)) {
            vardata.setLlvmType("%" + utils.getLastResult(), fundata.getLlvmReturnType(functionName));
        }*/
        returnFromCall = true;
        return result;
    }

    public List<String> llvmAssign(NASTNode node) {
        List<String> result = new ArrayList<>();

        NASTNode variableNode = node.getSubnode(0);
        NASTNode valueNode = node.getSubnode(1);

        String varValue = variableNode.getValueString();
        boolean hasMembers = false;
        String member = "";
        // TODO clean up this hack
        if(utils.hasMembersS(varValue)) {
            hasMembers = true;
            member = varValue.substring(varValue.indexOf(".") + 1);
            varValue = varValue.substring(0, varValue.indexOf("."));
        }
        NodeType varType = vardata.getType(varValue);

        String valueValue = valueNode.getValueString();
        NodeType valueType = valueNode.getType();

        // TODO cleanup
        // Need to check for array access to get proper variable name first, then do dereferencing
        if(variableNode.getType() == NodeType.ARRAY_ACCESS) {
            varValue = variableNode.getSubnode(0).getValueString();
        }


        if(scopedata.isGlobal(varValue)) varValue = "@global_" + varValue;
        else varValue = "%" + varValue;

        if(variableNode.getType() == NodeType.ARRAY_ACCESS) {
            //String type = variableNode.getSubnode(0).getValueString();
            String type = Symbols.nativeTypeToLLVM(vardata.getArrayType(varValue));
            int size = vardata.getArraySize(varValue);
            String pos = variableNode.getSubnode(1).getValueString();

            if(!utils.isNumeric(pos)) {
                result.add(utils.dereferenceVariable("%" + pos));
                pos = "%" + utils.getLastResult();
            }

            result.add(utils.getArrayPtr(varValue, type, size, pos));
            varValue = "%" + utils.getLastResult();
        }

        // TODO set var type

        if(variableNode.getType() == NodeType.PTRDEREF) {
            String llvmtype = vardata.getLlvmType(varValue);

            if(hasMembers) {
                varValue = "%" + utils.getLastResult();
                String struct = vardata.getLlvmType(varValue);
                int pos = vardata.getStructMemberIndex(struct, member);
                NodeType memberType = vardata.getStructMemberType(struct, member);
                result.add(utils.extractMember(varValue, struct, pos, memberType.toString()));
                result.add(utils.dereferenceVariable("%" + utils.getLastResult()));
            }else{
                result.add(utils.dereferenceVariable(varValue));
            }

            varValue = "%" + utils.getLastResult();
            //vardata.setLlvmType(varValue, llvmtype.substring(0, llvmtype.length()-1));
        }

        if(utils.hasMembers(variableNode)) {
            String structType = vardata.getLlvmType(varValue);
            member = variableNode.getSubnode(0).getValueString();

            int pos = vardata.getStructMemberIndex(structType, member);
            NodeType type = vardata.getStructMemberType(structType, member);
            List<String> structAccessList = utils.accessMemberNode(varValue, structType, member, pos, type);

            if(!vardata.isAllocatedRegister(structAccessList.get(0))) {
                result.add(structAccessList.get(1)); // Member access
                vardata.allocateRegister(structAccessList.get(0));
            }
            varValue = structAccessList.get(0); // variable for struct access
            vardata.loadTo(varValue, -999);

        }

        // Move literal into variable
        if(utils.isLiteral(valueNode)) {
            String storedValue = valueNode.getValueString();
            result.add(utils.storeToVariable(varValue, storedValue, LLVMUtils.STORETYPE_LITERAL));
        }

        // TODO Arrays

        // Register-based assignment always comes from needing further evaluation
        else if(valueType == NodeType.FUNCTIONCALL || utils.isArith(valueType) || utils.isLogical(valueType)) {
            result.addAll(compileLLVM(valueNode));
            result.add(utils.storeToVariable(varValue, utils.getLastResult(), LLVMUtils.STORETYPE_REGISTER));
        }

        // Copying variable or dereference
        // Result of dereference stored in the current counter
        else if(valueType == NodeType.VARIABLE || valueType == NodeType.PTRDEREF) {
            // TODO if the variable is already loaded somewhere, store that reference before trying to reload

            if(scopedata.isGlobal(valueValue)) valueValue = "@global_" + valueValue;
            else valueValue = "%" + valueValue;

            result.add(utils.dereferenceVariable(valueValue));
            utils.initializeType("%" + utils.getLastResult(), vardata.getType(varValue));
            result.add(utils.storeToVariable(varValue, utils.getLastResult(), LLVMUtils.STORETYPE_VARIABLE));
        }

        else if(valueType == NodeType.FINDADDRESS) {
            result.add(utils.storeToPointer("%" + utils.getLastResult(), valueValue, LLVMUtils.STORETYPE_VARIABLE));
        }
        return result;
    }

    public List<String> llvmVardefPrep(NASTNode node) {
        List<String> result = new ArrayList<>();
        boolean extract = false;

        // TODO scoping
        // TODO set var type

        /*

            name = variable name

            type = variable type
            The type will change since it can't be determined yet.
            Right now the type could either be a literal, function call,
            arith/logic operation, dereference, etc.

            llvmType = the LLVM equivalent of "type"

            value = subnode 0, which contains the data to store

            cast = INVALID by default. If one exists, then the type is determined

         */

        String name = node.getValueString();
        String llvmType;
        NASTNode value = node.getSubnode(0);
        NodeType type = value.getType();
        NodeType cast = NodeType.INVALID;
        boolean hasCast = false;
        returnFromCall = true; // If a function is called, it will get stored


        if(type == NodeType.CAST) hasCast = true;

        if(hasCast) {
            // If we only have the cast, it's just for space
            if(node.getAllSubnodes().size() == 1) {
                if(value.getValue().toString().equals("STRUCT")) {
                    String typeName = value.getSubnode(0).getValueString();
                    result.add(utils.allocateSpace("%" + name, "%" + typeName));
                    vardata.setLlvmType("%" + name, "%" + typeName);
                }else {
                    result.add(utils.allocateSpace("%" + name, (NodeType) value.getValue()));
                    vardata.setLlvmType("%" + name, utils.nativeTypeToLLVM((NodeType) value.getValue()));
                }

                // Return immediately since only space is requested
                scopedata.set(name, scopedata.getDepth());
                return result;
            }

            // Otherwise, modify type
            else {
                cast = (NodeType) node.getSubnode(0).getValue();
                type = cast;
                value = node.getSubnode(1);
                NodeType valueType = value.getType();
                Object subValue = value.getValue();

                if(Symbols.isInteger(cast) && Symbols.isFloatingPoint(valueType)) {
                    long truncValue = utils.floatToLong(Double.parseDouble(subValue.toString()));
                    node.getSubnode(1).setValue(truncValue);
                }

                else if(cast == NodeType.INT32_T && valueType == NodeType.INT64_T) {
                    node.getSubnode(1).setValue(utils.longToInt(Long.parseLong(subValue.toString())));
                }

            }
        }

        // Set the current scope for the variable
        scopedata.set(name, scopedata.getDepth());

        final boolean isArithOrLogic = utils.isArith(type) || utils.isLogical(type);
        final boolean isFunctionCall = type == NodeType.FUNCTIONCALL;

        if(utils.isLiteral(value)) {

            // TODO cleanup
            String storedValue = value.getValueString();
            if(scopedata.getDepth() != ScopeData.SCOPE_GLOBAL) {
                String floatval = "";
                if (type == NodeType.FP32) {
                    float tmp = Float.parseFloat(storedValue);
                    floatval = storedValue;
                    storedValue = String.valueOf(Float.floatToRawIntBits(tmp)); //utils.floatToHex(tmp);
                    String globalName = utils.bitcastVariableI32ToF32();
                    globalValues.add(utils.allocateGlobal(globalName, NodeType.INT32, storedValue, floatval));
                    storedValue = "@global_" + globalName;
                    utils.initializeType(storedValue, NodeType.INT32);
                    result.add(utils.dereferenceVariable(storedValue));
                    storedValue = "%" + utils.getLastResult();
                    result.add(utils.bitcastIntToFloat(storedValue));
                    storedValue = "%" + globalName;
                } else if (type == NodeType.FP64) {
                    double tmp = Double.parseDouble(storedValue);
                    floatval = storedValue;
                    storedValue = utils.doubleToHex(tmp);
                }
            }

            // Now store the data
            if(scopedata.getDepth() == ScopeData.SCOPE_GLOBAL) {
                globalValues.add(utils.allocateGlobal(name, type, storedValue, value.getValueString()));
                utils.initializeType("@global_" + name, type);
            }else {
                name = "%" + name;
                // TODO scope allocation
                result.add(utils.allocateSpace(name, type));
                utils.initializeType(name, type);
                result.add(utils.storeToVariable(name, storedValue, LLVMUtils.STORETYPE_LITERAL));
            }
        }

        /*else if(type == NodeType.ARRAY) {
            name = "%" + name;

            NodeType arrayType = (NodeType) value.getValue();
            utils.initializeType(name, arrayType);
            result.add(utils.allocateArray(name, value));
        }*/

        else if(type == NodeType.ARRAY) {
            name = "%" + name;

            NodeType arrayType = (NodeType) value.getValue();
            int arraySize = (int) value.getSubnode(0).getValue();
            utils.initializeType(name, arrayType);
            vardata.createArray(name, arraySize, arrayType);
            result.add(utils.allocateArray(name, value));
        }

        else if(type == NodeType.ARRAY_ACCESS) {

            /*
                -- TNODE(ARRAY_ACCESS, VOID)
                    -- TNODE(ARRAY, x)
                    -- TNODE(ARRAY_INDEX, 0)
             */
            name = "%" + name;
            String arrayVariable = "%" + value.getSubnode(0).getValueString();
            NodeType arrayType = vardata.getArrayType(arrayVariable);
            int size = vardata.getArraySize(arrayVariable); // TODO change array size
            String pos = value.getSubnode(1).getValueString();

            if(!utils.isNumeric(pos)) {
                result.add(utils.dereferenceVariable("%" + pos));
                pos = "%" + utils.getLastResult();
            }

            String nat = Symbols.nativeTypeToLLVM(arrayType);

            utils.initializeType(name, arrayType);

            result.add(utils.allocateSpace(name, arrayType));


            result.add(utils.getArrayPtr(arrayVariable, nat, size, pos));
            result.add(utils.loadFromArrayPtr("%" + utils.getLastResult(), arrayVariable, utils.nativeTypeToLLVM(arrayType)));
            result.add(utils.storeToVariable(name, "%" + utils.getLastResult(), LLVMUtils.STORETYPE_STRING));
        }

        else if(type == NodeType.STR) {
            name = "%" + name;

            // Load the string and store the register in the variable
            utils.initializeType(name, type);

            result.add(utils.allocateSpace(name, type));

            List<String> stringLoad = utils.loadString(value.getValueString());
            globalValues.add(stringLoad.get(0));
            result.add(stringLoad.get(1));
            result.add(utils.storeToVariable(name, "%.str_" + utils.getLastResult(), LLVMUtils.STORETYPE_STRING));
            utils.initializeType("%" + name, NodeType.STR);
        }

        else if(isArithOrLogic || isFunctionCall) {
            name = "%" + name;

            // TODO doesn't work for struct access
            if(isArithOrLogic) {
                /*if(utils.hasMembers(value)) {
                    resu
                    lt.addAll(llvmExpr(value, LLVMUtils.ARITHMETIC_EXPR));
                    //result.addAll(dumpAllStructMembers(value, true));
                    //System.out.println(value);
                }*/
                type = utils.arithmeticPrecedence(value, vardata.getTypeMap(), fundata.getReturnMap());
            }
            else type = fundata.getReturnType(value.getValueString());


            if(fundata.hasLlvmType(value.getValueString())) {
                String llvm = fundata.getLlvmReturnType(value.getValueString());
                result.add(utils.allocateSpace(name, llvm));
            }else {
                result.add(utils.allocateSpace(name, type));
            }

            // Type is now available from either scanning the expression tree or checking function return type
            result.addAll(compileLLVM(value));
            utils.initializeType(name, type);
            utils.initializeType("%" + utils.getLastResult(), type);
            vardata.loadTo(name, utils.getLastResult());
            result.add(utils.storeToVariable(name, utils.getLastResult(), LLVMUtils.STORETYPE_REGISTER));
        }

        // TODO trace chain to find type
        else if(type == NodeType.FINDADDRESS) {
            /*String variable = value.getValueString();
            if(scopedata.isGlobal(variable)) variable = "@global_" + variable;
            else variable = "%" + variable;


            name = "%" + name;
            llvmType = vardata.getLlvmType(variable);

            type = vardata.getType(variable);
            String starType = type.toString();
            if(!starType.contains("_STAR"))
                type = NodeType.valueOf(vardata.getType(variable).toString().replace("_T", "") + "_STAR");

            result.add(utils.allocateSpace(name, llvmType + "*"));
            vardata.setType(name, type);
            vardata.setLlvmType(name, llvmType + "*");
            result.add(utils.storeToVariable(name,  variable, LLVMUtils.STORETYPE_GLOBALVAR));
        */
            result.addAll(handleMemoryAccess(value));

        }


        // TODO storing a dereference
        else if(type == NodeType.PTRDEREF) {
            /*String variable = value.getValueString();

            if(scopedata.isGlobal(variable)) variable = "@global_" + variable;
            else variable = "%" + variable;

            name = "%" + name;
            llvmType = vardata.getLlvmType(variable);

            // Ensure that removing one level of indirection doesn't accidentally mess up the type name
            long starCount = llvmType.chars().filter(c -> c == '*').count();
            if(starCount >= 1) {
                llvmType = llvmType.substring(0, llvmType.length() - 1);
            }

            result.add(utils.allocateSpace(name, llvmType));
            vardata.setType(name, type);
            vardata.setLlvmType(name, llvmType);
            result.add(utils.dereferenceVariable(variable));
            for(int i = 0; i < starCount; i++) {
                result.add(utils.dereferencePointer("%" + utils.getLastResult()));
            }
            result.add(utils.storeToVariable(name,  utils.getLastResult(), LLVMUtils.STORETYPE_REGISTER));

             */
            result.addAll(handleMemoryAccess(value));
        }

        else if(type == NodeType.VARIABLE) {
            name = "%" + name;

            String otherName = "%" + value.getValueString();

            // Struct member access
            if(utils.hasMembers(value)) {
                NASTNode structMember = value.getSubnode(0);
                String structType = vardata.getLlvmType(otherName);
                String member = structMember.getValueString();
                int pos = vardata.getStructMemberIndex(structType, member);

                type = vardata.getStructMemberType(structType, member);
                llvmType = utils.nativeTypeToLLVM(type);

                result.add(utils.dereferenceVariable(otherName));
                result.addAll(llvmStructAccessMember(value));
                extract = true;
                otherName = "%" + utils.getLastResult();

                vardata.setType(otherName, type);
                vardata.setLlvmType(otherName, llvmType);

                /*List<String> memberAccess = utils.accessMemberNode(otherName, structType, member, pos, type);
                otherName = memberAccess.get(0);
                if(!vardata.isLoaded(otherName)) {
                    result.add(memberAccess.get(1));
                }*/
            } else {
                if(scopedata.isGlobal(otherName.replace("%", ""))) {
                    otherName = "@global_" + otherName.replace("%", "");
                }
                type = vardata.getType(otherName);
                llvmType = vardata.getLlvmType(otherName);
            }

            vardata.setType(name, type);
            vardata.setLlvmType(name, llvmType);

            result.add(utils.allocateSpace(name, type));
            if (fundata.isNamedParam(currentFunction, otherName)) {
                result.add(utils.storeToVariable(name, otherName, LLVMUtils.STORETYPE_COPYFUNREGISTER));
            } else if(extract) {
                result.add(utils.storeToVariable(name, utils.getLastResult(), LLVMUtils.STORETYPE_REGISTER));
            } else {
                result.add(utils.dereferenceVariable(otherName));
                result.add(utils.storeToVariable(name, utils.getLastResult(), LLVMUtils.STORETYPE_REGISTER));
            }
        }

        //vardata.setType(name, type);
        //vardata.setLlvmType(name, utils.nativeTypeToLLVM(type));

        return result;
    }

    /*

        loop_condition0:
                %1 = load i64, i64* %i
                %2 = icmp slt i64 %1, %b
                br i1 %2, label %loop_body0, label %loop_exit0
        loop_body0:
                %3 = load i64, i64* %x
                %4 = mul i64 %3, %3
                store i64 %4, i64* %x
                %bcast_arith_5_from_1 = sext i32 1 to i64
                %5 = add i64 %1, %bcast_arith_5_from_1
                store i64 %5, i64* %i
                br label %loop_condition0
        loop_exit0:
                ret i64 %4
        }

        If the loop isn't taken, it tries to return %4 but it isnt assigned yet
        from pow.nv

        TODO set value to reload variables inside loops/conditionals

     */


    public List<String> llvmForLoop(NASTNode node) {
        reloadReturnValue = true;
        List<String> result = new ArrayList<>();

        NASTNode loopVariable = node.getSubnode(0).getSubnode(0);
        NASTNode loopCondition = node.getSubnode(1).getSubnode(0);
        NASTNode loopIteration = node.getSubnode(2).getSubnode(0);
        NASTNode loopBodyNodes = node.getSubnode(3);

        String loopcheck = utils.loopcheck();
        String loopbody = utils.loopbody();
        String loopexit = utils.loopexit();
        utils.increaseLoopDepth(); // For keeping track of nested loops


        // TODO type could be a function, arithmetic, etc. compile it first. Assumed to always be integer right now
        String variableName = "%" + loopVariable.getValueString();
        NodeType variableType = loopVariable.getSubnode(0).getType();


        // Pre-loop code
        result.addAll(compileLLVM(loopVariable));
        result.add(utils.jumpDirectly(loopcheck));

        scopedata.increaseDepth();
        // Loop condition
        result.add(loopcheck + ":\n");
        result.addAll(compileLLVM(loopCondition));
        result.add(utils.jumpConditional(loopbody, loopexit, utils.getLastResult()));

        // Create body code then add the iteration at the end
        result.add(loopbody + ":\n");
        for(NASTNode subnode : loopBodyNodes.getAllSubnodes()) {
            result.addAll(compileLLVM(subnode));
        }
        result.addAll(compileLLVM(loopIteration));
        result.add(utils.storeToVariable(variableName, utils.getLastResult(), LLVMUtils.STORETYPE_REGISTER));
        result.add(utils.jumpDirectly(loopcheck));

        // Loop exit
        result.add(loopexit + ":\n");
        scopedata.decreaseDepth();
        //utils.decreaseLoopDepth(); // Move up a layer
        return result;
    }

    private List<String> llvmStructAccessMember(NASTNode parent) {

        /*
        TNODE(VARIABLE, A)
        -- TNODE(MEMBER, B)
         */

        // TODO recursive descent to access multiple members in sequence like in var.mem1.mem2
        // Struct data
        String variable = "%" + parent.getValueString();
        String struct = vardata.getLlvmType(variable);

        // Member data
        NASTNode memberNode = parent.getSubnode(0);
        String memberName = memberNode.getValueString();
        NodeType memberType = vardata.getStructMemberType(struct, memberName);
        int pos = vardata.getStructMemberIndex(struct, memberName);

        List<String> result = new ArrayList<>();

        // Load if necessary
        if(!vardata.isLoaded(variable)) {
            result.add(utils.dereferenceVariable(variable));
            variable = "%" + utils.getLastResult();
        }else{
            variable = "%" + vardata.getLoadedIndex(variable);
        }

        /*

            variable    - Variable to access member from
            struct      - The type of struct
            pos         - Position of the member in the struct
            aux         - Used for comments in ir (the type of the member)

         */
        result.add(utils.extractMember(variable, struct, pos, memberType.toString()));

        return result;
    }

    private List<String> dumpAllStructMembers(NASTNode head, boolean typeCast) {
        List<String> result = new ArrayList<>();

        if(!head.getAllSubnodes().isEmpty()) {
            for(NASTNode node : head.getAllSubnodes()) {
                if(node.getType() == NodeType.VARIABLE && utils.hasMembers(node)) {
                    result.addAll(llvmStructAccessMember(node));
                    if(typeCast) {
                        String variable = "%" + node.getValueString();
                        String structType = vardata.getLlvmType(variable);
                        String structMember = node.getSubnode(0).getValueString();
                        NodeType type = vardata.getStructMemberType(structType, structMember);
                        node.getSubnode(0).setType(type);
                    }
                }else{
                    result.addAll(dumpAllStructMembers(node, typeCast));
                }
            }
        }

        return result;
    }

    /*
        -- TNODE(VARDEF, a)
            -- TNODE(PTRDEREF, VOID)
                -- TNODE(PTRDEREF, VOID)
                    -- TNODE(VARIABLE, z)


            if ptrderef
                compute subnode
                store in list
                dereference last result
     */

    private List<String> handleMemoryAccess(NASTNode node) {
        List<String> result = new ArrayList<>();
        Stack<NodeType> operationStack = new Stack<>();
        NodeType type = node.getType();

        boolean variable = true;

        while(type == NodeType.PTRDEREF || type == NodeType.FINDADDRESS) {
            operationStack.push(type);
            node = node.getSubnode(0);
            type = node.getType();
        }

        // If we have PTRDEREF(ADD(x, 1)), need to compile the add and deref the resulting register
        if(node.getType() != NodeType.VARIABLE) {
            result.addAll(compileLLVM(node));
            variable = false;
        }

        String operand = variable ? "%" + node.getValueString() : "%" + utils.getLastResult();

        while(!operationStack.isEmpty()) {
            NodeType top = operationStack.pop();
            switch (top) {
                case PTRDEREF -> {
                    if(variable) result.add(utils.dereferenceVariable(operand));
                    else result.add(utils.dereferencePointer(operand));
                }
                case FINDADDRESS -> {
                    if(variable) result.add(utils.getAddressInRegister(operand));
                    else result.add(utils.getAddressInRegister(operand));
                }
                default -> result.addAll(compileLLVM(node));
            }
        }

        return result;
    }
}