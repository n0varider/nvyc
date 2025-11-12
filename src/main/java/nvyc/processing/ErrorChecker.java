package nvyc.processing;

import nvyc.data.NASTNode;
import nvyc.data.NodeType;
import nvyc.generation.Parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ErrorChecker {

    Parser parse = new Parser();

    Map<String, NASTNode> functionmap = new HashMap<>();

    public void validateTree(List<NASTNode> n) {
        for(NASTNode node : n) {
            validate(node);
        }
    }

    public void validate(NASTNode n) {
        NodeType t = n.getType();
        String name;
        switch (t) {
            case FUNCTION:
                name = n.getValue().toString();
                if(!functionmap.containsKey(name)) {
                    functionmap.put(name, n);
                } else {
                    // Commented out for now until reconstruction is fixed
                    System.out.printf("ERROR > Function '%s' already exists!\n", name);
                    //System.out.printf("1st declaration at %s\n", parse.reconstruct(functionmap.get(name)).get(0));
                    //System.out.printf("2nd declaration at %s\n", parse.reconstruct(n).get(0));
                    System.exit(1);
                }
                break;
            case VARDEF:
                break;
            default:
                break;
        }
    }

}
