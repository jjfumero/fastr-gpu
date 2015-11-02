/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.ops.na.*;

public abstract class CastLogicalBaseNode extends CastBaseNode {

    protected final NACheck naCheck = NACheck.create();

    @Child private CastLogicalNode recursiveCastLogical;

    protected Object castLogicalRecursive(Object o) {
        if (recursiveCastLogical == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            recursiveCastLogical = insert(CastLogicalNodeGen.create(isPreserveNames(), isDimensionsPreservation(), isAttrPreservation()));
        }
        return recursiveCastLogical.execute(o);
    }

    @Specialization
    protected byte doLogical(byte operand) {
        return operand;
    }

    @Specialization
    protected byte doDouble(double operand) {
        naCheck.enable(operand);
        return naCheck.convertDoubleToLogical(operand);
    }

    @Specialization
    protected byte doInt(int operand) {
        naCheck.enable(operand);
        return naCheck.convertIntToLogical(operand);
    }

    @Specialization
    protected byte doComplex(RComplex operand) {
        naCheck.enable(operand);
        return naCheck.convertComplexToLogical(operand);
    }

    @Specialization
    protected byte doString(String operand) {
        naCheck.enable(operand);
        return naCheck.convertStringToLogical(operand);
    }

    @Specialization
    protected byte doRaw(RRaw operand) {
        return RRuntime.raw2logical(operand);
    }

}
