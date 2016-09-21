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

import com.oracle.truffle.api.utilities.ConditionProfile;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.ffi.RFFIFactory;

public final class Fft extends RExternalBuiltinNode {

    private final ConditionProfile zVecLgt1 = ConditionProfile.createBinaryProfile();
    private final ConditionProfile noDims = ConditionProfile.createBinaryProfile();

    // TODO: handle more argument types (this is sufficient to run the b25 benchmarks)
    @Override
    public RComplexVector call(RArgsValuesAndNames args) {
        Object[] argValues = args.getArguments();
        RComplexVector zVec = castComplexVector(castVector(argValues[0]));
        double[] z = zVec.getDataTemp();
        byte inverse = castLogical(castVector(argValues[1]));
        int inv = RRuntime.isNA(inverse) || inverse == RRuntime.LOGICAL_FALSE ? -2 : 2;
        @SuppressWarnings("unused")
        int retCode = 7;
        if (zVecLgt1.profile(zVec.getLength() > 1)) {
            int[] maxf = new int[1];
            int[] maxp = new int[1];
            if (noDims.profile(zVec.getDimensions() == null)) {
                int n = zVec.getLength();
                RFFIFactory.getRFFI().getStatsRFFI().fft_factor(n, maxf, maxp);
                if (maxf[0] == 0) {
                    errorProfile.enter();
                    throw RError.error(this, RError.Message.FFT_FACTORIZATION);
                }
                double[] work = new double[4 * maxf[0]];
                int[] iwork = new int[maxp[0]];
                retCode = RFFIFactory.getRFFI().getStatsRFFI().fft_work(z, 1, n, 1, inv, work, iwork);
            } else {
                int maxmaxf = 1;
                int maxmaxp = 1;
                int[] d = zVec.getDimensions();
                int ndims = d.length;
                /* do whole loop just for error checking and maxmax[fp] .. */
                for (int i = 0; i < ndims; i++) {
                    if (d[i] > 1) {
                        RFFIFactory.getRFFI().getStatsRFFI().fft_factor(d[i], maxf, maxp);
                        if (maxf[0] == 0) {
                            errorProfile.enter();
                            throw RError.error(this, RError.Message.FFT_FACTORIZATION);
                        }
                        if (maxf[0] > maxmaxf) {
                            maxmaxf = maxf[0];
                        }
                        if (maxp[0] > maxmaxp) {
                            maxmaxp = maxp[0];
                        }
                    }
                }
                double[] work = new double[4 * maxmaxf];
                int[] iwork = new int[maxmaxp];
                int nseg = zVec.getLength();
                int n = 1;
                int nspn = 1;
                for (int i = 0; i < ndims; i++) {
                    if (d[i] > 1) {
                        nspn *= n;
                        n = d[i];
                        nseg /= n;
                        RFFIFactory.getRFFI().getStatsRFFI().fft_factor(n, maxf, maxp);
                        RFFIFactory.getRFFI().getStatsRFFI().fft_work(z, nseg, n, nspn, inv, work, iwork);
                    }
                }

            }
        }

        return RDataFactory.createComplexVector(z, zVec.isComplete(), zVec.getDimensions());
    }
}
