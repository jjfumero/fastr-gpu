package com.oracle.truffle.r.library.gpu.cache;

import java.util.HashMap;

import uk.ac.ed.jpai.graal.GraalGPUCompilationUnit;
import uk.ac.ed.jpai.graal.GraalGPUExecutor;

public class CacheGPUExecutor {

    private HashMap<GraalGPUCompilationUnit, GraalGPUExecutor> cache;

    public static final CacheGPUExecutor INSTANCE = new CacheGPUExecutor();

    private CacheGPUExecutor() {
        this.cache = new HashMap<>();
    }

    public void insert(GraalGPUCompilationUnit unit, GraalGPUExecutor executor) {
        cache.put(unit, executor);
    }

    public GraalGPUExecutor getExecutor(GraalGPUCompilationUnit unit) {
        return cache.get(unit);
    }
}
