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

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.conn.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

/**
 * Handles all builtin functions of the form {@code is.xxx}, where is {@code xxx} is a "type".
 */
@SuppressWarnings("unused")
public class IsTypeFunctions {

    protected abstract static class ErrorAdapter extends RBuiltinNode {
        protected final BranchProfile errorProfile = BranchProfile.create();

        protected RError missingError() throws RError {
            errorProfile.enter();
            throw RError.error(this, RError.Message.ARGUMENT_MISSING, "x");
        }
    }

    protected abstract static class MissingAdapter extends ErrorAdapter {

        @Specialization
        protected byte isType(RMissing value) throws RError {
            controlVisibility();
            throw missingError();
        }
    }

    @RBuiltin(name = "is.array", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsArray extends MissingAdapter {

        @Specialization
        protected byte isType(RAbstractVector vector) {
            controlVisibility();
            return RRuntime.asLogical(vector.isArray());
        }

        @Specialization(guards = {"!isRMissing(value)", "!isRAbstractVector(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }

    }

    @RBuiltin(name = "is.recursive", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsRecursive extends MissingAdapter {

        @Specialization
        protected byte isRecursive(RNull arg) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }

        @Specialization(guards = "!isListVector(arg)")
        protected byte isRecursive(RAbstractVector arg) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }

        @Specialization
        protected byte isRecursive(RList arg) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization
        protected byte isRecursive(RFactor arg) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }

        protected boolean isListVector(RAbstractVector arg) {
            return arg instanceof RList;
        }

        @Fallback
        protected byte isRecursiveFallback(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }
    }

    @RBuiltin(name = "is.atomic", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsAtomic extends MissingAdapter {

        @Specialization
        protected byte isAtomic(RNull arg) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization(guards = "!isRList(arg)")
        protected byte isAtomic(RAbstractVector arg) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization
        protected byte isAtomic(RFactor arg) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        protected static boolean isNonListVector(Object value) {
            return value instanceof Integer || value instanceof Double || value instanceof RComplex || value instanceof String || value instanceof RRaw ||
                            (value instanceof RAbstractVector && !(value instanceof RList));
        }

        @Specialization(guards = {"!isRMissing(value)", "!isRNull(value)", "!isRFactor(value)", "!isNonListVector(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.call", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsCall extends MissingAdapter {

        @Specialization
        protected byte isType(RLanguage lang) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isRLanguage(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.character", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsCharacter extends MissingAdapter {

        @Specialization
        protected byte isType(RAbstractStringVector value) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        protected static boolean isAnyCharacter(Object value) {
            return value instanceof String || value instanceof RAbstractStringVector;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isAnyCharacter(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.complex", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsComplex extends MissingAdapter {

        @Specialization
        protected byte isType(RAbstractComplexVector value) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        protected static boolean isAnyComplex(Object value) {
            return value instanceof RComplex || value instanceof RAbstractComplexVector;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isAnyComplex(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.double", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsDouble extends MissingAdapter {

        @Specialization
        protected byte isType(RAbstractDoubleVector value) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        protected static boolean isAnyDouble(Object value) {
            return value instanceof Double || value instanceof RAbstractDoubleVector;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isAnyDouble(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.expression", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsExpression extends MissingAdapter {

        @Specialization
        protected byte isType(RExpression expr) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isRExpression(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.function", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsFunction extends MissingAdapter {

        @Specialization
        protected byte isType(RFunction value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isRFunction(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.integer", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsInteger extends MissingAdapter {

        @Specialization
        protected byte isType(RAbstractIntVector value) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        protected static boolean isAnyInteger(Object value) {
            return value instanceof Integer || value instanceof RAbstractIntVector;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isAnyInteger(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.language", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsLanguage extends MissingAdapter {
        @Specialization
        protected byte isType(RSymbol value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization
        protected byte isType(RExpression value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization
        protected byte isType(RLanguage value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isRSymbol(value)", "!isRExpression(value)", "!isRLanguage(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.list", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsList extends MissingAdapter {

        private final ConditionProfile isListProfile = ConditionProfile.createBinaryProfile();

        @Specialization
        protected byte isType(RList value) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization
        protected byte isType(RDataFrame value) {
            controlVisibility();
            return RRuntime.asLogical(isListProfile.profile(value.getVector() instanceof RList));
        }

        @Specialization
        protected byte isType(RPairList pl) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isRList(value)", "!isRDataFrame(value)", "!isRPairList(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.logical", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsLogical extends MissingAdapter {

        @Specialization
        protected byte isType(RAbstractLogicalVector value) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        protected static boolean isAnyLogical(Object value) {
            return value instanceof Byte || value instanceof RAbstractLogicalVector;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isAnyLogical(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.matrix", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsMatrix extends MissingAdapter {

        private final ConditionProfile isMatrixProfile = ConditionProfile.createBinaryProfile();

        @Specialization
        protected byte isType(RAbstractVector vector) {
            controlVisibility();
            return RRuntime.asLogical(isMatrixProfile.profile(vector.isMatrix()));
        }

        @Specialization(guards = {"!isRMissing(value)", "!isRAbstractVector(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.name", aliases = {"is.symbol"}, kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsName extends MissingAdapter {

        @Specialization
        protected byte isType(RSymbol value) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isRSymbol(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.numeric", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsNumeric extends MissingAdapter {

        @Specialization
        protected byte isType(RAbstractIntVector value) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization
        protected byte isType(RAbstractDoubleVector value) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        protected static boolean isAnyNumeric(Object value) {
            return value instanceof Integer || value instanceof Double || value instanceof RAbstractIntVector || value instanceof RAbstractDoubleVector;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isAnyNumeric(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.null", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsNull extends MissingAdapter {

        @Specialization
        protected byte isType(RNull value) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isRNull(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    /**
     * The specification is not quite what you might expect. Several builtin types, e.g.,
     * {@code expression} respond to {@code class(e)} but return {@code FALSE} to {@code is.object}.
     * Essentially, this method should only return {@code TRUE} if a {@code class} attribute has
     * been added explicitly to the object. If the attribute is removed, it should return
     * {@code FALSE}.
     */
    @RBuiltin(name = "is.object", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsObject extends MissingAdapter {

        private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

        @Specialization
        protected byte isObject(RAbstractContainer arg) {
            controlVisibility();
            return arg.isObject(attrProfiles) ? RRuntime.LOGICAL_TRUE : RRuntime.LOGICAL_FALSE;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isRAbstractContainer(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.pairlist", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsPairList extends MissingAdapter {
        @Specialization
        protected byte isType(RNull value) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization
        protected byte isType(RPairList value) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isRNull(value)", "!isRPairList(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.raw", kind = PRIMITIVE, parameterNames = {"x"})
    public abstract static class IsRaw extends MissingAdapter {

        @Specialization
        protected byte isType(RAbstractRawVector value) {
            controlVisibility();
            return RRuntime.LOGICAL_TRUE;
        }

        protected static boolean isAnyRaw(Object value) {
            return value instanceof RRaw || value instanceof RAbstractRawVector;
        }

        @Specialization(guards = {"!isRMissing(value)", "!isAnyRaw(value)"})
        protected byte isType(Object value) {
            controlVisibility();
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.vector", kind = INTERNAL, parameterNames = {"x", "mode"})
    public abstract static class IsVector extends ErrorAdapter {

        private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

        @Override
        public Object[] getDefaultParameterValues() {
            // INTERNAL does not need default parameters
            return RNode.EMPTY_OBJECT_ARRAY;
        }

        @Specialization
        protected byte isVector(RMissing value, String mode) {
            controlVisibility();
            throw missingError();
        }

        @Specialization
        protected byte isVector(RAbstractVector x, String mode) {
            controlVisibility();
            if (!namesOnlyOrNoAttr(x) || !modeIsAnyOrMatches(x, mode)) {
                return RRuntime.LOGICAL_FALSE;
            } else {
                return RRuntime.LOGICAL_TRUE;
            }
        }

        @Fallback
        protected byte isVector(Object x, Object mode) {
            return RRuntime.LOGICAL_FALSE;
        }

        protected boolean namesOnlyOrNoAttr(RAbstractVector x) {
            // there should be no attributes other than names
            if (x.getNames(attrProfiles) == null) {
                assert x.getAttributes() == null || x.getAttributes().size() > 0;
                return x.getAttributes() == null ? true : false;
            } else {
                assert x.getAttributes() != null;
                return x.getAttributes().size() == 1 ? true : false;
            }
        }

        protected boolean modeIsAnyOrMatches(RAbstractVector x, String mode) {
            return RType.Any.getName().equals(mode) || (x instanceof RList && mode.equals("list")) || (x.getElementClass() == RDouble.class && RType.Double.getName().equals(mode)) ||
                            RRuntime.classToString(x.getElementClass()).equals(mode);
        }
    }

}
