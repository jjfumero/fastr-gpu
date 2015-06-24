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
package com.oracle.truffle.r.nodes.function;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.attributes.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;

public abstract class ClassHierarchyNode extends UnaryNode {

    @Child private AttributeAccess access;

    private final boolean withImplicitTypes;
    private final ConditionProfile noAttributesProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile nullAttributeProfile = ConditionProfile.createBinaryProfile();
    private final ValueProfile typeProfile;

    protected ClassHierarchyNode(boolean withImplicitTypes) {
        this.withImplicitTypes = withImplicitTypes;
        this.typeProfile = withImplicitTypes ? ValueProfile.createClassProfile() : null;
    }

    @Override
    public abstract RStringVector execute(Object arg);

    @Specialization
    protected RStringVector getClassHr(@SuppressWarnings("unused") byte arg) {
        return RDataFactory.createStringVector(RType.Logical.getName());
    }

    @Specialization
    protected RStringVector getClassHr(@SuppressWarnings("unused") String arg) {
        return RDataFactory.createStringVector(RType.Character.getName());
    }

    @Specialization
    protected RStringVector getClassHr(@SuppressWarnings("unused") int arg) {
        return RDataFactory.createStringVector(RType.Integer.getName());
    }

    @Specialization
    protected RStringVector getClassHr(@SuppressWarnings("unused") double arg) {
        return RDataFactory.createStringVector(RRuntime.CLASS_DOUBLE, RDataFactory.COMPLETE_VECTOR);
    }

    @Specialization
    protected RStringVector getClassHr(@SuppressWarnings("unused") RNull arg) {
        return RDataFactory.createStringVector(RType.Null.getName());
    }

    @Specialization
    protected RStringVector getClassHr(@SuppressWarnings("unused") RFormula arg) {
        return RDataFactory.createStringVector(RType.Formula.getName());
    }

    @Specialization
    protected RStringVector getClassHrStorage(RAttributeStorage arg, //
                    @Cached("createClassProfile()") ValueProfile argProfile) {
        return getClassHrAttributable(arg, argProfile);
    }

    @Specialization(contains = "getClassHrStorage")
    protected RStringVector getClassHrAttributable(RAttributable arg, //
                    @Cached("createClassProfile()") ValueProfile argProfile) {
        RAttributes attributes = argProfile.profile(arg).getAttributes();
        if (noAttributesProfile.profile(attributes != null)) {
            if (access == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                access = insert(AttributeAccessNodeGen.create(RRuntime.CLASS_ATTR_KEY));
            }
            RStringVector classHierarcy = (RStringVector) access.execute(attributes);
            if (nullAttributeProfile.profile(classHierarcy != null)) {
                return classHierarcy;
            }
        }
        return withImplicitTypes ? typeProfile.profile(arg).getImplicitClass() : null;
    }

    @Fallback
    protected RStringVector getClassHr(Object obj) {
        throw RInternalError.shouldNotReachHere("type: " + obj.getClass());
    }
}