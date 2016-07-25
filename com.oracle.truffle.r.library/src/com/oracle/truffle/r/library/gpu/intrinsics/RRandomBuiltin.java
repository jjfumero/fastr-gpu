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
package com.oracle.truffle.r.library.gpu.intrinsics;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RDouble;

public abstract class RRandomBuiltin extends RExternalBuiltinNode.Arg1 {

    @Specialization
    public RDouble generateRandom(Object x) {
        long seed = castInt(castVector(x));
        seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
        seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
        // this generates a number between 0 and 1 (with an awful entropy)
        float random = (seed & 0x0FFFFFFF) / 268435455f;
        RDouble randomValue = RDouble.valueOf(random);
        return randomValue;
    }
}
