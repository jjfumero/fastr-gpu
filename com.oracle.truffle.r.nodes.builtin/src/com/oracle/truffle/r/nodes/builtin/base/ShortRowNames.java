/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.utilities.BranchProfile;
import com.oracle.truffle.api.utilities.ConditionProfile;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

@RBuiltin(name = "shortRowNames", kind = INTERNAL, parameterNames = {"x", "type"})
@GenerateNodeFactory
public abstract class ShortRowNames extends RBuiltinNode {
    private final ConditionProfile nameConditionProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile typeConditionProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile naValueMet = BranchProfile.create();
    private final BranchProfile intVectorMet = BranchProfile.create();

    public abstract Object executeObject(VirtualFrame frame, Object operand, Object type);

    @CreateCast({"arguments"})
    public RNode[] createCastValue(RNode[] children) {
        return new RNode[]{children[0], CastIntegerNodeGen.create(children[1], false, false, false)};
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RNull getNames(RNull operand, RAbstractIntVector type) {
        controlVisibility();
        return RNull.instance;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "invalidType")
    protected RNull getNamesInvalidType(RAbstractContainer operand, RAbstractIntVector type) {
        controlVisibility();
        CompilerDirectives.transferToInterpreter();
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_ARGUMENT, "type");
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!invalidType", "!returnScalar"})
    protected Object getNamesNull(RAbstractContainer operand, RAbstractIntVector type) {
        controlVisibility();
        return operand.getRowNames();
    }

    @Specialization(guards = {"!invalidType", "returnScalar"})
    protected int getNames(RAbstractContainer operand, RAbstractIntVector type) {
        controlVisibility();
        int t = type.getDataAt(0);
        Object rowNames = operand.getRowNames();
        if (nameConditionProfile.profile(rowNames == RNull.instance)) {
            return 0;
        } else {
            int n = calculateN((RAbstractVector) rowNames);
            if (typeConditionProfile.profile(t == 1)) {
                return n;
            } else {
                return Math.abs(n);
            }
        }
    }

    private int calculateN(RAbstractVector rowNames) {
        if (rowNames.getElementClass() == RInt.class && rowNames.getLength() == 2) {
            RAbstractIntVector rowNamesIntVector = (RAbstractIntVector) rowNames;
            intVectorMet.enter();
            if (RRuntime.isNA(rowNamesIntVector.getDataAt(0))) {
                naValueMet.enter();
                return rowNamesIntVector.getDataAt(1);
            }
        }
        return rowNames.getLength();
    }

    protected boolean invalidType(@SuppressWarnings("unused") RAbstractContainer operand, RAbstractIntVector type) {
        return type.getLength() == 0 || type.getDataAt(0) < 0 || type.getDataAt(0) > 2;
    }

    protected boolean returnScalar(@SuppressWarnings("unused") RAbstractContainer operand, RAbstractIntVector type) {
        return type.getDataAt(0) >= 1;
    }

}
