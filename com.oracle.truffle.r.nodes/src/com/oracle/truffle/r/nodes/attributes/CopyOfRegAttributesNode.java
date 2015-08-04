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
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.RAttributes.RAttribute;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.nodes.*;

/**
 * Simple attribute access node that specializes on the position at which the attribute was found
 * last time.
 */
public abstract class CopyOfRegAttributesNode extends RBaseNode {

    public abstract void execute(RAbstractVector source, RVector target);

    public static CopyOfRegAttributesNode create() {
        return CopyOfRegAttributesNodeGen.create();
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "source.getAttributes() == null")
    protected void copyNoAttributes(RAbstractVector source, RVector target) {
        // nothing to do
    }

    protected static final boolean emptyAttributes(RAbstractVector source) {
        RAttributes attributes = source.getAttributes();
        return attributes == null || attributes.isEmpty();
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "emptyAttributes(source)", contains = "copyNoAttributes")
    protected void copyEmptyAttributes(RAbstractVector source, RVector target) {
        // nothing to do
    }

    protected static final boolean onlyDimAttribute(RAbstractVector source) {
        RAttributes attributes = source.getAttributes();
        return attributes != null && attributes.size() == 1 && attributes.getNames()[0] == RRuntime.DIM_ATTR_KEY;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "onlyDimAttribute(source)")
    protected void copyDimOnly(RAbstractVector source, RVector target) {
        // nothing to do
    }

    protected static final boolean onlyNamesAttribute(RAbstractVector source) {
        RAttributes attributes = source.getAttributes();
        return attributes != null && attributes.size() == 1 && attributes.getNames()[0] == RRuntime.NAMES_ATTR_KEY;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "onlyNamesAttribute(source)")
    protected void copyNamesOnly(RAbstractVector source, RVector target) {
        // nothing to do
    }

    protected static final boolean onlyClassAttribute(RAbstractVector source) {
        RAttributes attributes = source.getAttributes();
        return attributes != null && attributes.size() == 1 && attributes.getNames()[0] == RRuntime.CLASS_ATTR_KEY;
    }

    @Specialization(guards = "onlyClassAttribute(source)")
    protected void copyClassOnly(RAbstractVector source, RVector target) {
        target.initAttributes(RAttributes.createInitialized(new String[]{RRuntime.CLASS_ATTR_KEY}, new Object[]{source.getAttributes().getValues()[0]}));
    }

    @Specialization
    protected void copyGeneric(RAbstractVector source, RVector target) {
        RAttributes orgAttributes = source.getAttributes();
        if (orgAttributes != null) {
            Object newRowNames = null;
            for (RAttribute e : orgAttributes) {
                String name = e.getName();
                if (name != RRuntime.DIM_ATTR_KEY && name != RRuntime.DIMNAMES_ATTR_KEY && name != RRuntime.NAMES_ATTR_KEY) {
                    Object val = e.getValue();
                    target.initAttributes().put(name, val);
                    if (name == RRuntime.ROWNAMES_ATTR_KEY) {
                        newRowNames = val;
                    }
                }
            }
            target.setInternalRowNames(newRowNames == null ? RNull.instance : newRowNames);
        }
    }
}
