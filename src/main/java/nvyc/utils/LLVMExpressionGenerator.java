package nvyc.utils;

import nvyc.data.*;

import java.util.ArrayList;
import java.util.List;

public class LLVMExpressionGenerator {

    public static final int OPTION_VARDEF = 0;
    public static final int OPTION_ASSIGN = 1;
    public static final int OPTION_ENCLOSED_EXPR = 2;

    private static final VariableData vardata = VariableData.getInstance();
    private static final FunctionData fundata = FunctionData.getInstance();
    private static final ScopeData scopedata = ScopeData.getInstance();
    private static final LLVMUtils utils = new LLVMUtils();


    public List<String> generate(NASTNode node, int option) {
        List<String> result = new ArrayList<>();
        NodeType type = node.getType();
        String name = null;

        final boolean isArithOrLogic = Symbols.isArithmetic(type) || Symbols.isLogical(type);
        final boolean isFunctionCall = type == NodeType.FUNCTIONCALL;


        /*
            -- TNODE(VARDEF, x)
                -- TNODE(ARRAY, INT32_T)
                    -- TNODE(INT32, 5)
         */
        if(type == NodeType.ARRAY) {
            name = "%" + node.getValueString();
            NASTNode info = node.getSubnode(0);
            NodeType arrayType = (NodeType) info.getValue();
            int arraySize = (int) info.getSubnode(0).getValue();

            vardata.createArray(name, arraySize, arrayType);
            vardata.initializeNativeType(name, NodeType.ARRAY);
            result.add(utils.allocateArray(name, info));
        }

        /*
                -- TNODE(ARRAY_ACCESS, VOID)
                    -- TNODE(ARRAY, x)
                    -- TNODE(ARRAY_INDEX, 0)
         */
        else if(type == NodeType.ARRAY_ACCESS) {
            name = "%" + node.getSubnode(0).getValueString();
            String index = node.getSubnode(1).getValueString();

            // Could return Tuple type instead
            NodeType arrayType = vardata.getArrayType(name);
            int arraySize = vardata.getArraySize(name);

            // Index will either be an integer string or a variable.
            // If it isn't a number, dereference the variable to get the integer
            // TODO this assumes the variable is an integer. Type checking has not been implemented yet
            if(!utils.isNumeric(index)) {
                result.add(utils.dereferenceVariable("%" + index));
                index = "%" + utils.getLastResult();
            }

            String nativeType = Symbols.nativeTypeToLLVM(arrayType);
            utils.initializeType(name, arrayType);

            // Allocate space for variable if we're doing something like "let x = arr[y]"
            if(option == OPTION_VARDEF) {
                result.add(utils.allocateSpace(name, arrayType));
            }

            // Universal for any array access
            result.add(utils.getArrayPtr(name, nativeType, arraySize, index));
            result.add(utils.loadFromArrayPtr("%" + utils.getLastResult(), name, nativeType));

            // Store value into variable if needed
            if(option == OPTION_VARDEF) {
                result.add(utils.storeToVariable(name, "%" + utils.getLastResult(), LLVMUtils.STORETYPE_STRING));
            }

        }

        else if(type == NodeType.STR) {

            // For now, prevent reassignment of strings and throw error for debugging
            if(option != OPTION_VARDEF) {
                throw new IllegalStateException("Cannot call assign on a string! At node\n" + node);
            }

            name = "%" + node.getValueString();
            utils.initializeType(name, type);
            result.add(utils.allocateSpace(name, type));

            NASTNode value = node.getSubnode(0);
            List<String> stringData = utils.loadString(value.getValueString());
            // TODO globalValues reference call
            // globalValues.add(stringLoad.get(0));
            result.add(stringData.get(1));
            result.add(utils.storeToVariable(name, "%.str_" + utils.getLastResult(), LLVMUtils.STORETYPE_STRING));
            utils.initializeType(name, type);
        }

        else if(isArithOrLogic || isFunctionCall) {

            // Arithmetic, logic, and function calls should never be on the LHS
            // unless inside a higher priority expression like arr[1+x] = ...
            // This is for debugging until proper error checking/handling is implemented
            if(option != OPTION_VARDEF) {
                throw new IllegalStateException("Cannot assign to an arithmetic/logical/function call expression! At node\n" + node);
            }

            name = "%" + node.getValueString();

            NodeType precedenceType;
            NASTNode value = node.getSubnode(0);

            if(isArithOrLogic) {
                // TODO Don't need to pass types and return types directly anymore
                precedenceType = utils.arithmeticPrecedence(value, vardata.getTypeMap(), fundata.getReturnMap());

                // Arith ops can't have custom types (yet) so no need to check for llvm type
                result.add(utils.allocateSpace(name, precedenceType));
                utils.initializeType(name, precedenceType);
            }

            else {
                String functionName = value.getValueString();
                precedenceType = fundata.getReturnType(functionName);
                if(fundata.hasLlvmType(functionName)) {
                    String llvmType = fundata.getLlvmReturnType(functionName);
                    result.add(utils.allocateSpace(name, llvmType));
                    vardata.setLlvmType(name, llvmType);
                } else {
                    result.add(utils.allocateSpace(name, precedenceType));
                    utils.initializeType(name, precedenceType);
                }
            }

            // TODO call to compileLLVM
            // result.addAll(compileLLVM(value));
            utils.initializeType("%" + utils.getLastResult(), precedenceType);
            vardata.loadTo(name, utils.getLastResult());
            result.add(utils.storeToVariable(name, utils.getLastResult(), LLVMUtils.STORETYPE_REGISTER));
        }


        return result;
    }
}
