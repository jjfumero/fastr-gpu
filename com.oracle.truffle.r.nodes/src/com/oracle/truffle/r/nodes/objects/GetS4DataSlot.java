/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995, 1996, 1997  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1995-2014, The R Core Team
 * Copyright (c) 2002-2008, The R Foundation
 * Copyright (c) 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.utilities.BranchProfile;
import com.oracle.truffle.r.nodes.attributes.AttributeAccess;
import com.oracle.truffle.r.nodes.attributes.AttributeAccessNodeGen;
import com.oracle.truffle.r.nodes.attributes.RemoveAttributeNode;
import com.oracle.truffle.r.nodes.attributes.RemoveAttributeNodeGen;
import com.oracle.truffle.r.nodes.unary.CastToVectorNode;
import com.oracle.truffle.r.nodes.unary.TypeofNode;
import com.oracle.truffle.r.nodes.unary.TypeofNodeGen;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RAttributes;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RS4Object;
import com.oracle.truffle.r.runtime.data.RShareable;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RTypedValue;
import com.oracle.truffle.r.runtime.nodes.RNode;

// transcribed from src/main/attrib.c
@NodeChildren({@NodeChild(value = "obj", type = RNode.class), @NodeChild(value = "type", type = RNode.class)})
public abstract class GetS4DataSlot extends RNode {

    public abstract RTypedValue executeObject(RAttributable attObj, RType type);

    @Child AttributeAccess s3ClassAttrAccess;
    @Child AttributeAccess dotDataAttrAccess;
    @Child AttributeAccess dotXDataAttrAccess;
    @Child RemoveAttributeNode s3ClassAttrRemove;
    @Child CastToVectorNode castToVector;
    @Child TypeofNode typeOf = TypeofNodeGen.create();
    private final BranchProfile nonNullAttr = BranchProfile.create();
    private final BranchProfile stillNonNullAttr = BranchProfile.create();
    private final BranchProfile shareable = BranchProfile.create();

    @Specialization
    protected RTypedValue doNewObject(RAttributable attrObj, RType type) {
        RAttributable obj = attrObj;
        Object value = null;
        if (!(obj instanceof RS4Object) || type == RType.S4Object) {
            Object s3Class = null;
            RAttributes attributes = obj.getAttributes();
            if (attributes != null) {
                nonNullAttr.enter();
                if (s3ClassAttrAccess == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    s3ClassAttrAccess = insert(AttributeAccessNodeGen.create(RRuntime.DOT_S3_CLASS));
                }
                s3Class = s3ClassAttrAccess.execute(attributes);
            }
            if (s3Class == null && type == RType.S4Object) {
                return RNull.instance;
            }
            if (obj instanceof RShareable && ((RShareable) obj).isShared()) {
                shareable.enter();
                obj = (RAttributable) ((RShareable) obj).copy();
            }
            if (s3Class != null) {
                if (s3ClassAttrRemove == null) {
                    assert castToVector == null;
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    s3ClassAttrRemove = insert(RemoveAttributeNodeGen.create(RRuntime.DOT_S3_CLASS));
                    castToVector = insert(CastToVectorNode.create());

                }
                s3ClassAttrRemove.execute(obj.initAttributes());
                obj = obj.setClassAttr((RStringVector) castToVector.execute(s3Class), false);
            } else {
                obj = obj.setClassAttr(null, false);
            }
            obj.unsetS4();
            if (type == RType.S4Object) {
                return obj;
            }
            value = obj;
        } else {
            RAttributes attributes = obj.getAttributes();
            if (attributes != null) {
                stillNonNullAttr.enter(); // could have been initialized
                if (dotDataAttrAccess == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    dotDataAttrAccess = insert(AttributeAccessNodeGen.create(RRuntime.DOT_DATA));
                }
                value = dotDataAttrAccess.execute(attributes);
            }
        }
        if (value == null) {
            RAttributes attributes = obj.getAttributes();
            if (attributes != null) {
                stillNonNullAttr.enter(); // could have been initialized
                if (dotXDataAttrAccess == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    dotXDataAttrAccess = insert(AttributeAccessNodeGen.create(RRuntime.DOT_XDATA));
                }
                value = dotXDataAttrAccess.execute(attributes);
            }
        }
        if (value != null && (type == RType.Any || type == typeOf.execute(value))) {
            return (RTypedValue) value;
        } else {
            return RNull.instance;
        }
    }

}
