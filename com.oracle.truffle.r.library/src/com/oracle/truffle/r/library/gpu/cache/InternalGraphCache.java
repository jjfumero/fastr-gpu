package com.oracle.truffle.r.library.gpu.cache;

import java.util.HashMap;

import uk.ac.ed.jpai.graal.GraalGPUCompilationUnit;

import com.oracle.graal.nodes.StructuredGraph;

public class InternalGraphCache {

    private HashMap<StructuredGraph, GraalGPUCompilationUnit> cache;

    public static final InternalGraphCache INSTANCE = new InternalGraphCache();

    private InternalGraphCache() {
        cache = new HashMap<>();
    }

    public void insertGPUBinary(StructuredGraph graph, GraalGPUCompilationUnit gpuCompilationUnit) {
        if (!cache.containsKey(graph)) {
            cache.put(graph, gpuCompilationUnit);
        }
    }

    public GraalGPUCompilationUnit getGPUCompilationUnit(StructuredGraph graph) {
        if (cache.containsKey(graph)) {
            return cache.get(graph);
        }
        return null;
    }
}
