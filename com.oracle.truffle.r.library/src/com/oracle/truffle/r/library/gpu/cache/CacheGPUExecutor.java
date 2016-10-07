package com.oracle.truffle.r.library.gpu.cache;

import java.util.HashMap;

import uk.ac.ed.jpai.graal.GraalOpenCLCompilationUnit;
import uk.ac.ed.jpai.graal.GraalOpenCLExecutor;

public class CacheGPUExecutor {

    private HashMap<GraalOpenCLCompilationUnit, GraalOpenCLExecutor> cache;

    public static final CacheGPUExecutor INSTANCE = new CacheGPUExecutor();

    private CacheGPUExecutor() {
        this.cache = new HashMap<>();
    }

    public void insert(GraalOpenCLCompilationUnit unit, GraalOpenCLExecutor executor) {
        cache.put(unit, executor);
    }

    public GraalOpenCLExecutor getExecutor(GraalOpenCLCompilationUnit unit) {
        return cache.get(unit);
    }
}
