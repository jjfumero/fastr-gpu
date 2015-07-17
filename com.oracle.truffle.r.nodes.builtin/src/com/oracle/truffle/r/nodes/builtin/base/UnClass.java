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

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.BranchProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import static com.oracle.truffle.r.runtime.RBuiltinKind.PRIMITIVE;

@RBuiltin(name = "unclass", kind = PRIMITIVE, parameterNames = {"x"})
public abstract class UnClass extends RBuiltinNode {
    private final BranchProfile objectProfile = BranchProfile.create();
    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    @Specialization
    @TruffleBoundary
    protected Object unClass(RAbstractVector arg) {
        controlVisibility();
        if (arg.isObject(attrProfiles)) {
            objectProfile.enter();
            RVector resultVector = arg.materialize();
            if (!resultVector.isTemporary()) {
                resultVector = resultVector.copy();
                if (FastROptions.NewStateTransition) {
                    resultVector.incRefCount();
                } else {
                    resultVector.markNonTemporary();
                }
            }
            return RVector.setVectorClassAttr(resultVector, null, null, null);
        }
        return arg;
    }

    @Specialization
    @TruffleBoundary
    protected Object unClass(RDataFrame arg) {
        controlVisibility();
        RDataFrame resultFrame = arg;
        if (!resultFrame.isTemporary()) {
            resultFrame = resultFrame.copy();
            if (FastROptions.NewStateTransition) {
                resultFrame.incRefCount();
            } else {
                resultFrame.markNonTemporary();
            }
        }
        return RVector.setVectorClassAttr(resultFrame.getVector(), null, arg, null);
    }

    @Specialization
    @TruffleBoundary
    protected Object unClass(RFactor arg) {
        controlVisibility();
        RFactor resultFactor = arg;
        if (!resultFactor.isTemporary()) {
            resultFactor = resultFactor.copy();
            if (FastROptions.NewStateTransition) {
                resultFactor.incRefCount();
            } else {
                resultFactor.markNonTemporary();
            }
        }
        return RVector.setVectorClassAttr(resultFactor.getVector(), null, null, arg);
    }

    @Specialization
    protected Object unClass(RLanguage arg) {
        controlVisibility();
        if (arg.getClassAttr(attrProfiles) != null) {
            objectProfile.enter();
            RLanguage resultLang = arg;
            if (!resultLang.isTemporary()) {
                resultLang = resultLang.copy();
                if (FastROptions.NewStateTransition) {
                    resultLang.incRefCount();
                } else {
                    resultLang.markNonTemporary();
                }
            }
            resultLang.removeAttr(attrProfiles, RRuntime.CLASS_ATTR_KEY);
            return resultLang;
        }
        return arg;
    }
}
