/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.unary;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

public abstract class CastExpressionNode extends CastNode {

    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    public abstract Object executeExpression(VirtualFrame frame, Object o);

    @Specialization
    protected RExpression doNull(@SuppressWarnings("unused") RNull value) {
        return create(RNull.instance);
    }

    @Specialization
    protected RExpression doDouble(double value) {
        return create(value);
    }

    @Specialization
    protected RExpression doInt(int value) {
        return create(value);
    }

    @Specialization
    protected RExpression doLogical(byte value) {
        return create(value);
    }

    @Specialization
    protected RExpression doSymbol(RSymbol value) {
        return create(value);
    }

    @Specialization
    protected RExpression doFunction(RFunction value) {
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.CANNOT_COERCE, value.isBuiltin() ? "builtin" : "closure", "expression");
    }

    @Specialization
    protected RExpression doExpression(RExpression value) {
        return value;
    }

    @Specialization
    protected RExpression doAbstractContainer(RAbstractContainer obj) {
        int len = obj.getLength();
        Object[] data = new Object[len];
        for (int i = 0; i < len; i++) {
            data[i] = obj.getDataAtAsObject(i);
        }
        if (obj instanceof RList) {
            RList list = (RList) obj;
            // TODO other attributes
            return RDataFactory.createExpression(RDataFactory.createList(data, list.getNames(attrProfiles)));
        } else {
            return create(data);
        }
    }

    private static RExpression create(Object obj) {
        return create(new Object[]{obj});
    }

    private static RExpression create(Object[] objArray) {
        return RDataFactory.createExpression(RDataFactory.createList(objArray));
    }

}
