package nvyc.data;

public enum ErrorType {
    // --- General syntax errors ---
    MISSING_SEMICOLON("Expected semicolon, but didn't find one"),
    UNEXPECTED_TOKEN("Unexpected or misplaced token"),
    UNTERMINATED_STRING("String literal missing closing quote"),
    UNTERMINATED_COMMENT("Block comment not properly closed"),
    INVALID_NUMBER_LITERAL("Malformed numeric constant"),
    INVALID_IDENTIFIER("Identifier contains illegal characters"),
    EXPECTED_IDENTIFIER("Expected variable or function name"),
    EXPECTED_EXPRESSION("Expected expression but found none"),
    EXPECTED_TYPE("Missing or invalid type name"),
    EXPECTED_EQUALS("Expected '=' in assignment"),
    EXPECTED_PAREN_OPEN("Expected '('"),
    EXPECTED_PAREN_CLOSE("Expected ')'"),
    EXPECTED_BRACE_OPEN("Expected '{'"),
    EXPECTED_BRACE_CLOSE("Expected '}'"),
    EXPECTED_BRACKET_OPEN("Expected '['"),
    EXPECTED_BRACKET_CLOSE("Expected ']'"),
    UNEXPECTED_EOF("Unexpected end of file"),

    // --- Declarations and scope ---
    REDECLARATION("Variable or function redeclared"),
    UNDECLARED_IDENTIFIER("Use of undeclared variable or function"),
    TYPE_MISMATCH("Type mismatch in assignment or expression"),
    INVALID_ASSIGNMENT_TARGET("Cannot assign to this expression"),
    MISSING_RETURN_TYPE("Function missing return type"),
    MISSING_PARAMETER_TYPE("Parameter missing type annotation"),
    WRONG_NUMBER_OF_ARGUMENTS("Incorrect number of arguments in call"),

    // --- Control flow ---
    EXPECTED_CONDITION("Missing condition in if/while"),
    EXPECTED_BLOCK("Expected code block after control statement"),
    BREAK_OUTSIDE_LOOP("Break used outside of loop"),
    CONTINUE_OUTSIDE_LOOP("Continue used outside of loop"),
    RETURN_OUTSIDE_FUNCTION("Return used outside of function"),

    // --- Pointers / references ---
    INVALID_DEREFERENCE("Attempted to dereference non-pointer"),
    INVALID_ADDRESS_OF("Invalid use of address-of operator"),
    TYPE_DEPTH_MISMATCH("Pointer/reference depth mismatch"),
    NULL_REFERENCE_ACCESS("Dereferencing null reference"),

    // --- Miscellaneous ---
    INVALID_OPERATOR_USAGE("Invalid operator usage for operand types"),
    DIVISION_BY_ZERO("Division by zero detected"),
    EXPECTED_COMMA("Missing comma in list or arguments"),
    EXTRA_TOKEN_AFTER_STATEMENT("Unexpected token after valid statement"),
    INVALID_RETURN_VALUE("Invalid return value or type"),
    INVALID_CAST("Invalid or unsafe type cast"),

    // --- Arithmetic ---
    ARITHMETIC_ERROR(""),

    // Safety
    VARIABLE_DOUBLE_REFERENCE("Variables may not have two mutable references"),
    POINTER_LITERAL_ASSIGNMENT("Cannot assign literal value to a pointer type"),

    // --- Internal / catch-all ---
    UNKNOWN_ERROR("Unclassified or unknown error");

    // --- Implementation ---
    private final String message;

    ErrorType(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return name() + ": " + message;
    }
}
