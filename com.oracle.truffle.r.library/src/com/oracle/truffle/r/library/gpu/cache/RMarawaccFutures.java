/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.library.gpu.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import uk.ac.ed.accelerator.truffle.ASTxOptions;
import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.jpai.ArrayFunction;

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
