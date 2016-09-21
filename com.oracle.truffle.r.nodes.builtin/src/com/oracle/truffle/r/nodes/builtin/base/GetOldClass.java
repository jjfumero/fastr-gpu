/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.ConditionProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.data.RAttributeProfiles;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

@RBuiltin(name = "oldClass", kind = PRIMITIVE, parameterNames = {"x"})
public abstract class GetOldClass extends RBuiltinNode {

    private final ConditionProfile isObjectProfile = ConditionProfile.createBinaryProfile();
    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    @Specialization
    protected Object getOldClass(RAbstractContainer arg) {
        controlVisibility();
        if (isObjectProfile.profile(arg.isObject(attrProfiles))) {
            return arg.getClassHierarchy();
        } else {
            return RNull.instance;
        }
    }

    @Specialization
    protected Object getOldClass(@SuppressWarnings("unused") RFunction arg) {
        controlVisibility();
        return RNull.instance;
    }

    @Specialization
    protected Object getOldClass(@SuppressWarnings("unused") RNull arg) {
        controlVisibility();
        return RNull.instance;
    }

    public abstract Object execute(VirtualFrame frame, RAbstractVector o);
}
