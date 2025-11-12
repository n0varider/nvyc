package nvyc.generation;

import nvyc.data.NodeStream;
import nvyc.data.NodeType;

import java.util.*;

public class Lexer {

    private Map<String, NodeType> rep = new HashMap<>();
    private static Lexer lex;

    public static Lexer getInstance() {
        if (lex == null) {
            lex = new Lexer();
            lex.init();
        }
        return lex;
    }

    public void init() {
        // Keywords
        rep.put("let", NodeType.VARDEF);
        rep.put("true", NodeType.BOOL_T);
        rep.put("false", NodeType.BOOL_FA);
        //rep.put("%import", NodeType.DIRIMPORT);

        // Symbols
        rep.put("+", NodeType.ADD);
        rep.put("-", NodeType.SUB);
        rep.put("/", NodeType.DIV);
        rep.put("*", NodeType.MUL);
        // rep.put("<<", NodeType.ARITHLEFTSHIFT);
        // rep.put(">>", NodeType.ARITHRIGHTSHIFT);
        // rep.put(">>>", NodeType.LOGICRIGHTSHIFT);
        rep.put("~", NodeType.BITNEGATE);
        rep.put("&", NodeType.BITAND);
        rep.put("|", NodeType.BITOR);
        rep.put("^", NodeType.BITXOR);
        rep.put(">", NodeType.GT);
        rep.put("<", NodeType.LT);
        // rep.put("==", NodeType.EQ);
        // rep.put("<=", NodeType.LTE);
        // rep.put(">=", NodeType.GTE);
        // rep.put("!=", NodeType.NEQ);
        // rep.put("->", NodeType.RETTYPE);
        rep.put("func", NodeType.FUNCTION);
        // rep.put("||", NodeType.LOGICOR);
        // rep.put("&&", NodeType.LOGICAND);
        // rep.put("^^", NodeType.LOGICXOR);
        rep.put("!", NodeType.NOT);
        // rep.put("|>", NodeType.FUNCTIONCHAIN);
        // rep.put("//", NodeType.COMMENT);
        // rep.put("/*", NodeType.MLCOMMENTSTART);
        // rep.put("*/", NodeType.MLCOMMENTEND);
        rep.put("?", NodeType.TERNARY);
        rep.put(".", NodeType.ATTRIB);

        // Conditionals
        rep.put("if", NodeType.IF);
        rep.put("else", NodeType.ELSE);
        rep.put("switch", NodeType.SWITCH);
        rep.put("case", NodeType.CASE);
        rep.put("return", NodeType.RETURN);
        rep.put("for", NodeType.FORLOOP);
        rep.put("while", NodeType.WHILELOOP);

        // Types
        rep.put("int32", NodeType.INT32_T);
        rep.put("int64", NodeType.INT64_T);
        rep.put("unsigned", NodeType.UNSIGNED);
        rep.put("fp32", NodeType.FP32_T);
        rep.put("fp64", NodeType.FP64_T);
        rep.put("string", NodeType.STR_T);
        rep.put("char", NodeType.CHAR_T);
        rep.put("bool", NodeType.BOOL_T);
        rep.put("type", NodeType.TYPE_T);
        rep.put("short", NodeType.SHORT);
        rep.put("numeric32", NodeType.NUM32);
        rep.put("numeric64", NodeType.NUM64);
        rep.put("unified", NodeType.UNIFIED);
        rep.put("function", NodeType.FUNCTION_T);
        rep.put("void", NodeType.VOID);

        // Modifiers
        rep.put("final", NodeType.FINAL);
        rep.put("static", NodeType.STATIC);
        rep.put("public", NodeType.PUBLIC);
        rep.put("private", NodeType.PRIVATE);
        rep.put("impl", NodeType.IMPLICIT);
        rep.put("constant", NodeType.CONSTANT);
        rep.put("native", NodeType.NATIVE);
        rep.put("ref", NodeType.FINDADDRESS);
        rep.put("struct", NodeType.STRUCT);

        // Delimiters
        rep.put("(", NodeType.OPENPARENS);
        rep.put(")", NodeType.CLOSEPARENS);
        rep.put("=", NodeType.ASSIGN);
        rep.put(";", NodeType.ENDOFLINE);
        rep.put(",", NodeType.COMMADELIMIT);
        rep.put("[", NodeType.OPENBRKT);
        rep.put("]", NodeType.CLOSEBRKT);
        rep.put("\"", NodeType.DQUOTE);
        rep.put("'", NodeType.SQUOTE);
        rep.put("{", NodeType.OPENBRACE);
        rep.put("}", NodeType.CLOSEBRACE);
        rep.put("\\", NodeType.BSLASH);
    }

