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

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.r.runtime.data.RFunction;

public class RGPUCache {

    private HashMap<RFunction, RCacheObjects> cache;

    public static final RGPUCache INSTANCE = new RGPUCache();

    private RGPUCache() {
        cache = new HashMap<>();
    }

    public void insertFunction(RFunction function, RootCallTarget target) {
        if (!cache.containsKey(function)) {
            RCacheObjects cachedObject = new RCacheObjects(target);
            cache.put(function, cachedObject);
        }
    }

    public RootCallTarget lookup(RFunction function) {
        if (!cache.containsKey(function)) {
            RCacheObjects cachedObject = new RCacheObjects(function.getTarget());
            cache.put(function, cachedObject);
        }
        return cache.get(function).getRootCallTarget();
    }

    public RootCallTarget getCallTarget(RFunction function) {
        if (cache.containsKey(function)) {
            return cache.get(function).getRootCallTarget();
        }
        return null;
    }

    public boolean contains(RFunction function) {
        return cache.containsKey(function);
    }

    public RCacheObjects getCachedObjects(RFunction function) {
        if (cache.containsKey(function)) {
            return cache.get(function);
        }
        return null;
    }

    public RootCallTarget updateCacheObjects(RFunction function, RCacheObjects cachedObjects) {
        cache.put(function, cachedObjects);
        return cachedObjects.getRootCallTarget();
    }
}
