/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995-2012, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.builtin.base.foreign;

import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ffi.*;

public final class Dqrdc2 extends RExternalBuiltinNode {

    private static final String E = RRuntime.NAMES_ATTR_EMPTY_VALUE;
    private static final RStringVector DQRDC2_NAMES = RDataFactory.createStringVector(new String[]{"qr", E, E, E, E, "rank", "qraux", "pivot", E}, RDataFactory.COMPLETE_VECTOR);

    @Override
    public RList call(RArgsValuesAndNames args) {
        Object[] argValues = args.getArguments();
        try {
            RAbstractDoubleVector xVec = (RAbstractDoubleVector) argValues[0];
            int ldx = (int) argValues[1];
            int n = (int) argValues[2];
            int p = (int) argValues[3];
            double tol = (double) argValues[4];
            RAbstractIntVector rankVec = (RAbstractIntVector) argValues[5];
            RAbstractDoubleVector qrauxVec = (RAbstractDoubleVector) argValues[6];
            RAbstractIntVector pivotVec = (RAbstractIntVector) argValues[7];
            RAbstractDoubleVector workVec = (RAbstractDoubleVector) argValues[8];
            double[] x = xVec.materialize().getDataTemp();
            int[] rank = rankVec.materialize().getDataTemp();
            double[] qraux = qrauxVec.materialize().getDataTemp();
            int[] pivot = pivotVec.materialize().getDataTemp();
            RFFIFactory.getRFFI().getRApplRFFI().dqrdc2(x, ldx, n, p, tol, rank, qraux, pivot, workVec.materialize().getDataCopy());
            // @formatter:off
            Object[] data = new Object[]{
                        RDataFactory.createDoubleVector(x, RDataFactory.COMPLETE_VECTOR, xVec.getDimensions()),
                        argValues[1], argValues[2], argValues[3], argValues[4],
                        RDataFactory.createIntVector(rank, RDataFactory.COMPLETE_VECTOR),
                        RDataFactory.createDoubleVector(qraux, RDataFactory.COMPLETE_VECTOR),
                        RDataFactory.createIntVector(pivot, RDataFactory.COMPLETE_VECTOR),
                        argValues[8]
            };
            // @formatter:on
            return RDataFactory.createList(data, DQRDC2_NAMES);
        } catch (ClassCastException | ArrayIndexOutOfBoundsException ex) {
            errorProfile.enter();
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.INCORRECT_ARG, "dqrdc2");
        }
    }
}
