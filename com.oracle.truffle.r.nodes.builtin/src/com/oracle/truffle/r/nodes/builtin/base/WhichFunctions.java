/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.RNode;
import com.oracle.truffle.r.nodes.access.ConstantNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.unary.CastDoubleNodeFactory;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.RBuiltinKind;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.RAbstractDoubleVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractLogicalVector;
import com.oracle.truffle.r.runtime.ops.na.*;

import java.util.ArrayList;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import static com.oracle.truffle.r.runtime.RBuiltinKind.INTERNAL;

/**
 * Encapsulates all which* as nested static classes.
 */
public class WhichFunctions {

    @RBuiltin(name = "which", kind = INTERNAL, parameterNames = {"x"})
    public abstract static class Which extends RBuiltinNode {

        private final NACheck naCheck = NACheck.create();

        @Override
        public RNode[] getParameterValues() {
            return new RNode[]{ConstantNode.create(RMissing.instance)};
        }

        @Specialization(guards = "!hasNames")
        @TruffleBoundary
        protected RIntVector which(RAbstractLogicalVector x) {
            controlVisibility();
            ArrayList<Integer> w = new ArrayList<>();
            for (int i = 0; i < x.getLength(); ++i) {
                if (x.getDataAt(i) == RRuntime.LOGICAL_TRUE) {
                    w.add(i);
                }
            }
            int[] result = new int[w.size()];
            for (int i = 0; i < result.length; ++i) {
                result[i] = w.get(i) + 1;
            }
            return RDataFactory.createIntVector(result, RDataFactory.COMPLETE_VECTOR);
        }

        @Specialization(guards = "hasNames")
        @TruffleBoundary
        protected RIntVector whichNames(RAbstractLogicalVector x) {
            controlVisibility();
            ArrayList<Integer> w = new ArrayList<>();
            ArrayList<String> n = new ArrayList<>();
            RStringVector oldNames = (RStringVector) x.getNames();
            naCheck.enable(oldNames);
            for (int i = 0; i < x.getLength(); ++i) {
                if (x.getDataAt(i) == RRuntime.LOGICAL_TRUE) {
                    w.add(i);
                    String s = oldNames.getDataAt(i);
                    naCheck.check(s);
                    n.add(s);
                }
            }
            int[] result = new int[w.size()];
            for (int i = 0; i < result.length; ++i) {
                result[i] = w.get(i) + 1;
            }
            String[] names = new String[n.size()];
            return RDataFactory.createIntVector(result, RDataFactory.COMPLETE_VECTOR, RDataFactory.createStringVector(n.toArray(names), naCheck.neverSeenNA()));
        }

        protected boolean hasNames(RAbstractLogicalVector x) {
            return x.getNames() != RNull.instance;
        }
    }

    @RBuiltin(name = "which.max", kind = RBuiltinKind.INTERNAL, parameterNames = {"x"})
    public abstract static class WhichMax extends RBuiltinNode {

        @CreateCast("arguments")
        public RNode[] castArguments(RNode[] arguments) {
            arguments[0] = CastDoubleNodeFactory.create(arguments[0], true, false, false);
            return arguments;
        }

        @Specialization
        @TruffleBoundary
        protected int which(RAbstractDoubleVector x) {
            controlVisibility();
            double max = x.getDataAt(0);
            int maxIndex = 0;
            for (int i = 0; i < x.getLength(); i++) {
                if (x.getDataAt(i) > max) {
                    max = x.getDataAt(i);
                    maxIndex = i;
                }
            }
            return maxIndex + 1;
        }

    }

    @RBuiltin(name = "which.min", kind = RBuiltinKind.INTERNAL, parameterNames = {"x"})
    public abstract static class WhichMin extends RBuiltinNode {

        @CreateCast("arguments")
        public RNode[] castArguments(RNode[] arguments) {
            arguments[0] = CastDoubleNodeFactory.create(arguments[0], true, false, false);
            return arguments;
        }

        @Specialization
        @TruffleBoundary
        protected int which(RAbstractDoubleVector x) {
            controlVisibility();
            double minimum = x.getDataAt(0);
            int minIndex = 0;
            for (int i = 0; i < x.getLength(); i++) {
                if (x.getDataAt(i) < minimum) {
                    minimum = x.getDataAt(i);
                    minIndex = i;
                }
            }
            return minIndex + 1;
        }

    }
}
