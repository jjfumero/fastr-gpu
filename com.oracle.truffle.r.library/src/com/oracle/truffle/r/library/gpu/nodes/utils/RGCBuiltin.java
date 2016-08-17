package com.oracle.truffle.r.library.gpu.nodes.utils;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;

public abstract class RGCBuiltin extends RExternalBuiltinNode.Arg0 {

    @TruffleBoundary
    private static void printGCStats() {
        long totalGarbageCollections = 0;
        long garbageCollectionTime = 0;

        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            if (count >= 0) {
                totalGarbageCollections += count;
            }
            long time = gc.getCollectionTime();
            if (time >= 0) {
                garbageCollectionTime += time;
            }
        }
        System.out.println("Total Garbage Collections: " + totalGarbageCollections);
        System.out.println("Total Garbage Collection Time (ms): " + garbageCollectionTime);
    }

    @TruffleBoundary
    private static void memoryUsage() {
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        System.out.println("Memory usage: " + usedMB + " MB");
    }

    @Specialization
    public int garbageCollection() {
        System.gc();
        printGCStats();
        memoryUsage();
        return 0;
    }
}