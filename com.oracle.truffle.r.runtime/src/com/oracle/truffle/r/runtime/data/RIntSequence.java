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
package com.oracle.truffle.r.runtime.data;

import java.util.*;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.model.*;

public final class RIntSequence extends RSequence implements RAbstractIntVector {

    private final int start;
    private final int stride;

    RIntSequence(int start, int stride, int length) {
        super(length);
        assert length > 0;
        this.start = start;
        this.stride = stride;
    }

    public int getStart() {
        return start;
    }

    public int getStride() {
        return stride;
    }

    @Override
    protected RIntVector internalCreateVector() {
        int[] result = new int[getLength()];
        int current = start;
        for (int i = 0; i < getLength(); ++i) {
            result[i] = current;
            current += stride;
        }
        return RDataFactory.createIntVector(result, RDataFactory.COMPLETE_VECTOR);
    }

    @Override
    @SlowPath
    public String toString() {
        return internalCreateVector().toString();
    }

    public RIntSequence removeLast() {
        assert getLength() >= 1;
        return RDataFactory.createIntSequence(getStart(), getStride(), getLength() - 1);
    }

    public RIntSequence removeFirst() {
        assert getLength() >= 1;
        return RDataFactory.createIntSequence(getStart() + 1, getStride(), getLength() - 1);
    }

    public int getDataAt(int index) {
        assert index >= 0 && index < getLength();
        return start + stride * index;
    }

    public int getEnd() {
        return start + (getLength() - 1) * stride;
    }

    public RIntVector materialize() {
        return this.internalCreateVector();
    }

    public Class<?> getElementClass() {
        return RInt.class;
    }

    @Override
    public Object getDataAtAsObject(int index) {
        return getDataAt(index);
    }

    public RStringVector getClassHierarchy() {
        return RDataFactory.createStringVector(RRuntime.CLASS_INTEGER, true);
    }
}
