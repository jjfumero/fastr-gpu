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

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

/**
 * The {@code split} internal.
 *
 * TODO Can we find a way to efficiently write the specializations as generics? The code is
 * identical except for the argument type.
 */
@RBuiltin(name = "split", kind = INTERNAL, parameterNames = {"x", "f"})
public abstract class Split extends RBuiltinNode {

    @Child private CastStringNode castString;

    private final ConditionProfile noStringLevels = ConditionProfile.createBinaryProfile();
    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    private static final int INITIAL_SIZE = 5;
    private static final int SCALE_FACTOR = 2;

    @Specialization
    protected RList split(VirtualFrame frame, RAbstractIntVector x, RFactor f) {
        int[] factor = f.getVector().getDataWithoutCopying();
        final int nLevels = f.getNLevels(attrProfiles);

        // initialise result arrays
        int[][] collectResults = new int[nLevels][];
        int[] collectResultSize = new int[nLevels];
        for (int i = 0; i < collectResults.length; i++) {
            collectResults[i] = new int[INITIAL_SIZE];
        }

        // perform split
        for (int i = 0, fi = 0; i < x.getLength(); ++i, fi = Utils.incMod(fi, factor.length)) {
            int resultIndex = factor[fi] - 1; // a factor is a 1-based int vector
            int[] collect = collectResults[resultIndex];
            if (collect.length == collectResultSize[resultIndex]) {
                collectResults[resultIndex] = Arrays.copyOf(collect, collect.length * SCALE_FACTOR);
                collect = collectResults[resultIndex];
            }
            collect[collectResultSize[resultIndex]++] = x.getDataAt(i);
        }

        // assemble result vectors and level names
        Object[] results = new Object[nLevels];
        for (int i = 0; i < nLevels; i++) {
            results[i] = RDataFactory.createIntVector(Arrays.copyOfRange(collectResults[i], 0, collectResultSize[i]), RDataFactory.COMPLETE_VECTOR);
        }

        return RDataFactory.createList(results, makeNames(frame, f));
    }

    @Specialization
    protected RList split(VirtualFrame frame, RAbstractDoubleVector x, RFactor f) {
        int[] factor = f.getVector().getDataWithoutCopying();
        final int nLevels = f.getNLevels(attrProfiles);

        // initialise result arrays
        double[][] collectResults = new double[nLevels][];
        int[] collectResultSize = new int[nLevels];
        for (int i = 0; i < collectResults.length; i++) {
            collectResults[i] = new double[INITIAL_SIZE];
        }

        // perform split
        for (int i = 0, fi = 0; i < x.getLength(); ++i, fi = Utils.incMod(fi, factor.length)) {
            int resultIndex = factor[fi] - 1; // a factor is a 1-based int vector
            double[] collect = collectResults[resultIndex];
            if (collect.length == collectResultSize[resultIndex]) {
                collectResults[resultIndex] = Arrays.copyOf(collect, collect.length * SCALE_FACTOR);
                collect = collectResults[resultIndex];
            }
            collect[collectResultSize[resultIndex]++] = x.getDataAt(i);
        }

        // assemble result vectors and level names
        Object[] results = new Object[nLevels];
        for (int i = 0; i < nLevels; i++) {
            results[i] = RDataFactory.createDoubleVector(Arrays.copyOfRange(collectResults[i], 0, collectResultSize[i]), RDataFactory.COMPLETE_VECTOR);
        }

        return RDataFactory.createList(results, makeNames(frame, f));
    }

    @Specialization
    protected RList split(VirtualFrame frame, RAbstractStringVector x, RFactor f) {
        int[] factor = f.getVector().getDataWithoutCopying();
        final int nLevels = f.getNLevels(attrProfiles);

        // initialise result arrays
        String[][] collectResults = new String[nLevels][];
        int[] collectResultSize = new int[nLevels];
        for (int i = 0; i < collectResults.length; i++) {
            collectResults[i] = new String[INITIAL_SIZE];
        }

        // perform split
        for (int i = 0, fi = 0; i < x.getLength(); ++i, fi = Utils.incMod(fi, factor.length)) {
            int resultIndex = factor[fi] - 1; // a factor is a 1-based int vector
            String[] collect = collectResults[resultIndex];
            if (collect.length == collectResultSize[resultIndex]) {
                collectResults[resultIndex] = Arrays.copyOf(collect, collect.length * SCALE_FACTOR);
                collect = collectResults[resultIndex];
            }
            collect[collectResultSize[resultIndex]++] = x.getDataAt(i);
        }

        // assemble result vectors and level names
        Object[] results = new Object[nLevels];
        for (int i = 0; i < nLevels; i++) {
            results[i] = RDataFactory.createStringVector(Arrays.copyOfRange(collectResults[i], 0, collectResultSize[i]), RDataFactory.COMPLETE_VECTOR);
        }

        return RDataFactory.createList(results, makeNames(frame, f));
    }

    @Specialization
    protected RList split(VirtualFrame frame, RAbstractLogicalVector x, RFactor f) {
        int[] factor = f.getVector().getDataWithoutCopying();
        final int nLevels = f.getNLevels(attrProfiles);

        // initialise result arrays
        byte[][] collectResults = new byte[nLevels][];
        int[] collectResultSize = new int[nLevels];
        for (int i = 0; i < collectResults.length; i++) {
            collectResults[i] = new byte[INITIAL_SIZE];
        }

        // perform split
        for (int i = 0, fi = 0; i < x.getLength(); ++i, fi = Utils.incMod(fi, factor.length)) {
            int resultIndex = factor[fi] - 1; // a factor is a 1-based int vector
            byte[] collect = collectResults[resultIndex];
            if (collect.length == collectResultSize[resultIndex]) {
                collectResults[resultIndex] = Arrays.copyOf(collect, collect.length * SCALE_FACTOR);
                collect = collectResults[resultIndex];
            }
            collect[collectResultSize[resultIndex]++] = x.getDataAt(i);
        }

        // assemble result vectors and level names
        Object[] results = new Object[nLevels];
        for (int i = 0; i < nLevels; i++) {
            results[i] = RDataFactory.createLogicalVector(Arrays.copyOfRange(collectResults[i], 0, collectResultSize[i]), RDataFactory.COMPLETE_VECTOR);
        }

        return RDataFactory.createList(results, makeNames(frame, f));
    }

    private RStringVector makeNames(VirtualFrame frame, RFactor f) {
        RVector levels = f.getLevels(attrProfiles);
        if (noStringLevels.profile(!(levels instanceof RStringVector))) {
            if (castString == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castString = insert(CastStringNodeGen.create(null, false, false, false, false));
            }
            RStringVector slevels = (RStringVector) castString.executeString(frame, f.getLevels(attrProfiles));
            return RDataFactory.createStringVector(slevels.getDataWithoutCopying(), RDataFactory.COMPLETE_VECTOR);
        } else {
            return RDataFactory.createStringVector(((RStringVector) levels).getDataCopy(), RDataFactory.COMPLETE_VECTOR);
        }
    }

}
