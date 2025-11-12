package nvyc.data;

import java.util.ArrayList;
import java.util.List;

public class NASTNode {

    private NodeType type;
    private Object value;
    private List<NASTNode> subtrees;
    private int line;

    public static final int TAIL = -1;


    public NASTNode(NodeType type, Object value) {
        this.type = type;
        this.value = value;
        this.subtrees = new ArrayList<>();
    }

    public NASTNode(NodeStream stream) {
        this.type = stream.getType();
        this.value = stream.getValue();
        this.subtrees = new ArrayList<>();
    }

    public void setLine(int i) {
        line = i;
    }

    public int getLine() {
        return line;
    }

    public NodeType getType() {
        return type;
    }


    public void setType(NodeType t) {
        type = t;
    }

    public Object getValue() {
        return value;
    }

    public String getValueString() {
        return value.toString();
    }

    public void setValue(Object v) {
        value = v;
    }

    public void addNode(NASTNode node, int index) {
        subtrees.add(index == -1 ? subtrees.size() : index, node);
    }

    public void removeAllSubnodes() {
        subtrees.clear();
    }

    public boolean contains(NodeType t) {
        if(type == t) {
            return true;
        }
        for(NASTNode nodes : subtrees) {
            if(nodes.contains(t)) {
                return true;
            }
        }
        return false;
    }

    public void removeNode(int index) {
        subtrees.remove(index);
    }

    public NASTNode getSubnode(int index) {
        return subtrees.get(index);
    }

    public List<NASTNode> getAllSubnodes() {
        return subtrees;
    }

    public String toString() {
        return toStringHelper("", "");
    }

    private String toStringHelper(String prefix, String child) {
        StringBuilder b = new StringBuilder();
        b.append(prefix);
        b.append("TNODE(").append(type).append(", ").append(value).append(")\n");
        if(!subtrees.isEmpty()) {
            for(NASTNode n : subtrees) {
                if(n != null) {
                    b.append(n.toStringHelper(child + "    -- ", child + "    "));
                }else{
                    b.append("NULL NODE\n");
                }
            }
        }
        return b.toString();
    }

    public List<String> getVariables() {
        List<String> result = new ArrayList<>();
        if(type == NodeType.VARIABLE) {
            result.add(value.toString());
        }else{
            if(subtrees.size() > 0 && getSubnode(0) != null)
                result.addAll(getSubnode(0).getVariables());
            if(subtrees.size() > 1 && getSubnode(1) != null) {
                result.addAll(getSubnode(1).getVariables());
            }
        }
        return result;
    }

    public String currentNode() {
        return "TNODE(" + type + ", " + value + ")";
    }

    public List<String> flatten() {
        List<String> result = new ArrayList<>();
        return flattenHelper(result);
    }

    private List<String> flattenHelper(List<String> result) {
        result.add(currentNode());
        for(NASTNode n : subtrees) {
            n.flattenHelper(result);
        }
        return result;
    }

    public NASTNode literalNode() {
        return new NASTNode(type, value);
    }

    public List<NASTNode> flattenNodes() {
        List<NASTNode> result = new ArrayList<>();
        return flattenNodesHelper(result);
    }

    public List<NASTNode> flattenNodesHelper(List<NASTNode> result) {
        result.add(literalNode());
        for(NASTNode n : subtrees) {
            n.flattenNodesHelper(result);
        }
        return result;
    }
}
