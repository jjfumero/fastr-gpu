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

import java.util.HashMap;

import uk.ac.ed.datastructures.common.PArray;

import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public class CacheInputBuffers {

    private static CacheInputBuffers instance;

    private HashMap<RFunction, RAbstractVector[]> cacheRType;
    private HashMap<RFunction, PArray<?>[]> cachePArray;

    public static CacheInputBuffers getInstance() {
        if (instance == null) {
            instance = new CacheInputBuffers();
        }
        return instance;
    }

    private CacheInputBuffers() {
        cacheRType = new HashMap<>();
        cachePArray = new HashMap<>();
    }

    public void add(RFunction function, RAbstractVector[] values) {
        cacheRType.put(function, values);
    }

    public void add(RFunction function, PArray<?>[] values) {
        cachePArray.put(function, values);
    }

    public boolean constainsRVector(RFunction function) {
        return cacheRType.containsKey(function);
    }

    public boolean constainsPArray(RFunction function) {
        return cachePArray.containsKey(function);
    }

    public boolean check(RFunction function, RAbstractVector[] vector) {
        RAbstractVector[] values = cacheRType.get(function);
        int i = 0;
        for (RAbstractVector v : values) {
            if (v != vector[i]) {
                return false;
            }
            i++;
        }
        return true;
    }

    public boolean check(RFunction function, PArray<?>[] vector) {
        PArray<?>[] values = cachePArray.get(function);
        int i = 0;
        for (PArray<?> v : values) {
            if (v != vector[i]) {
                return false;
            }
            i++;
        }
        return true;
    }
}
