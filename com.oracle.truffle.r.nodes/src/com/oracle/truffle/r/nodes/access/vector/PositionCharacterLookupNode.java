/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.access.vector;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

final class PositionCharacterLookupNode extends Node {

    private final ElementAccessMode mode;
    private final RAttributeProfiles attributeProfiles = RAttributeProfiles.create();
    private final int numDimensions;
    private final int dimensionIndex;
    private final BranchProfile emptyProfile = BranchProfile.create();

    @Child private SearchFirstStringNode searchNode;

    public PositionCharacterLookupNode(ElementAccessMode mode, int numDimensions, int dimensionIndex, boolean useNAForNotFound, boolean exact) {
        this.numDimensions = numDimensions;
        this.dimensionIndex = dimensionIndex;
        this.searchNode = SearchFirstStringNode.createNode(exact, useNAForNotFound);
        this.mode = mode;
    }

    public RAbstractIntVector execute(RAbstractContainer target, RAbstractStringVector position, int notFoundStartIndex) {
        // lookup names for single dimension case
        RAbstractIntVector result;
        if (numDimensions <= 1) {
            RStringVector names = target.getNames(attributeProfiles);
            if (names == null) {
                emptyProfile.enter();
                names = RDataFactory.createEmptyStringVector();
            }
            result = searchNode.apply(names, position, notFoundStartIndex);
            result.setNames(position.materialize());
        } else {
            RList dimNames = target.getDimNames(attributeProfiles);
            if (dimNames != null) {
                Object dataAt = dimNames.getDataAt(dimensionIndex);
                if (dataAt != RNull.instance) {
                    RStringVector dimName = (RStringVector) dataAt;
                    result = searchNode.apply(dimName, position, notFoundStartIndex);
                } else {
                    emptyProfile.enter();
                    throw RError.error(this, Message.SUBSCRIPT_BOUNDS);
                }
            } else {
                emptyProfile.enter();
                throw noDimNames();
            }

        }
        return result;
    }

    private RError noDimNames() {
        if (mode.isSubset()) {
            return RError.error(this, Message.NO_ARRAY_DIMNAMES);
        } else {
            return RError.error(this, Message.SUBSCRIPT_BOUNDS);
        }
    }

}
