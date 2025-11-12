package nvyc.data;

import java.util.Set;

public class NodeStream {

    private NodeType type;
    private Object value;
    private NodeStream next;
    private NodeStream prev;
    private int line;

    public NodeStream(NodeType type, Object value) {
        this.type = type;
        this.value = value;
    }

    public static NodeStream voidStream() {
        return new NodeStream(NodeType.VOID, NodeType.VOID);
    }

    public NodeStream setNext(NodeStream s) {
        this.next = s;
        return s;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public NodeStream deleteNode() {
        NodeStream n = next;
        if (prev != null) {
            prev.setNext(next);
        }
        if (next != null) {
            next.setPrev(prev);
        }
        next = null;
        prev = null;
        value = null;
        return n;
    }


    public void setPrev(NodeStream s) {
        this.prev = s;
    }

    public NodeStream next() {
        return this.next;
    }


    public NodeStream prev() {
        return this.prev;
    }

    public Object getValue() {
        return this.value;
    }

    public NodeType getType() {
        return this.type;
    }

    public void setType(NodeType t) {
        this.type = t;
    }

    /*
     * Sets next node to null.
     * Useful for parsing by allowing the parser to
     * cutoff the ends of a code block.
     */
    public void cutoff() {
        next = null;
    }

    public NodeStream copyAtCurrent() {
        int idx = 0;
        NodeStream s = this;
        while(s.prev != null) {
            s = s.prev;
            idx++;
        }
        NodeStream copy = this.copy();
        for(int i = 0; i < idx; i++) {
            copy = copy.next;
            s = s.next;
        }
        return copy;
    }

    /*
     * Backtracks to the root NodeStream
     *
     * @return the head NodeStream
     */
    public NodeStream backtrack() {
        NodeStream s = this;
        while(s.prev != null) {
            s = s.prev;
        }
        return s;
    }

    /*
     * Makes current node the head of this stream
     */
    public void cuthead() {
        prev = null;
    }

    public boolean contains(NodeType t) {
        NodeStream s = this.backtrack();
        while(s != null) {
            if(s.getType() == t) {
                return true;
            }
            s = s.next;
        }
        return false;
    }


    public boolean contains(Set<NodeType> set) {
        NodeStream s = this.backtrack();
        while(s != null) {
            if(set.contains(s.getType())) {
                return true;
            }
            s = s.next;
        }
        return false;
    }

    /*
     * cuthead() on a copy and return
     */
    /*public NodeStream cutheadAndReturn() {
        NodeStream copy = this.copy();
        copy.prev = null;
        return copy;
    }*/
    public NodeStream cutheadAndReturn() {
        NodeStream original = this;
        NodeStream copy = this.copy(); // Copy the entire stream

        // Find the corresponding node in the copied stream
        NodeStream copyCurrent = copy;
        NodeStream originalCurrent = original;

        while (originalCurrent.prev != null) {
            originalCurrent = originalCurrent.prev;
            copyCurrent = copyCurrent.next;
        }

        // Now copyCurrent should be at the same position as 'this' in the copied stream
        copyCurrent.prev = null; // Make it the new head
        return copyCurrent;
    }

    /*
     * cutoff() on a copy and return
     */
    public NodeStream cutoffAndReturn() {
        NodeStream original = this;
        NodeStream copy = this.copy(); // Copy the entire stream

        // Find the corresponding node in the copied stream
        NodeStream copyCurrent = copy;
        NodeStream originalCurrent = original;

        while (originalCurrent.prev != null) {
            originalCurrent = originalCurrent.prev;
            copyCurrent = copyCurrent.next;
        }

        // Now copyCurrent should be at the same position as 'this' in the copied stream
        copyCurrent.next = null; // Cut off everything after it
        return copy.backtrack(); // Return the head of the truncated copy
    }

    public NodeStream getStripped() {
        return new NodeStream(type, value);
    }

    public int length() {
        int x = 0;
        NodeStream cpy = this.copy();
        cpy = cpy.backtrack();
        while(cpy != null) {
            x++;
            cpy = cpy.next;
        }
        return x;
    }

    /*
     * Copies all data for the current NodeStream
     *
     * @return a copy of the current NodeStream
     */
    public NodeStream copy() {
        NodeStream head = backtrack();
        NodeStream copy = new NodeStream(head.getType(), head.getValue());
        NodeStream current = copy;
        while(head.next() != null) {
            head = head.next();
            current.setNext(new NodeStream(head.getType(), head.getValue()));
            current = current.next();
            current.setPrev(copy);
            copy = copy.next();
        }
        return copy.backtrack();
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void remove() {
        prev.next = next;
        next.prev = prev;
        type = null;
        prev = null;
        next = null;
        value = null;
    }

    public NodeStream forward(int dist) {
        NodeStream s = this;
        for(int i = 0; i < dist; i++) {
            s = s.next;
        }
        return s;
    }

    public NodeStream forwardType(NodeType t) {
        NodeStream s = this;
        while(s != null && s.getType() != t) {
            s = s.next;
        }
        return s;
    }

    public int distance(NodeStream other) {
        NodeStream s = this;
        int dist = 0;
        while(s != null && s != other) {
            s = s.next;
            dist++;
        }
        return dist;
    }

    public String getCurrentAsString() {
        return String.format("NODE(%s, %s)", type, value);
    }

    public String toString() {
        NodeStream c = this;
        StringBuilder b = new StringBuilder();
        b.append(String.format("NODE(%s, %s)\n", type, value));
        while(c.next() != null) {
            c = c.next();
            b.append(String.format("NODE(%s, %s)\n", c.getType(), c.getValue()));
        }
        return b.toString();
    }

    public boolean nodeEquals(NodeStream s) {
        return s.type == this.type && s.getValue().equals(this.value);
    }

    public boolean equals(NodeStream s) {
        NodeStream current = this;
        while(current.prev() != null) {
            current = current.prev();
        }
        while(s.prev() != null) {
            s = s.prev();
        }
        while(s != null && current != null) {
            boolean nodes = s.getType() == current.getType();
            boolean vals = s.getValue().equals(current.getValue());
            if(!nodes || !vals) {
                return false;
            }
            s = s.next();
            current = current.next();
        }
        return true;
    }
}
