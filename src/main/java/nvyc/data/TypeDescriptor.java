package nvyc.data;

public class TypeDescriptor {

    private Object[] data;

    public TypeDescriptor(Object... args) {
        data = args;
    }

    public Object[] get() {
        return data;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Descriptor[");
        for(Object o : data) {
            builder.append(o).append(", ");
        }
        builder.append("]");
        return builder.toString();
    }
}
