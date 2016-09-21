/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */

package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.attributes.TypeFromModeNode;
import com.oracle.truffle.r.nodes.binary.CastTypeNode;
import com.oracle.truffle.r.nodes.binary.CastTypeNodeGen;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.unary.CastStringNode;
import com.oracle.truffle.r.nodes.unary.CastStringNodeGen;
import com.oracle.truffle.r.nodes.unary.TypeofNode;
import com.oracle.truffle.r.nodes.unary.TypeofNodeGen;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RAttributeProfiles;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RExternalPtr;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RString;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.model.RAbstractStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.env.REnvironment;

@RBuiltin(name = "class<-", kind = PRIMITIVE, parameterNames = {"x", ""})
// 2nd parameter is "value", but should not be matched against, so ""
public abstract class UpdateClass extends RBuiltinNode {

    protected static final int CACHE_LIMIT = 2;

    @Child private CastTypeNode castTypeNode;
    @Child private CastStringNode castStringNode;
    @Child private TypeofNode typeof;

    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    @Specialization(guards = "!isStringVector(className)")
    protected Object setClass(RAbstractContainer arg, RAbstractVector className) {
        controlVisibility();
        if (className.getLength() == 0) {
            return setClass(arg, RNull.instance);
        }
        initCastStringNode();
        Object result = castStringNode.execute(className);
        return setClass(arg, (RStringVector) result);
    }

    private void initCastStringNode() {
        if (castStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castStringNode = insert(CastStringNodeGen.create(false, false, false, false));
        }
    }

    @Specialization
    @TruffleBoundary
    protected Object setClass(RAbstractContainer arg, @SuppressWarnings("unused") RNull className) {
        controlVisibility();

        RAbstractContainer result = arg.materializeNonShared();
        return result.setClassAttr(null, false);
    }

    @Specialization(limit = "CACHE_LIMIT", guards = "cachedClassName == className")
    protected Object setClassCached(RAbstractContainer arg, @SuppressWarnings("unused") String className, //
                    @Cached("className") String cachedClassName, //
                    @Cached("fromMode(className)") RType cachedMode) {
        return setClassInternal(arg, cachedClassName, cachedMode);
    }

    @Specialization(contains = "setClassCached")
    protected Object setClass(RAbstractContainer arg, String className, //
                    @Cached("create()") TypeFromModeNode typeFromMode) {
        controlVisibility();
        RType mode = typeFromMode.execute(className);
        return setClassInternal(arg, className, mode);
    }

    private Object setClassInternal(RAbstractContainer arg, String className, RType mode) {
        if (!arg.isObject(attrProfiles)) {
            initTypeof();
            RType argType = typeof.execute(arg);
            if (argType.equals(className) || (mode == RType.Numeric && (argType == RType.Integer || argType == RType.Double))) {
                // "explicit" attribute might have been set (e.g. by oldClass<-)
                return setClass(arg, RNull.instance);
            }
        }
        if (mode != null) {
            initCastTypeNode();
            Object result = castTypeNode.execute(arg, mode);
            if (result != null) {
                return setClass((RAbstractVector) result, RNull.instance);
            }
        }
        RAbstractContainer result = arg.materializeNonShared();
        if (result instanceof RAbstractVector) {
            RAbstractVector resultVector = (RAbstractVector) result;
            if (RType.Matrix.getName().equals(className)) {
                if (resultVector.isMatrix()) {
                    return setClass(resultVector, RNull.instance);
                }
                CompilerDirectives.transferToInterpreter();
                int[] dimensions = resultVector.getDimensions();
                throw RError.error(this, RError.Message.NOT_A_MATRIX_UPDATE_CLASS, dimensions == null ? 0 : dimensions.length);
            }
            if (RType.Array.getName().equals(className)) {
                if (resultVector.isArray()) {
                    return setClass(resultVector, RNull.instance);
                }
                CompilerDirectives.transferToInterpreter();
                throw RError.error(this, RError.Message.NOT_ARRAY_UPDATE_CLASS);
            }
        }

        return result.setClassAttr(RDataFactory.createStringVector(className), false);
    }

    @Specialization
    @TruffleBoundary
    protected Object setClass(RAbstractContainer arg, RStringVector className) {
        controlVisibility();
        RAbstractContainer result = arg.materializeNonShared();
        return result.setClassAttr(className, false);
    }

    @Specialization
    public Object setClass(RFunction arg, RAbstractStringVector className) {
        controlVisibility();
        arg.setClassAttr(className.materialize(), false);
        return arg;
    }

    @Specialization
    public Object setClass(RFunction arg, @SuppressWarnings("unused") RNull className) {
        controlVisibility();
        arg.setClassAttr(null, false);
        return arg;
    }

    @Specialization
    public Object setClass(REnvironment arg, RAbstractStringVector className) {
        controlVisibility();
        arg.setClassAttr(className.materialize(), false);
        return arg;
    }

    @Specialization
    public Object setClass(REnvironment arg, @SuppressWarnings("unused") RNull className) {
        controlVisibility();
        arg.setClassAttr(null, false);
        return arg;
    }

    @Specialization
    public Object setClass(RSymbol arg, RAbstractStringVector className) {
        controlVisibility();
        arg.setClassAttr(className.materialize(), false);
        return arg;
    }

    @Specialization
    public Object setClass(RSymbol arg, @SuppressWarnings("unused") RNull className) {
        controlVisibility();
        arg.setClassAttr(null, false);
        return arg;
    }

    @Specialization
    public Object setClass(RExternalPtr arg, RAbstractStringVector className) {
        controlVisibility();
        arg.setClassAttr(className.materialize(), false);
        return arg;
    }

    @Specialization
    public Object setClass(RExternalPtr arg, @SuppressWarnings("unused") RNull className) {
        controlVisibility();
        arg.setClassAttr(null, false);
        return arg;
    }

    private void initCastTypeNode() {
        if (castTypeNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castTypeNode = insert(CastTypeNodeGen.create(null, null));
        }
    }

    private void initTypeof() {
        if (typeof == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            typeof = insert(TypeofNodeGen.create());
        }
    }

    protected boolean isStringVector(RAbstractVector className) {
        return className.getElementClass() == RString.class;
    }
}
