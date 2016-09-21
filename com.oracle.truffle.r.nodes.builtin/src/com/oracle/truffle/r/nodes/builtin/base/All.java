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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.unary.CastLogicalNode;
import com.oracle.truffle.r.nodes.unary.CastLogicalNodeGen;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RSequence;
import com.oracle.truffle.r.runtime.data.RVector;

@RBuiltin(name = "all", kind = PRIMITIVE, parameterNames = {"...", "na.rm"})
public abstract class All extends RBuiltinNode {

    @Child private CastLogicalNode castLogicalNode;

    public abstract Object execute(Object o);

    @Override
    protected void createCasts(CastBuilder casts) {
        casts.toLogical(0);
    }

    @Specialization
    protected byte all(byte value) {
        controlVisibility();
        return value;
    }

    @Specialization
    protected byte all(RLogicalVector vector) {
        controlVisibility();
        return accumulate(vector);
    }

    @Specialization
    protected byte all(@SuppressWarnings("unused") RNull vector) {
        controlVisibility();
        return RRuntime.LOGICAL_TRUE;
    }

    @Specialization
    protected byte all(@SuppressWarnings("unused") RMissing vector) {
        controlVisibility();
        return RRuntime.LOGICAL_TRUE;
    }

    @Specialization
    protected byte all(RArgsValuesAndNames args) {
        if (castLogicalNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castLogicalNode = insert(CastLogicalNodeGen.create(true, false, false));
        }
        controlVisibility();
        Object[] argValues = args.getArguments();
        for (Object argValue : argValues) {
            byte result;
            if (argValue instanceof RVector || argValue instanceof RSequence) {
                result = accumulate((RLogicalVector) castLogicalNode.execute(argValue));
            } else {
                result = (byte) castLogicalNode.execute(argValue);
            }
            if (result != RRuntime.LOGICAL_TRUE) {
                return result;
            }
        }
        return RRuntime.LOGICAL_TRUE;
    }

    private static byte accumulate(RLogicalVector vector) {
        for (int i = 0; i < vector.getLength(); i++) {
            byte b = vector.getDataAt(i);
            if (b != RRuntime.LOGICAL_TRUE) {
                return b;
            }
        }
        return RRuntime.LOGICAL_TRUE;
    }
}
