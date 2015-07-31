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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

@RBuiltin(name = "attributes<-", kind = PRIMITIVE, parameterNames = {"obj", ""})
// 2nd parameter is "value", but should not be matched against, so ""
@SuppressWarnings("unused")
public abstract class UpdateAttributes extends RInvisibleBuiltinNode {
    private final ConditionProfile numAttributesProfile = ConditionProfile.createBinaryProfile();
    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    @Child private UpdateNames updateNames;
    @Child private UpdateDimNames updateDimNames;
    @Child private CastIntegerNode castInteger;
    @Child private CastToVectorNode castVector;
    @Child private CastListNode castList;

    // it's OK for the following two methods to update attributes in-place as the container has been
    // already materialized to non-shared

    private void updateNames(RAbstractContainer container, Object o) {
        if (updateNames == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            updateNames = insert(UpdateNamesNodeGen.create(new RNode[2], null, null));
        }
        updateNames.executeStringVector(container, o);
    }

    private void updateDimNames(RAbstractContainer container, Object o) {
        if (updateDimNames == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            updateDimNames = insert(UpdateDimNamesNodeGen.create(new RNode[2], null, null));
        }
        updateDimNames.executeRAbstractContainer(container, o);
    }

    private RAbstractIntVector castInteger(RAbstractVector vector) {
        if (castInteger == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castInteger = insert(CastIntegerNodeGen.create(true, false, false));
        }
        return (RAbstractIntVector) castInteger.execute(vector);
    }

    private RAbstractVector castVector(Object value) {
        if (castVector == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castVector = insert(CastToVectorNodeGen.create(false));
        }
        return (RAbstractVector) castVector.execute(value);
    }

    private RList castList(Object value) {
        if (castList == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castList = insert(CastListNodeGen.create(true, false, false));
        }
        return castList.executeList(value);
    }

    @Specialization
    protected RAbstractVector updateAttributes(RAbstractVector abstractVector, RNull list) {
        controlVisibility();
        RVector resultVector = abstractVector.materialize();
        resultVector.resetAllAttributes(true);
        return resultVector;
    }

    @Specialization
    protected RAbstractContainer updateAttributes(RAbstractContainer container, RList list) {
        controlVisibility();
        Object listNamesObject = list.getNames(attrProfiles);
        if (listNamesObject == null || listNamesObject == RNull.instance) {
            throw RError.error(this, RError.Message.ATTRIBUTES_NAMED);
        }
        RStringVector listNames = (RStringVector) listNamesObject;
        RAbstractContainer result = container.materializeNonShared();
        if (numAttributesProfile.profile(list.getLength() == 0)) {
            result.resetAllAttributes(true);
        } else {
            result.resetAllAttributes(false);
            // error checking is a little weird - seems easier to separate it than weave it into the
            // update loop
            if (listNames.getLength() > 1) {
                checkAttributeForEmptyValue(list);
            }
            // has to be reported if no other name is undefined
            if (listNames.getDataAt(0).equals(RRuntime.NAMES_ATTR_EMPTY_VALUE)) {
                throw RError.error(this, RError.Message.ZERO_LENGTH_VARIABLE);
            }
            // set the dim attribute first
            setDimAttribute(result, list);
            // set the remaining attributes in order
            result = setRemainingAttributes(container, result, list);
        }
        return result;
    }

    @TruffleBoundary
    @ExplodeLoop
    private void checkAttributeForEmptyValue(RList rlist) {
        RStringVector listNames = rlist.getNames(attrProfiles);
        int length = rlist.getLength();
        assert length > 0 : "Length should be > 0 for ExplodeLoop";
        for (int i = 1; i < length; i++) {
            String attrName = listNames.getDataAt(i);
            if (attrName.equals(RRuntime.NAMES_ATTR_EMPTY_VALUE)) {
                throw RError.error(this, RError.Message.ALL_ATTRIBUTES_NAMES, i + 1);
            }
        }
    }

