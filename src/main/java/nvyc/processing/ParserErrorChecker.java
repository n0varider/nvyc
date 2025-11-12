package nvyc.processing;

import nvyc.data.NodeStream;

public class ParserErrorChecker {

    // Reconstructor

    public static String reconstruct(NodeStream stream) {
        StringBuilder builder = new StringBuilder();
        while(stream.next() != null) {
            builder.append(stream.getValue().toString());
            stream = stream.next();
        }
        return builder.toString();
    }
}