    public NodeStream lex(List<String> lines) {
        NodeStream head = new NodeStream(NodeType.PROGRAM, 0);
        NodeStream sentinel = head;
        NodeStream c;
        int lineNumber = 0;

        for (String line : lines) {
            int i = 0;
            while (i < line.length()) {
                char ch = line.charAt(i);

                // Skip whitespace
                if (Character.isWhitespace(ch)) {
                    i++;
                    continue;
                }

                // Symbols
                String sym = String.valueOf(ch);
                if (rep.containsKey(sym)) {
                    NodeType type = rep.get(sym);

                    if (type == NodeType.DQUOTE) { // string literal
                        StringBuilder sb = new StringBuilder();
                        sb.append(ch);
                        i++;
                        while (i < line.length() && line.charAt(i) != '"') {
                            sb.append(line.charAt(i));
                            i++;
                        }
                        if (i < line.length()) sb.append('"');
                        i++;
                        c = new NodeStream(NodeType.STR, sb.toString());
                        c.setLine(lineNumber);
                    } else if (type == NodeType.SQUOTE) { // char literal
                        StringBuilder sb = new StringBuilder();
                        sb.append(ch);
                        i++;
                        while (i < line.length() && line.charAt(i) != '\'') {
                            sb.append(line.charAt(i));
                            i++;
                        }
                        if (i < line.length()) sb.append('\'');
                        i++;
                        c = new NodeStream(NodeType.CHAR, sb.toString());
                        c.setLine(lineNumber);
                    } else {
                        c = new NodeStream(type, sym);
                        c.setLine(lineNumber);
                        i++;
                    }

                    head.setNext(c);
                    c.setPrev(head);
                    head = head.next();
                    continue;
                }

                // Numbers or identifiers
                if (Character.isLetterOrDigit(ch) || ch == '_') {
                    StringBuilder sb = new StringBuilder();
                    while (i < line.length() &&
                            (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_' || line.charAt(i) == '.')) {
                        sb.append(line.charAt(i));
                        i++;
                    }
                    String token = sb.toString();
                    NodeType type = rep.getOrDefault(token, nativeType(token));

                    if(NUMERICS.contains(type)) {
                        token = token
                                .replace("L", "")
                                .replace("F", "")
                                .replace("D", "")
                                .replace("_", "");
                    }

                    c = new NodeStream(type, token);
                    c.setLine(lineNumber);
                    head.setNext(c);
                    c.setPrev(head);
                    head = head.next();
                    continue;
                }

                // Unknown fallback
                c = new NodeStream(NodeType.VARIABLE, String.valueOf(ch));
                c.setLine(lineNumber);
                head.setNext(c);
                c.setPrev(head);
                head = head.next();
                i++;
            }
            lineNumber++;
        }

        c = new NodeStream(NodeType.ENDOFSTREAM, NodeType.VOID);
        c.setPrev(head);
        head.setNext(c);

        return sentinel;
    }


    private static final Set<NodeType> NUMERICS = Set.of(NodeType.INT32, NodeType.INT64, NodeType.FP32, NodeType.FP64);

    public NodeType nativeType(String s) {
        try {
            Integer.parseInt(s);
            return NodeType.INT32;
        }catch(NumberFormatException e) {
            try {
                if(s.endsWith("L")) {
                    Long.parseLong(s.substring(0, s.length()-1));
                    return NodeType.INT64;
                }
                Long.parseLong(s);
                return NodeType.INT64;
            }catch(NumberFormatException e1) {
                try {
                    if(s.endsWith("F")) {
                        Float.parseFloat(s.substring(0, s.length()-1));
                        return NodeType.FP32;
                    }
                    if(s.endsWith("D")) {
                        Double.parseDouble(s.substring(0, s.length()-1));
                        return NodeType.FP64;
                    }
                    Float.parseFloat(s);
                    return NodeType.FP32;
                }catch (NumberFormatException e2) {
                    try {
                        Double.parseDouble(s);
                        return NodeType.FP64;
                    }catch (NumberFormatException e3) {
                        if(s.length() == 3 && s.charAt(0) == '\'' && s.charAt(2) == '\'') {
                            return NodeType.CHAR;
                        }
                        return NodeType.VARIABLE;
                    }
                }
            }
        }
    }
}
