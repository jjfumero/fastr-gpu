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
package com.oracle.truffle.r.runtime.data;

import java.util.*;

import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.model.*;

public final class RList extends RListBase implements RAbstractVector, RGPBits {

    private static final RStringVector implicitClassHeader = RDataFactory.createStringVectorFromScalar(RType.List.getName());

    private int gpbits;
    public String elementNamePrefix;

    RList(Object[] data, int[] dims, RStringVector names) {
        super(data, dims, names);
    }

    public RType getRType() {
        return RType.List;
    }

    @Override
    protected RList internalCopy() {
        return new RList(Arrays.copyOf(data, data.length), dimensions, null);
    }

    @Override
    protected RVector internalDeepCopy() {
        // TOOD: only used for nested list updates, but still could be made faster (through a
        // separate AST node?)
        RList listCopy = new RList(Arrays.copyOf(data, data.length), dimensions, null);
        for (int i = 0; i < listCopy.getLength(); i++) {
            Object el = listCopy.getDataAt(i);
            if (el instanceof RVector) {
                Object elCopy = ((RVector) el).deepCopy();
                listCopy.updateDataAt(i, elCopy, null);
            }
        }
        return listCopy;
    }

    @Override
    public RList createEmptySameType(int newLength, boolean newIsComplete) {
        return RDataFactory.createList(new Object[newLength]);
    }

    @Override
    public RList copyWithNewDimensions(int[] newDimensions) {
        return RDataFactory.createList(data, newDimensions);
    }

    @Override
    public RList copyResized(int size, boolean fillNA) {
        return RDataFactory.createList(copyResizedData(size, fillNA));
    }

    @Override
    public RStringVector getImplicitClass() {
        return getClassHierarchyHelper(implicitClassHeader);
    }

    public int getGPBits() {
        return gpbits;
    }

    public void setGPBits(int value) {
        gpbits = value;
    }
}
