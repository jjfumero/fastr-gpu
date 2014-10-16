/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995-2012, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ffi.*;

/*
 * Logic derived from GNU-R, src/modules/lapack/Lapack.c
 */

/**
 * Lapack builtins.
 */
public class LaFunctions {

    @RBuiltin(name = "La_version", kind = INTERNAL, parameterNames = {})
    public abstract static class Version extends RBuiltinNode {
        @Specialization
        @SlowPath
        protected String doVersion() {
            int[] version = new int[3];
            RFFIFactory.getRFFI().getLapackRFFI().ilaver(version);
            return version[0] + "." + version[1] + "." + version[2];
        }
    }

    @RBuiltin(name = "La_rg", kind = INTERNAL, parameterNames = {"matrix", "onlyValues"})
    public abstract static class Rg extends RBuiltinNode {

        private static final String[] NAMES = new String[]{"values", "vectors"};

        private final ConditionProfile hasComplexValues = ConditionProfile.createBinaryProfile();
        private final BranchProfile errorProfile = BranchProfile.create();

        @Specialization
        protected Object doRg(RDoubleVector matrix, byte onlyValues) {
            controlVisibility();
            if (!matrix.isMatrix()) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.MUST_BE_SQUARE_NUMERIC, "x");
            }
            int[] dims = matrix.getDimensions();
            if (onlyValues == RRuntime.LOGICAL_NA) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_ARGUMENT, "only.values");
            }
            // copy array component of matrix as Lapack destroys it
            int n = dims[0];
            double[] a = matrix.getDataCopy();
            char jobVL = 'N';
            char jobVR = 'N';
            boolean vectors = onlyValues == RRuntime.LOGICAL_FALSE;
            if (vectors) {
                // TODO fix
                RError.nyi(getEncapsulatingSourceSection(), "\"only.values == FALSE\" not implemented");
            }
            double[] left = null;
            double[] right = null;
            if (vectors) {
                jobVR = 'V';
                right = new double[a.length];
            }
            double[] wr = new double[n];
            double[] wi = new double[n];
            double[] work = new double[1];
            // ask for optimal size of work array
            int info = RFFIFactory.getRFFI().getLapackRFFI().dgeev(jobVL, jobVR, n, a, n, wr, wi, left, n, right, n, work, -1);
            if (info != 0) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.LAPACK_ERROR, info, "dgeev");
            }
            // now allocate work array and make the actual call
            int lwork = (int) work[0];
            work = new double[lwork];
            info = RFFIFactory.getRFFI().getLapackRFFI().dgeev(jobVL, jobVR, n, a, n, wr, wi, left, n, right, n, work, lwork);
            if (info != 0) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.LAPACK_ERROR, info, "dgeev");
            }
            // result is a list containing "values" and "vectors" (unless only.values is TRUE)
            boolean complexValues = false;
            for (int i = 0; i < n; i++) {
                if (Math.abs(wi[i]) > 10 * RAccuracyInfo.get().eps * Math.abs(wr[i])) {
                    complexValues = true;
                    break;
                }
            }
            RVector values = null;
            Object vectorValues = RNull.instance;
            if (hasComplexValues.profile(complexValues)) {
                double[] data = new double[n * 2];
                for (int i = 0; i < n; i++) {
                    int ix = 2 * i;
                    data[ix] = wr[i];
                    data[ix + 1] = wi[i];
                }
                values = RDataFactory.createComplexVector(data, RDataFactory.COMPLETE_VECTOR);
                if (vectors) {
                    // TODO
                }
            } else {
                values = RDataFactory.createDoubleVector(wr, RDataFactory.COMPLETE_VECTOR);
                if (vectors) {
                    // TODO
                }
            }
            RStringVector names = RDataFactory.createStringVector(NAMES, RDataFactory.COMPLETE_VECTOR);
            RList result = RDataFactory.createList(new Object[]{values, vectorValues}, names);
            return result;
        }

    }

    @RBuiltin(name = "La_qr", kind = INTERNAL, parameterNames = {"in"})
    public abstract static class Qr extends RBuiltinNode {

        private static final String[] NAMES = new String[]{"qr", "rank", "qraux", "pivot"};

        private final BranchProfile errorProfile = BranchProfile.create();

        @Specialization
        protected RList doQr(RAbstractVector aIn) {
            // This implementation is sufficient for B25 matcal-5.
            if (!aIn.isMatrix()) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.MUST_BE_NUMERIC_MATRIX, "a");
            }
            if (!(aIn instanceof RDoubleVector)) {
                RError.nyi(getEncapsulatingSourceSection(), "non-real vectors not supported (yet)");
            }
            RDoubleVector daIn = (RDoubleVector) aIn;
            int[] dims = daIn.getDimensions();
            // copy array component of matrix as Lapack destroys it
            int n = dims[0];
            int m = dims[1];
            double[] a = daIn.getDataCopy();
            int[] jpvt = new int[n];
            double[] tau = new double[m < n ? m : n];
            double[] work = new double[1];
            // ask for optimal size of work array
            int info = RFFIFactory.getRFFI().getLapackRFFI().dgeqp3(m, n, a, m, jpvt, tau, work, -1);
            if (info < 0) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.LAPACK_ERROR, info, "dgeqp3");
            }
            int lwork = (int) work[0];
            work = new double[lwork];
            info = RFFIFactory.getRFFI().getLapackRFFI().dgeqp3(m, n, a, m, jpvt, tau, work, lwork);
            if (info < 0) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.LAPACK_ERROR, info, "dgeqp3");
            }
            Object[] data = new Object[4];
            // TODO check complete
            RDoubleVector ra = RDataFactory.createDoubleVector(a, RDataFactory.COMPLETE_VECTOR);
            // TODO check pivot
            ra.setDimensions(dims);
            data[0] = ra;
            data[1] = m < n ? m : n;
            data[2] = RDataFactory.createDoubleVector(tau, RDataFactory.COMPLETE_VECTOR);
            data[3] = RDataFactory.createIntVector(jpvt, RDataFactory.COMPLETE_VECTOR);
            return RDataFactory.createList(data, RDataFactory.createStringVector(NAMES, RDataFactory.COMPLETE_VECTOR));
        }
    }

    @RBuiltin(name = "qr_coef_real", kind = INTERNAL, parameterNames = {"q", "b"})
    public abstract static class QrCoefReal extends RBuiltinNode {

        private final BranchProfile errorProfile = BranchProfile.create();

        private static final char SIDE = 'L';
        private static final char TRANS = 'T';

        @CreateCast("arguments")
        protected RNode[] castbInArgument(RNode[] arguments) {
            arguments[1] = CastDoubleNodeFactory.create(arguments[1], false, true, false);
            return arguments;
        }

        @Specialization
        protected RDoubleVector doQrCoefReal(RList qIn, RDoubleVector bIn) {
            if (!bIn.isMatrix()) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.MUST_BE_NUMERIC_MATRIX, "b");
            }
            // If bIn was coerced this extra copy is unnecessary
            RDoubleVector b = (RDoubleVector) bIn.copy();

            RDoubleVector qr = (RDoubleVector) qIn.getDataAt(0);

            RDoubleVector tau = (RDoubleVector) qIn.getDataAt(2);
            int k = tau.getLength();

            int[] bDims = bIn.getDimensions();
            int[] qrDims = qr.getDimensions();
            int n = qrDims[0];
            if (bDims[0] != n) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.RHS_SHOULD_HAVE_ROWS, n, bDims[0]);
            }
            int nrhs = bDims[1];
            double[] work = new double[1];
            // qr and tau do not really need copying
            double[] qrData = qr.getDataWithoutCopying();
            double[] tauData = tau.getDataWithoutCopying();
            // we work directly in the internal data of b
            double[] bData = b.getDataWithoutCopying();
            // ask for optimal size of work array
            int info = RFFIFactory.getRFFI().getLapackRFFI().dormqr(SIDE, TRANS, n, nrhs, k, qrData, n, tauData, bData, n, work, -1);
            if (info < 0) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.LAPACK_ERROR, info, "dormqr");
            }
            int lwork = (int) work[0];
            work = new double[lwork];
            info = RFFIFactory.getRFFI().getLapackRFFI().dormqr(SIDE, TRANS, n, nrhs, k, qrData, n, tauData, bData, n, work, lwork);
            if (info < 0) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.LAPACK_ERROR, info, "dormqr");
            }
            info = RFFIFactory.getRFFI().getLapackRFFI().dtrtrs('U', 'N', 'N', k, nrhs, qrData, n, bData, n);
            if (info < 0) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.LAPACK_ERROR, info, "dtrtrs");
            }
            // TODO check complete
            return b;
        }

    }

    @RBuiltin(name = "det_ge_real", kind = INTERNAL, parameterNames = {"a", "uselog"})
    public abstract static class DetGeReal extends RBuiltinNode {

        private static final RStringVector NAMES_VECTOR = RDataFactory.createStringVector(new String[]{"modulus", "sign"}, RDataFactory.COMPLETE_VECTOR);
        private static final RStringVector DET_CLASS = RDataFactory.createStringVector(new String[]{"det"}, RDataFactory.COMPLETE_VECTOR);

        private final BranchProfile errorProfile = BranchProfile.create();
        private final ConditionProfile infoGreaterZero = ConditionProfile.createBinaryProfile();
        private final ConditionProfile doUseLog = ConditionProfile.createBinaryProfile();

        @Specialization
        protected RList doDetGeReal(RDoubleVector aIn, byte useLogIn) {
            if (!aIn.isMatrix()) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.MUST_BE_NUMERIC_MATRIX, "a");
            }
            RDoubleVector a = (RDoubleVector) aIn.copy();
            int[] aDims = aIn.getDimensions();
            int n = aDims[0];
            if (n != aDims[1]) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.MUST_BE_SQUARE, "a");
            }
            int[] ipiv = new int[n];
            double modulus = 0;
            boolean useLog = RRuntime.fromLogical(useLogIn);
            double[] aData = a.getDataWithoutCopying();
            int info = RFFIFactory.getRFFI().getLapackRFFI().dgetrf(n, n, aData, n, ipiv);
            int sign = 1;
            if (info < 0) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.LAPACK_ERROR, info, "dgetrf");
            } else if (infoGreaterZero.profile(info > 0)) {
                modulus = useLog ? Double.NEGATIVE_INFINITY : 0;
            } else {
                for (int i = 0; i < n; i++) {
                    if (ipiv[i] != (i + 1)) {
                        sign = -sign;
                    }
                }
                if (doUseLog.profile(useLog)) {
                    modulus = 0.0;
                    int n1 = n + 1;
                    for (int i = 0; i < n; i++) {
                        double dii = aData[i * n1]; /* ith diagonal element */
                        modulus += Math.log(dii < 0 ? -dii : dii);
                        if (dii < 0) {
                            sign = -sign;
                        }
                    }
                } else {
                    modulus = 1.0;
                    int n1 = n + 1;
                    for (int i = 0; i < n; i++) {
                        modulus *= aData[i * n1];
                    }
                    if (modulus < 0) {
                        modulus = -modulus;
                        sign = -sign;
                    }
                }
            }
            RDoubleVector modulusVec = RDataFactory.createDoubleVectorFromScalar(modulus);
            modulusVec.setAttr("logarithm", useLogIn);
            RList result = RDataFactory.createList(new Object[]{modulusVec, sign}, NAMES_VECTOR);
            RList.setClassAttr(result, DET_CLASS, null);
            return result;
        }
    }

    @RBuiltin(name = "La_chol", kind = INTERNAL, parameterNames = {"a", "pivot", "tol"})
    public abstract static class LaChol extends RBuiltinNode {

        private final BranchProfile errorProfile = BranchProfile.create();
        private final ConditionProfile noPivot = ConditionProfile.createBinaryProfile();

        @Specialization
        protected RDoubleVector doDetGeReal(RDoubleVector aIn, byte pivot, double tol) {
            RDoubleVector a = (RDoubleVector) aIn.copy();
            int[] aDims = aIn.getDimensions();
            int n = aDims[0];
            int m = aDims[1];
            if (n != m) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.MUST_BE_SQUARE, "a");
            }
            if (m <= 0) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.DIMS_GT_ZERO, "a");
            }
            double[] aData = a.getDataWithoutCopying();
            /* zero the lower triangle */
            for (int j = 0; j < n; j++) {
                for (int i = j + 1; i < n; i++) {
                    aData[i + n * j] = 0;
                }
            }
            boolean piv = RRuntime.fromLogical(pivot);
            int info;
            if (noPivot.profile(!piv)) {
                info = RFFIFactory.getRFFI().getLapackRFFI().dpotrf('U', m, aData, m);
                if (info != 0) {
                    errorProfile.enter();
                    // TODO informative error message (aka GnuR)
                    throw RError.error(getEncapsulatingSourceSection(), RError.Message.LAPACK_ERROR, info, "dpotrf");
                }
            } else {
                int[] ipiv = new int[m];
                double[] work = new double[2 * m];
                int[] rank = new int[1];
                info = RFFIFactory.getRFFI().getLapackRFFI().dpstrf('U', n, aData, n, ipiv, rank, tol, work);
                if (info != 0) {
                    errorProfile.enter();
                    // TODO informative error message (aka GnuR)
                    throw RError.error(getEncapsulatingSourceSection(), RError.Message.LAPACK_ERROR, info, "dpotrf");
                }
                a.setAttr("pivot", pivot);
                a.setAttr("rank", rank[0]);
                RList dn = a.getDimNames();
                if (dn != null && dn.getDataAt(0) != null) {
                    Object[] dn2 = new Object[m];
                    // need to pivot the colnames
                    for (int i = 0; i < m; i++) {
                        dn2[i] = dn.getDataAt(ipiv[i] - 1);
                    }
                    a.setDimNames(RDataFactory.createList(dn2));
                }
            }
            return a;
        }
    }
}
