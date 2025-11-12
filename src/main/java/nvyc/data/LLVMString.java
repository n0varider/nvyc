package nvyc.data;

public class LLVMString {

    private String root;
    private String converted;

    public LLVMString(String root) {
        this.converted = llvmString(root);
        this.root = root;
    }

    private String llvmString(String s) {
        s = s.substring(0, s.length() - 1);
        s = s.replace("\\n", "\\0A");
        s = s + "\\00";
        s = s + "\"";
        return s;
    }

    public int rootLength() {
        return root.length();
    }

    public int length() {
        int baseLength = root.length() - 2 + 1; // -2 for begin/end quote, +1 for null
        if(root.contains("\\n")) {
            baseLength--;
        }
        return baseLength;
    }

    public String toString() {
        return converted;
    }
}
