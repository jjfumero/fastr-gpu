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

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

// oldClass<- (as opposed to class<-), simply sets the attribute (without handling "implicit" attributes)
@RBuiltin(name = "oldClass<-", kind = PRIMITIVE, parameterNames = {"x", ""})
// 2nd parameter is "value", but should not be matched against, so ""
public abstract class UpdateOldClass extends RInvisibleBuiltinNode {

    @Child private CastStringNode castStringNode;

    @Specialization(guards = "!isStringVector(className)")
    protected Object setOldClass(RAbstractContainer arg, RAbstractVector className) {
        controlVisibility();
        if (className.getLength() == 0) {
            return setOldClass(arg, RNull.instance);
        }
        initCastStringNode();
        Object result = castStringNode.execute(className);
        return setOldClass(arg, (RStringVector) result);
    }

    private void initCastStringNode() {
        if (castStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castStringNode = insert(CastStringNodeGen.create(false, false, false, false));
        }
    }

    @Specialization
    @TruffleBoundary
    protected Object setOldClass(RAbstractContainer arg, String className) {
        return setOldClass(arg, RDataFactory.createStringVector(className));
    }

    @Specialization
    @TruffleBoundary
    protected Object setOldClass(RAbstractContainer arg, RStringVector className) {
        controlVisibility();
        RAbstractContainer result = arg.materializeNonShared();
        return result.setClassAttr(className, false);
    }

    @Specialization
    @TruffleBoundary
    protected Object setOldClass(RAbstractContainer arg, @SuppressWarnings("unused") RNull className) {
        controlVisibility();
        RAbstractContainer result = arg.materializeNonShared();
        return result.setClassAttr(null, false);
    }

    protected boolean isStringVector(RAbstractVector className) {
        return className.getElementClass() == RString.class;
    }
}
