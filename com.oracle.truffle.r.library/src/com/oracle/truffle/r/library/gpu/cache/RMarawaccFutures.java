package com.oracle.truffle.r.library.gpu.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.oracle.truffle.r.library.gpu.MarawaccReduceBuiltin;

import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.jpai.ArrayFunction;

public class RMarawaccFutures {

    public static final RMarawaccFutures INSTANCE = new RMarawaccFutures();

    @SuppressWarnings("rawtypes") private Queue<Future<PArray>> executionList;
    @SuppressWarnings("rawtypes") private ArrayList<Future<PArray>> futures;
    private HashMap<ArrayFunction<?, ?>, Integer> index;
    @SuppressWarnings("rawtypes") private ArrayList<PArray> results;
    private ArrayList<MarawaccPackage> packages;
    private int position;

    private RMarawaccFutures() {
        executionList = new LinkedList<>();
        futures = new ArrayList<>();
        index = new HashMap<>();
        results = new ArrayList<>();
        packages = new ArrayList<>();

    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void addFuture(MarawaccPackage marawaccPackage) {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Callable<PArray> task = null;
        if (executionList.isEmpty()) {
            task = () -> {
                return marawaccPackage.getArrayFunction().apply(marawaccPackage.getpArray());
            };
        } else {
            Future<PArray> element = executionList.element();
            task = () -> {
                int pos = position;
                results.add(null);
                while (!element.isDone()) {
                    // wait
                }
                executionList.remove();
                PArray input = element.get();
                results.set(pos - 1, input);
                return marawaccPackage.getArrayFunction().apply(input);
            };
        }
        Future<PArray> future = executor.submit(task);
        futures.add(future);
        executionList.add(future);
        packages.add(marawaccPackage);
        index.put(marawaccPackage.getArrayFunction(), position);
        position++;
    }

    @SuppressWarnings("rawtypes")
    public PArray getPArray(int idx) throws InterruptedException, ExecutionException {
        return futures.get(idx).get();
    }

    @SuppressWarnings("rawtypes")
    public PArray getPArray(ArrayFunction arrayFunction) {
        try {
            return getPArray(index.get(arrayFunction));
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    public MarawaccPackage getPackageForArrayFunction(ArrayFunction arrayFunction) {
        return packages.get(index.get(arrayFunction));
    }

    public void clean() {
        executionList.clear();
        futures.clear();
        results.clear(); // Possibility for memoisation if I dont clean the result (association with
                         // R lambdas + data required)
        index.clear();
        position = 0;
    }
}