    @ExplodeLoop
    private void setDimAttribute(RAbstractContainer result, RList sourceList) {
        RStringVector listNames = sourceList.getNames(attrProfiles);
        int length = sourceList.getLength();
        assert length > 0 : "Length should be > 0 for ExplodeLoop";
        for (int i = 0; i < sourceList.getLength(); i++) {
            Object value = sourceList.getDataAt(i);
            String attrName = listNames.getDataAt(i);
            if (attrName.equals(RRuntime.DIM_ATTR_KEY)) {
                if (value == RNull.instance) {
                    result.setDimensions(null);
                } else {
                    RAbstractIntVector dimsVector = castInteger(castVector(value));
                    if (dimsVector.getLength() == 0) {
                        throw RError.error(this, RError.Message.LENGTH_ZERO_DIM_INVALID);
                    }
                    result.setDimensions(dimsVector.materialize().getDataCopy());
                }
            }
        }
    }

    @ExplodeLoop
    private RAbstractContainer setRemainingAttributes(RAbstractContainer container, RAbstractContainer result, RList sourceList) {
        RStringVector listNames = sourceList.getNames(attrProfiles);
        int length = sourceList.getLength();
        assert length > 0 : "Length should be > 0 for ExplodeLoop";
        RAbstractContainer res = result;
        for (int i = 0; i < sourceList.getLength(); i++) {
            Object value = sourceList.getDataAt(i);
            String attrName = listNames.getDataAt(i);
            if (attrName.equals(RRuntime.DIM_ATTR_KEY)) {
                continue;
            } else if (attrName.equals(RRuntime.NAMES_ATTR_KEY)) {
                updateNames(res, value);
            } else if (attrName.equals(RRuntime.DIMNAMES_ATTR_KEY)) {
                updateDimNames(res, value);
            } else if (attrName.equals(RRuntime.CLASS_ATTR_KEY)) {
                if (value == RNull.instance) {
                    res = (RAbstractContainer) result.setClassAttr(null, false);
                } else {
                    res = (RAbstractContainer) result.setClassAttr(UpdateAttr.convertClassAttrFromObject(value), false);
                }
            } else if (attrName.equals(RRuntime.ROWNAMES_ATTR_KEY)) {
                res.setRowNames(castVector(value));
            } else {
                if (value == RNull.instance) {
                    res.removeAttr(attrProfiles, attrName);
                } else {
                    res.setAttr(attrName, value);
                }
            }
        }
        return res;
    }

    /**
     * All other, non-performance centric, {@link RAttributable} types, or error case.
     */
    @Fallback
    @TruffleBoundary
    public Object doOther(Object obj, Object operand) {
        controlVisibility();
        if (obj instanceof RAttributable) {
            RAttributable attrObj = (RAttributable) obj;
            attrObj.removeAllAttributes();
            if (operand == RNull.instance) {
                attrObj.setClassAttr(null, false);
            } else if (operand instanceof RList) {
                RList list = (RList) operand;
                RStringVector listNames = list.getNames(attrProfiles);
                if (listNames == null) {
                    throw RError.error(this, RError.Message.ATTRIBUTES_NAMED);
                }
                for (int i = 0; i < list.getLength(); i++) {
                    String attrName = listNames.getDataAt(i);
                    if (attrName == null) {
                        throw RError.error(this, RError.Message.ATTRIBUTES_NAMED);
                    }
                    if (attrName.equals(RRuntime.CLASS_ATTR_KEY)) {
                        Object attrValue = RRuntime.asString(list.getDataAt(i));
                        if (attrValue == null) {
                            throw RError.error(this, RError.Message.SET_INVALID_CLASS_ATTR);
                        }
                        attrObj.setClassAttr(RDataFactory.createStringVectorFromScalar((String) attrValue), false);
                    } else {
                        attrObj.setAttr(attrName, list.getDataAt(i));
                    }
                }
            } else {
                throw RError.error(this, RError.Message.ATTRIBUTES_LIST_OR_NULL);
            }
        } else {
            throw RError.error(this, RError.Message.INVALID_OR_UNIMPLEMENTED_ARGUMENTS);
        }
        return obj;
    }

}
