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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.RBuiltinKind;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RFactor;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;

@RBuiltin(name = "tabulate", kind = RBuiltinKind.INTERNAL, parameterNames = {"bin", "nbins"})
public abstract class Tabulate extends RBuiltinNode {

    @Override
    protected void createCasts(CastBuilder casts) {
        casts.toInteger(1);
    }

    @Specialization(guards = {"isValidNBin(nBins)"})
    @TruffleBoundary
    public RIntVector tabulate(RAbstractIntVector bin, int nBins) {
        controlVisibility();
        int[] ans = new int[nBins];
        for (int i = 0; i < bin.getLength(); i++) {
            int currentEl = bin.getDataAt(i);
            if (!RRuntime.isNA(currentEl) && currentEl > 0 && currentEl <= nBins) {
                ans[currentEl - 1]++;
            }
        }
        return RDataFactory.createIntVector(ans, RDataFactory.COMPLETE_VECTOR);
    }

    @Specialization(guards = {"isValidNBin(nBins)"})
    @TruffleBoundary
    public RIntVector tabulate(RFactor bin, int nBins) {
        return tabulate(bin.getVector(), nBins);
    }

    @SuppressWarnings("unused")
    @Specialization
    public RIntVector tabulate(Object bin, int nBins) {
        CompilerDirectives.transferToInterpreter();
        throw RError.error(this, RError.Message.INVALID_INPUT);
    }

    protected boolean isValidNBin(int nBins) {
        if (RRuntime.isNA(nBins) || nBins < 0) {
            throw RError.error(this, RError.Message.INVALID_ARGUMENT, "nbin");
        }
        return true;
    }
}
