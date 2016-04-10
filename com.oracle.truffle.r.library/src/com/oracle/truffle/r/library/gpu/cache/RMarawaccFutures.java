package com.oracle.truffle.r.library.gpu.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.jpai.ArrayFunction;

import com.oracle.truffle.r.library.gpu.options.ASTxOptions;

public class RMarawaccFutures {

    public static final RMarawaccFutures INSTANCE = new RMarawaccFutures();

    @SuppressWarnings("rawtypes") private LinkedList<Future<PArray>> executionQueue;
    @SuppressWarnings("rawtypes") private ArrayList<Future<PArray>> futuresList;
    private HashMap<ArrayFunction<?, ?>, Integer> index;
    private ArrayList<MarawaccPackage> packages;
    private int position;

    private RMarawaccFutures() {
        executionQueue = new LinkedList<>();
        futuresList = new ArrayList<>();
        index = new HashMap<>();
        packages = new ArrayList<>();

    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void addFuture(MarawaccPackage marawaccPackage) {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Callable<PArray> task = null;
        if (executionQueue.isEmpty()) {
            task = () -> {
                return marawaccPackage.getArrayFunction().apply(marawaccPackage.getpArray());
            };
        } else {
            Future<PArray> element = executionQueue.removeFirst();
            task = () -> {
                while (!element.isDone()) {
                    // wait
                }
                PArray input = element.get();
                return marawaccPackage.getArrayFunction().apply(input);
            };
        }
        Future<PArray> future = executor.submit(task);

        futuresList.add(future);
        executionQueue.addLast(future);
        packages.add(marawaccPackage);
        index.put(marawaccPackage.getArrayFunction(), position);
        position++;
    }

    @SuppressWarnings("rawtypes")
    public PArray getPArray(int idx) throws InterruptedException, ExecutionException {
        return futuresList.get(idx).get();
    }

    @SuppressWarnings("rawtypes")
    public PArray getPArray(ArrayFunction arrayFunction) {
        try {
            return getPArray(index.get(arrayFunction));
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    public MarawaccPackage getPackageForArrayFunction(ArrayFunction arrayFunction) {
        return packages.get(index.get(arrayFunction));
    }

    public void clean() {
        executionQueue.clear();
        if (!ASTxOptions.useAsyncMemoisation) {
            futuresList.clear();
            index.clear();
        }
        position = 0;
    }
}
