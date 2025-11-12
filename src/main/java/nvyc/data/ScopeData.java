package nvyc.data;

import java.util.*;

public class ScopeData {

    public static final int SCOPE_LOCAL = 1;
    public static final int SCOPE_GLOBAL = 0;

    private static ScopeData instance;

    private static int SCOPE_LEVEL = 0; // For nested scopes like if, for, etc. Scope 2 can access values in 1 and 0, but not 3, and so on

    private static Map<String, Integer> scopeMap;
    private static Map<String, Stack<Integer>> shadowedGlobals;

    public static ScopeData getInstance() {
        if(instance == null) {
            instance = new ScopeData();
            scopeMap = new HashMap<>();
            shadowedGlobals = new HashMap<>();
        }
        return instance;
    }

    public void increaseDepth() {
        SCOPE_LEVEL++;
    }

    public void decreaseDepth() {

        // Remove all variables in current scope and pop stacks where needed
        List<String> toRemove = new ArrayList<>();

        for(String var : scopeMap.keySet()) {
            int scope = scopeMap.get(var);
            if(scope != 1 && scope == SCOPE_LEVEL) {

                // Don't remove if a previous scope already exists
                if(shadowedGlobals.containsKey(var)) {
                    int prevScope = shadowedGlobals.get(var).pop();
                    scopeMap.put(var, prevScope);
                }

                // Add to removal list if no previous scope exists
                else {
                    toRemove.add(var);
                }

            }
        }

        for(String s : toRemove) {
            scopeMap.remove(s);
        }

        SCOPE_LEVEL--;
    }

    public int getDepth() {
        return SCOPE_LEVEL;
    }

    public Map<String, Integer> getAll() {
        return scopeMap;
    }

    public void set(String var, int scope) {

        // If the variable exists, push last scope to the stack
        if(scopeMap.containsKey(var)) {
            int lastScope = scopeMap.get(var);

            // Create stack if it doesn't exist
            if(!shadowedGlobals.containsKey(var)) {
                createStack(var);
            }

            shadowedGlobals.get(var).push(lastScope);
        }

        scopeMap.put(var, scope);
    }

    private void createStack(String var) {
        shadowedGlobals.put(var, new Stack<>());
    }

    public void remove(String var) {
        scopeMap.remove(var);
    }

    public int get(String var) {
        // TODO error handling
        if(!scopeMap.containsKey(var)) {
            System.err.println("nvc > Variable not defined in current scope: " + var);
            throw new RuntimeException();
            //System.exit(1);
        }
        return scopeMap.get(var);
    }

    public boolean exists(String var) {
        return scopeMap.containsKey(var);
    }

    public boolean isLocal(String var) {
        return get(var) >= SCOPE_LOCAL;
    }

    public boolean isGlobal(String var) {
        return get(var) == SCOPE_GLOBAL;
    }

    public void removeAllLocals() {
        scopeMap.keySet().removeIf(s -> get(s) >= SCOPE_LOCAL);
    }

    public void removeHigherDepth() {
        scopeMap.keySet().removeIf(s -> get(s) > SCOPE_LEVEL);
    }
}
