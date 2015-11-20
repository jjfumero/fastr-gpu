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

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

@RBuiltin(name = "as.vector", kind = INTERNAL, parameterNames = {"x", "mode"})
public abstract class AsVector extends RBuiltinNode {

    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    @Override
    protected void createCasts(CastBuilder casts) {
        casts.firstStringWithError(1, RError.Message.INVALID_ARGUMENT, "mode");
    }

    @Specialization
    protected Object asVector(RNull x, @SuppressWarnings("unused") RMissing mode) {
        controlVisibility();
        return x;
    }

    @Specialization(guards = "castToString(mode)")
    protected Object asVectorString(Object x, @SuppressWarnings("unused") String mode, //
                    @Cached("create()") AsCharacter asCharacter) {
        controlVisibility();
        return asCharacter.execute(x);
    }

    @Specialization(guards = "castToInt(x, mode)")
    protected Object asVectorInt(RAbstractContainer x, @SuppressWarnings("unused") String mode, //
                    @Cached("createNonPreserving()") CastIntegerNode cast) {
        controlVisibility();
        return cast.execute(x);
    }

    @Specialization(guards = "castToDouble(x, mode)")
    protected Object asVectorDouble(RAbstractContainer x, @SuppressWarnings("unused") String mode, //
                    @Cached("createNonPreserving()") CastDoubleNode cast) {
        controlVisibility();
        return cast.execute(x);
    }

    @Specialization(guards = "castToComplex(x, mode)")
    protected Object asVectorComplex(RAbstractContainer x, @SuppressWarnings("unused") String mode, //
                    @Cached("createNonPreserving()") CastComplexNode cast) {
        controlVisibility();
        return cast.execute(x);
    }

    @Specialization(guards = "castToLogical(x, mode)")
    protected Object asVectorLogical(RAbstractContainer x, @SuppressWarnings("unused") String mode, //
                    @Cached("createNonPreserving()") CastLogicalNode cast) {
        controlVisibility();
        return cast.execute(x);
    }

    @Specialization(guards = "castToRaw(x, mode)")
    protected Object asVectorRaw(RAbstractContainer x, @SuppressWarnings("unused") String mode, //
                    @Cached("createNonPreserving()") CastRawNode cast) {
        controlVisibility();
        return cast.execute(x);
    }

    protected static CastListNode createListCast() {
        return CastListNodeGen.create(true, false, false);
    }

    @Specialization(guards = "castToList(mode)")
    protected Object asVectorList(RAbstractContainer x, @SuppressWarnings("unused") String mode, //
                    @Cached("createListCast()") CastListNode cast) {
        controlVisibility();
        return cast.execute(x);
    }

    @Specialization(guards = "castToSymbol(x, mode)")
    protected Object asVectorSymbol(RAbstractContainer x, @SuppressWarnings("unused") String mode, //
                    @Cached("createNonPreserving()") CastSymbolNode cast) {
        controlVisibility();
        return cast.execute(x);
    }

    @Specialization(guards = "castToExpression(mode)")
    protected Object asVectorExpression(Object x, @SuppressWarnings("unused") String mode, //
                    @Cached("createNonPreserving()") CastExpressionNode cast) {
        controlVisibility();
        return cast.execute(x);
    }

    @Specialization(guards = "castToList(mode)")
    protected RAbstractVector asVectorList(@SuppressWarnings("unused") RNull x, @SuppressWarnings("unused") String mode) {
        controlVisibility();
        return RDataFactory.createList();
    }

    @Specialization(guards = "isSymbol(x, mode)")
    protected RSymbol asVectorSymbol(RSymbol x, @SuppressWarnings("unused") String mode) {
        controlVisibility();
        String sName = x.getName();
        return RDataFactory.createSymbol(sName);
    }

    protected boolean isSymbol(@SuppressWarnings("unused") RSymbol x, String mode) {
        return RType.Symbol.getName().equals(mode);
    }

    @Specialization(guards = "modeIsAny(mode)")
    protected RAbstractVector asVector(RList x, @SuppressWarnings("unused") String mode) {
        controlVisibility();
        RList result = x.copyWithNewDimensions(null);
        result.copyNamesFrom(attrProfiles, x);
        return result;
    }

