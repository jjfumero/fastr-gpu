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

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

@RBuiltin(name = "as.double", aliases = {"as.numeric"}, kind = PRIMITIVE, parameterNames = {"x", "..."})
// TODO define alias in R
@SuppressWarnings("unused")
public abstract class AsDouble extends RBuiltinNode {

    @Child private CastDoubleNode castDoubleNode;

    private void initCast() {
        if (castDoubleNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castDoubleNode = insert(CastDoubleNodeGen.create(null, false, false, false));
        }
    }

    private double castDouble(int o) {
        initCast();
        return (double) castDoubleNode.executeDouble(o);
    }

    private double castDouble(double o) {
        initCast();
        return (double) castDoubleNode.executeDouble(o);
    }

    private double castDouble(byte o) {
        initCast();
        return (double) castDoubleNode.executeDouble(o);
    }

    private double castDouble(Object o) {
        initCast();
        return (double) castDoubleNode.executeDouble(o);
    }

    private RDoubleVector castDoubleVector(Object o) {
        initCast();
        return (RDoubleVector) castDoubleNode.executeDouble(o);
    }

    @Specialization
    protected double asDouble(double value) {
        controlVisibility();
        return value;
    }

    @Specialization
    protected double asDoubleInt(int value) {
        controlVisibility();
        return castDouble(value);
    }

    @Specialization
    protected double asDouble(byte value) {
        controlVisibility();
        return castDouble(value);
    }

    @Specialization
    protected double asDouble(RComplex value) {
        controlVisibility();
        return castDouble(value);
    }

    @Specialization
    protected double asDouble(String value) {
        controlVisibility();
        return castDouble(value);
    }

    @Specialization
    protected RDoubleVector asDouble(RNull vector) {
        controlVisibility();
        return RDataFactory.createDoubleVector(0);
    }

    @Specialization
    protected RDoubleVector asDouble(RDoubleVector vector) {
        controlVisibility();
        return RDataFactory.createDoubleVector(vector.getDataCopy(), vector.isComplete());
    }

    @Specialization
    protected RDoubleSequence asDouble(RDoubleSequence sequence) {
        controlVisibility();
        return sequence;
    }

    @Specialization
    protected RDoubleSequence asDouble(RIntSequence sequence) {
        controlVisibility();
        return RDataFactory.createDoubleSequence(sequence.getStart(), sequence.getStride(), sequence.getLength());
    }

    @Specialization
    protected RDoubleVector asDouble(RAbstractVector vector) {
        controlVisibility();
        return castDoubleVector(vector);
    }

    @Specialization
    protected RDoubleVector asDouble(RFactor vector) {
        return asDouble(vector.getVector());
    }
}
