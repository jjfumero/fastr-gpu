package com.oracle.truffle.r.library.gpu;

import java.util.HashMap;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.r.runtime.data.RFunction;

public class RGPUCache {

    private HashMap<RFunction, RootCallTarget> cache;

    public static RGPUCache INSTANCE = new RGPUCache();

    private RGPUCache() {
        cache = new HashMap<>();
    }

    public void insertFunction(RFunction function, RootCallTarget target) {
        if (!cache.containsKey(function)) {
            cache.put(function, target);
        }
    }

    public RootCallTarget lookup(RFunction function) {
        if (!cache.containsKey(function)) {
            System.out.println("Registering a new function");
            cache.put(function, function.getTarget());
        }
        return cache.get(function);
    }

    public RootCallTarget getCallTarget(RFunction function) {
        if (cache.containsKey(function)) {
            return cache.get(function);
        }
        return null;
    }
}