    @Specialization(guards = "modeIsAny(mode)")
    protected RAbstractVector asVector(RFactor x, @SuppressWarnings("unused") String mode) {
        RVector levels = x.getLevels(attrProfiles);
        RVector result = levels.createEmptySameType(x.getLength(), RDataFactory.COMPLETE_VECTOR);
        RIntVector factorData = x.getVector();
        for (int i = 0; i < result.getLength(); i++) {
            result.transferElementSameType(i, levels, factorData.getDataAt(i) - 1);
        }
        return result;
    }

    @Specialization(guards = "modeIsAny(mode)")
    protected RNull asVector(RNull x, @SuppressWarnings("unused") String mode) {
        controlVisibility();
        return x;
    }

    @Specialization(guards = "modeIsPairList(mode)")
    protected Object asVectorPairList(RList x, @SuppressWarnings("unused") String mode) {
        controlVisibility();
        // TODO implement non-empty element list conversion; this is a placeholder for type test
        if (x.getLength() == 0) {
            return RNull.instance;
        } else {
            throw RError.nyi(this, "non-empty lists");
        }
    }

    @Specialization(guards = "modeIsAny(mode)")
    protected RAbstractVector asVectorAny(RAbstractVector x, @SuppressWarnings("unused") String mode) {
        controlVisibility();
        return x.copyWithNewDimensions(null);
    }

    @Specialization(guards = "modeMatches(x, mode)")
    protected RAbstractVector asVector(RAbstractVector x, @SuppressWarnings("unused") String mode) {
        controlVisibility();
        return x.copyWithNewDimensions(null);
    }

    protected boolean castToInt(RAbstractContainer x, String mode) {
        return x.getElementClass() != RInteger.class && RType.Integer.getName().equals(mode);
    }

    protected boolean castToDouble(RAbstractContainer x, String mode) {
        return x.getElementClass() != RDouble.class && (RType.Numeric.getName().equals(mode) || RType.Double.getName().equals(mode));
    }

    protected boolean castToComplex(RAbstractContainer x, String mode) {
        return x.getElementClass() != RComplex.class && RType.Complex.getName().equals(mode);
    }

    protected boolean castToLogical(RAbstractContainer x, String mode) {
        return x.getElementClass() != RLogical.class && RType.Logical.getName().equals(mode);
    }

    protected boolean castToString(RAbstractContainer x, String mode) {
        return x.getElementClass() != RString.class && RType.Character.getName().equals(mode);
    }

    protected boolean castToString(String mode) {
        return RType.Character.getName().equals(mode);
    }

    protected boolean castToRaw(RAbstractContainer x, String mode) {
        return x.getElementClass() != RRaw.class && RType.Raw.getName().equals(mode);
    }

    protected boolean castToList(RAbstractContainer x, String mode) {
        return x.getElementClass() != Object.class && RType.List.getName().equals(mode);
    }

    protected boolean castToList(String mode) {
        return RType.List.getName().equals(mode);
    }

    protected boolean castToSymbol(RAbstractContainer x, String mode) {
        return x.getElementClass() != Object.class && RType.Symbol.getName().equals(mode);
    }

    protected boolean castToExpression(String mode) {
        return RType.Expression.getName().equals(mode);
    }

    protected boolean modeMatches(RAbstractVector x, String mode) {
        return RRuntime.classToString(x.getElementClass()).equals(mode) || x.getElementClass() == RDouble.class && RType.Double.getName().equals(mode);
    }

    protected boolean modeIsAny(String mode) {
        return RType.Any.getName().equals(mode);
    }

    protected boolean modeIsPairList(String mode) {
        return RType.PairList.getName().equals(mode);
    }

    @SuppressWarnings("unused")
    @Fallback
    protected RAbstractVector asVectorWrongMode(Object x, Object mode) {
        controlVisibility();
        throw RError.error(this, RError.Message.INVALID_ARGUMENT, "mode");
    }
}
