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
package com.oracle.truffle.r.nodes.access;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.utilities.BranchProfile;
import com.oracle.truffle.api.utilities.ConditionProfile;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RAttributeProfiles;
import com.oracle.truffle.r.runtime.data.RLanguage;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RNode;

/**
 * Perform a field access. This node represents the {@code $} operator in R.
 */
@NodeChildren({@NodeChild(value = "object", type = RNode.class), @NodeChild(value = "field", type = RNode.class)})
public abstract class AccessFieldNode extends RNode {

    public abstract Object executeAccess(VirtualFrame frame, Object o, String field);

    protected final ConditionProfile hasNamesProfile = ConditionProfile.createBinaryProfile();
    protected final BranchProfile inexactMatch = BranchProfile.create();
    protected final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    @TruffleBoundary
    public static int getElementIndexByName(RStringVector names, String name) {
        for (int i = 0; i < names.getLength(); i++) {
            if (names.getDataAt(i).equals(name)) {
                return i;
            }
        }
        return -1;
    }

    @Specialization
    protected RNull access(@SuppressWarnings("unused") RNull object, @SuppressWarnings("unused") String field) {
        return RNull.instance;
    }

    @Specialization
    protected Object accessField(RList object, String field) {
        RStringVector names = object.getNames(attrProfiles);
        if (hasNamesProfile.profile(names != null)) {
            int index = getElementIndexByName(names, field);
            if (index == -1) {
                inexactMatch.enter();
                index = object.getElementIndexByNameInexact(attrProfiles, field);
            }
            return index == -1 ? RNull.instance : object.getDataAt(index);
        } else {
            return RNull.instance;
        }
    }

    @Specialization
    protected Object accessField(REnvironment env, String field) {
        Object obj = env.get(field);
        return obj == null ? RNull.instance : obj;
    }

    @Specialization
    protected Object accessField(@SuppressWarnings("unused") RAbstractVector objec, @SuppressWarnings("unused") String field) {
        throw RError.error(this, RError.Message.DOLLAR_ATOMIC_VECTORS);
    }

    @Specialization
    protected Object accessFieldHasNames(RLanguage object, String field) {
        RStringVector names = object.getNames(attrProfiles);
        if (hasNamesProfile.profile(names != null)) {
            int index = getElementIndexByName(names, field);
            return index == -1 ? RNull.instance : RContext.getRRuntimeASTAccess().getDataAtAsObject(object, index);
        } else {
            return RNull.instance;
        }
    }

    protected static Node createForeignRead() {
        return Message.READ.createNode();
    }

    protected static boolean isForeignObject(TruffleObject object) {
        return RRuntime.isForeignObject(object);
    }

    @Specialization(guards = "isForeignObject(object)")
    protected Object accessField(VirtualFrame frame, TruffleObject object, String field, @Cached("createForeignRead()") Node foreignRead) {
        return ForeignAccess.execute(foreignRead, frame, object, new Object[]{field});
    }

}
