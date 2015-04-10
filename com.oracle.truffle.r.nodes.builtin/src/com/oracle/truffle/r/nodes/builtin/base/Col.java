/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import java.util.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;

@RBuiltin(name = "col", kind = INTERNAL, parameterNames = {"dims"})
public abstract class Col extends RBuiltinNode {

    @Specialization
    protected RIntVector col(@SuppressWarnings("unused") RNull x) {
        controlVisibility();
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.MATRIX_LIKE_REQUIRED, "col");
    }

    @Specialization
    protected RIntVector col(RIntVector x) {
        controlVisibility();
        int nrows = x.getDataAt(0);
        int ncols = x.getDataAt(1);
        int[] result = new int[nrows * ncols];
        for (int i = 0; i < ncols; i++) {
            int colstart = i * nrows;
            Arrays.fill(result, colstart, colstart + nrows, i + 1);
        }
        return RDataFactory.createIntVector(result, RDataFactory.COMPLETE_VECTOR, new int[]{nrows, ncols});
    }

}
