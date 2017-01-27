package com.oracle.truffle.r.library.gpu.cache;

import java.util.ArrayList;
import java.util.HashMap;

import com.oracle.truffle.r.runtime.data.RFunction;

/**
 * Table that keeps the function to be compiled with the input data associated. If the input data is
 * not the one previously observed, then the check returns false and the function, although was was
 * compiled before, has to be recompile for the new types.
 *
 */
public class LookupFunctionToData {

    // public static LookupFunctionToData INSTANCE = new LookupFunctionToData();
    private HashMap<RFunction, Object[]> table;

    public LookupFunctionToData() {
        table = new HashMap<>();
    }

    private static Object[] buildObjectArrayForRereferences(Object[] args) {
        ArrayList<Object> references = new ArrayList<>();
        references.add(args[0]);
        if (args.length > 2) {
            for (int i = 2; i < args.length; i++) {
                references.add(args[i]);
            }
        }
        return references.toArray();
    }

    public boolean checkData(RFunction function, Object... args) {
        if (table.containsKey(function)) {
            Object[] cachedArgs = table.get(function);
            Object[] references = buildObjectArrayForRereferences(args);
            if (cachedArgs.length != references.length) {
                return false;
            }
            for (int i = 0; i < cachedArgs.length; i++) {
                if (cachedArgs[i] != references[i]) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public void insert(RFunction function, Object... args) {
        Object[] references = buildObjectArrayForRereferences(args);
        table.put(function, references);
    }

    public void clear() {
        table.clear();
    }
}
