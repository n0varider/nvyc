package nvyc.data;

public class Tuple {

    private final Object[] data;

    public Tuple(Object... args) {
        data = args;
    }

    public Object[] get() {
        return data;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("TUPLE[");
        for(int i = 0; i < data.length; i++) {
            if(i > 0) builder.append(", ");
            builder.append(data[i]);
        }
        builder.append("]");
        return builder.toString();
    }
}
