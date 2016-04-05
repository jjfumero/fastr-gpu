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

import uk.ac.ed.jpai.ArrayFunction;

public class RMarawaccPromises {

    public static final RMarawaccPromises INSTANCE = new RMarawaccPromises();

    private ArrayList<MarawaccPackage> promises;
    private HashMap<ArrayFunction<?, ?>, Integer> index;
    private int size;

    private RMarawaccPromises() {
        promises = new ArrayList<>();
        index = new HashMap<>();
    }

    public void addPromise(MarawaccPackage marawaccPackage) {
        promises.add(marawaccPackage);
        index.put(marawaccPackage.getArrayFunction(), size);
        size++;
    }

    @SuppressWarnings("rawtypes")
    public MarawaccPackage getPackageForArrayFunction(ArrayFunction arrayFunction) {
        return promises.get(index.get(arrayFunction));
    }

    public MarawaccPackage getPackage(int idx) {
        return promises.get(idx);
    }

    public MarawaccPackage getLast() {
        return promises.get(size - 1);
    }

    public void clean() {
        promises.clear();
        index.clear();
        size = 0;
    }
}
