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
package com.oracle.truffle.r.nodes.attributes;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

/**
 * Simple attribute access node that specializes on the position at which the attribute was found
 * last time.
 */
public abstract class UnaryCopyAttributesNode extends BaseRNode {

    protected final boolean copyAllAttributes;

    protected final RAttributeProfiles attrSourceProfiles = RAttributeProfiles.create();

    protected UnaryCopyAttributesNode(boolean copyAllAttributes) {
        this.copyAllAttributes = copyAllAttributes;
    }

    public abstract RAbstractVector execute(RAbstractVector target, RAbstractVector left);

    protected boolean containsMetadata(RAbstractVector vector, RAttributeProfiles attrProfiles) {
        return vector instanceof RVector && vector.hasDimensions() || (copyAllAttributes && vector.getAttributes() != null) || vector.getNames(attrProfiles) != null ||
                        vector.getDimNames(attrProfiles) != null;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "!containsMetadata(source, attrSourceProfiles)")
    public RAbstractVector copyNoMetadata(RAbstractVector target, RAbstractVector source) {
        return target;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"copyAllAttributes", "target == source"})
    public RAbstractVector copySameVector(RAbstractVector target, RAbstractVector source) {
        return target;
    }

    @Specialization(guards = {"!copyAllAttributes || target != source", "containsMetadata(source, attrSourceProfiles)"})
    public RAbstractVector copySameLength(RAbstractVector target, RAbstractVector source, //
                    @Cached("create()") CopyOfRegAttributesNode copyOfReg, //
                    @Cached("createDim()") RemoveAttributeNode removeDim, //
                    @Cached("createDimNames()") RemoveAttributeNode removeDimNames, //
                    @Cached("create()") InitAttributesNode initAttributes, //
                    @Cached("createNames()") PutAttributeNode putNames, //
                    @Cached("createDim()") PutAttributeNode putDim, //
                    @Cached("createBinaryProfile()") ConditionProfile noDimensions, //
                    @Cached("createBinaryProfile()") ConditionProfile hasNamesSource, //
                    @Cached("createBinaryProfile()") ConditionProfile hasDimNames) {
        RVector result = target.materialize();

        if (copyAllAttributes) {
            copyOfReg.execute(source, result);
        }

        int[] newDimensions = source.getDimensions();
        if (noDimensions.profile(newDimensions == null)) {
            RAttributes attributes = result.getAttributes();
            if (attributes != null) {
                removeDim.execute(attributes);
                removeDimNames.execute(attributes);
                result.setInternalDimNames(null);
            }
            result.setInternalDimensions(null);

            RStringVector vecNames = source.getNames(attrSourceProfiles);
            if (hasNamesSource.profile(vecNames != null)) {
                putNames.execute(initAttributes.execute(result), vecNames);
                result.setInternalNames(vecNames);
                return result;
            }
            return result;
        }

        putDim.execute(initAttributes.execute(result), RDataFactory.createIntVector(newDimensions, RDataFactory.COMPLETE_VECTOR));
        result.setInternalDimensions(newDimensions);

        RList newDimNames = source.getDimNames(attrSourceProfiles);
        if (hasDimNames.profile(newDimNames != null)) {
            result.getAttributes().put(RRuntime.DIMNAMES_ATTR_KEY, newDimNames);
            newDimNames.elementNamePrefix = RRuntime.DIMNAMES_LIST_ELEMENT_NAME_PREFIX;
            result.setInternalDimNames(newDimNames);
            return result;
        }
        return result;
    }
}
