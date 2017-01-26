package com.oracle.truffle.r.library.gpu.cache;

import java.util.HashMap;

import com.oracle.truffle.r.runtime.data.RFunction;

public class LookupFunctionToData {

    public static LookupFunctionToData INSTANCE = new LookupFunctionToData();
    private HashMap<RFunction, Object[]> table;

    private LookupFunctionToData() {
        table = new HashMap<>();
    }

    public boolean checkData(RFunction function, Object... args) {
        if (table.containsKey(function)) {
            Object[] cachedArgs = table.get(function);
            int i = 0;
            for (Object c : cachedArgs) {
                if (c != args[i]) {
                    return false;
                }
                i++;
            }
            return true;
        } else {
            return false;
        }
    }

    public void insert(RFunction function, Object... args) {
        table.put(function, args);
    }

    public void clear() {
        table.clear();
    }
}
