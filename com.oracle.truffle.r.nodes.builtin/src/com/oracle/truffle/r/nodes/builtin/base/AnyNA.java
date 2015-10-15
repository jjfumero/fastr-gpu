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

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.control.RLengthNode;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.na.*;

@RBuiltin(name = "anyNA", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x"}, dispatch = RDispatch.INTERNAL_GENERIC)
public abstract class AnyNA extends RBuiltinNode {

    protected static final int MAX_CACHED_LENGTH = 10;

    private final NACheck naCheck = NACheck.create();

    public abstract byte execute(VirtualFrame frame, Object value);

    private byte doScalar(boolean isNA) {
        controlVisibility();
        return RRuntime.asLogical(isNA);
    }

    @FunctionalInterface
    private interface VectorIndexPredicate<T extends RAbstractVector> {
        boolean apply(T vector, int index);
    }

    private <T extends RAbstractVector> byte doVector(T vector, VectorIndexPredicate<T> predicate) {
        controlVisibility();
        naCheck.enable(vector);
        for (int i = 0; i < vector.getLength(); i++) {
            if (predicate.apply(vector, i)) {
                return RRuntime.LOGICAL_TRUE;
            }
        }
        return RRuntime.LOGICAL_FALSE;
    }

    @Specialization
    protected byte isNA(byte value) {
        return doScalar(RRuntime.isNA(value));
    }

    @Specialization
    protected byte isNA(int value) {
        return doScalar(RRuntime.isNA(value));
    }

    @Specialization
    protected byte isNA(double value) {
        return doScalar(RRuntime.isNAorNaN(value));
    }

    @Specialization
    protected byte isNA(RComplex value) {
        return doScalar(RRuntime.isNA(value));
    }

    @Specialization
    protected byte isNA(String value) {
        return doScalar(RRuntime.isNA(value));
    }

    @Specialization
    protected byte isNA(@SuppressWarnings("unused") RRaw value) {
        return doScalar(false);
    }

    @Specialization
    protected byte isNA(@SuppressWarnings("unused") RNull value) {
        return doScalar(false);
    }

    @Specialization
    protected byte isNA(RAbstractIntVector vector) {
        return doVector(vector, (v, i) -> naCheck.check(v.getDataAt(i)));
    }

    @Specialization
    protected byte isNA(RAbstractDoubleVector vector) {
        // since
        return doVector(vector, (v, i) -> naCheck.checkNAorNaN(v.getDataAt(i)));
    }

    @Specialization
    protected byte isNA(RAbstractComplexVector vector) {
        return doVector(vector, (v, i) -> naCheck.check(v.getDataAt(i)));
    }

    @Specialization
    protected byte isNA(RAbstractStringVector vector) {
        return doVector(vector, (v, i) -> naCheck.check(v.getDataAt(i)));
    }

    @Specialization
    protected byte isNA(RAbstractLogicalVector vector) {
        return doVector(vector, (v, i) -> naCheck.check(v.getDataAt(i)));
    }

    @Specialization
    protected byte isNA(@SuppressWarnings("unused") RAbstractRawVector vector) {
        return doScalar(false);
    }

    @Specialization
    protected byte isNA(RFactor value) {
        return doVector(value.getVector(), (v, i) -> naCheck.check(v.getDataAt(i)));
    }

    protected AnyNA createRecursive() {
        return AnyNANodeGen.create(null, null, null);
    }

    @Specialization
    protected byte isNA(VirtualFrame frame, RList list, //
                    @Cached("createRecursive()") AnyNA recursive, //
                    @Cached("createClassProfile()") ValueProfile elementProfile, //
                    @Cached("create()") RLengthNode length) {
        controlVisibility();
        for (int i = 0; i < list.getLength(); i++) {
            Object value = elementProfile.profile(list.getDataAt(i));
            if (length.executeInteger(frame, value) == 1) {
                byte result = recursive.execute(frame, value);
                if (result == RRuntime.LOGICAL_TRUE) {
                    return RRuntime.LOGICAL_TRUE;
                }
            }
        }
        return RRuntime.LOGICAL_FALSE;
    }
}
