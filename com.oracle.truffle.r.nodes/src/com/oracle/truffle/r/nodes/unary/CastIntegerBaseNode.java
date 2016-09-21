/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.unary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.BranchProfile;
import com.oracle.truffle.api.utilities.ConditionProfile;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RRaw;
import com.oracle.truffle.r.runtime.ops.na.NACheck;

public abstract class CastIntegerBaseNode extends CastBaseNode {

    protected final NACheck naCheck = NACheck.create();
    protected final BranchProfile warningBranch = BranchProfile.create();

    @Child private CastIntegerNode recursiveCastInteger;

    protected Object castIntegerRecursive(Object o) {
        if (recursiveCastInteger == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            recursiveCastInteger = insert(CastIntegerNodeGen.create(isPreserveNames(), isDimensionsPreservation(), isAttrPreservation()));
        }
        return recursiveCastInteger.executeInt(o);
    }

    @Specialization
    protected RNull doNull(@SuppressWarnings("unused") RNull operand) {
        return RNull.instance;
    }

    @Specialization
    protected RMissing doMissing(RMissing operand) {
        return operand;
    }

    @Specialization
    protected int doInt(int operand) {
        return operand;
    }

    @Specialization
    protected int doDouble(double operand) {
        naCheck.enable(operand);
        return naCheck.convertDoubleToInt(operand);
    }

    @Specialization
    protected int doComplex(RComplex operand) {
        naCheck.enable(operand);
        int result = naCheck.convertComplexToInt(operand, false);
        if (operand.getImaginaryPart() != 0.0) {
            warningBranch.enter();
            RError.warning(this, RError.Message.IMAGINARY_PARTS_DISCARDED_IN_COERCION);
        }
        return result;
    }

    @Specialization
    protected int doCharacter(String operand, //
                    @Cached("createBinaryProfile()") ConditionProfile emptyStringProfile) {
        naCheck.enable(operand);
        if (naCheck.check(operand) || emptyStringProfile.profile(operand.isEmpty())) {
            return RRuntime.INT_NA;
        }
        int result = RRuntime.string2intNoCheck(operand);
        if (RRuntime.isNA(result)) {
            warningBranch.enter();
            RError.warning(this, RError.Message.NA_INTRODUCED_COERCION);
        }
        return result;
    }

    @Specialization
    protected int doBoolean(byte operand) {
        naCheck.enable(operand);
        return naCheck.convertLogicalToInt(operand);
    }

    @Specialization
    protected int doRaw(RRaw operand) {
        return RRuntime.raw2int(operand);
    }

}
