package nvyc.processing;

import nvyc.data.NASTNode;
import nvyc.data.NodeType;

import java.util.List;
import java.util.Map;

public class ASTCleanup {

    public void resolveMangledNames(List<NASTNode> nodes, Map<String, String> mapping) {
        for(NASTNode node : nodes) {
            System.out.println(node);
            resolveNodes(node, mapping);
        }
    }

    public void resolveNodes(NASTNode n, Map<String, String> mapping) {
        NodeType type = n.getType();
        String value;
        if(type == NodeType.FUNCTIONCALL) {
            n.setValue(mapping.getOrDefault(n.getValue().toString(), n.getValue().toString()));
        }
        if(!n.getAllSubnodes().isEmpty()) {
            for (NASTNode subnode : n.getAllSubnodes()) {
                resolveNodes(subnode, mapping);
            }
        }
    }
}
