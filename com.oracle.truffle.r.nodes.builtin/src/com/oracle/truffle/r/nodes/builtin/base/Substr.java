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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.na.*;

@RBuiltin(name = "substr", kind = INTERNAL, parameterNames = {"x", "start", "stop"})
public abstract class Substr extends RBuiltinNode {
    private final NACheck na = NACheck.create();
    private final BranchProfile everSeenIllegalRange = BranchProfile.create();
    private final ConditionProfile naIndexesProfile = ConditionProfile.createBinaryProfile();

    @SuppressWarnings("unused")
    @Specialization(guards = "emptyArg(arg)")
    protected RStringVector substrEmptyArg(VirtualFrame frame, RAbstractStringVector arg, RAbstractIntVector start, RAbstractIntVector stop) {
        controlVisibility();
        return RDataFactory.createEmptyStringVector();
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!emptyArg(arg)", "wrongParams(start, stop)"})
    @TruffleBoundary
    protected RNull substrWrongParams(RAbstractStringVector arg, RAbstractIntVector start, RAbstractIntVector stop) {
        RInternalError.shouldNotReachHere();
        return RNull.instance; // dummy
    }

    @Specialization(guards = {"!emptyArg(arg)", "!wrongParams(start, stop)"})
    protected RStringVector substr(RAbstractStringVector arg, RAbstractIntVector start, RAbstractIntVector stop) {
        controlVisibility();
        String[] res = new String[arg.getLength()];
        na.enable(arg);
        na.enable(start);
        na.enable(stop);
        for (int i = 0, j, k; i < arg.getLength(); i++) {
            // Checkstyle: stop modified control variable check
            j = i % start.getLength();
            k = i % stop.getLength();
            // Checkstyle: resume modified control variable check
            res[i] = substr0(arg.getDataAt(i), start.getDataAt(j), stop.getDataAt(k));
        }
        return RDataFactory.createStringVector(res, na.neverSeenNA());
    }

    private String substr0(String x, int start, int stop) {
        if (naIndexesProfile.profile(na.check(x) || na.check(start) || na.check(stop))) {
            return RRuntime.STRING_NA;
        } else {
            int length = x.length();
            boolean startGreaterThanStop = start > stop;
            boolean startLessOrEqualZero = start <= 0;
            boolean stopLessOrEqualZero = stop <= 0;
            boolean startGreaterThanLength = start > length;
            boolean stopGreaterThanLength = stop > length;
            boolean wrongRange = startGreaterThanStop || startLessOrEqualZero || stopLessOrEqualZero || startGreaterThanLength || stopGreaterThanLength;
            int newStart = start;
            int newStop = stop;
            if (wrongRange) {
                everSeenIllegalRange.enter();
                if (startGreaterThanStop || (startLessOrEqualZero && stopLessOrEqualZero) || (startGreaterThanLength && stopGreaterThanLength)) {
                    return "";
                }
                if (startLessOrEqualZero) {
                    newStart = 1;
                }
                if (stopGreaterThanLength) {
                    newStop = length;
                }
            }
            return x.substring(newStart - 1, newStop);
        }
    }

    // protected static boolean rangeOk(String x, int start, int stop) {
    // return start <= stop && start > 0 && stop > 0 && start <= x.length() && stop <= x.length();
    // }
    //
    // protected String substr0(String x, int start, int stop) {
    // if (na.check(x) || na.check(start) || na.check(stop)) {
    // return RRuntime.STRING_NA;
    // }
    // int actualStart = start;
    // int actualStop = stop;
    // if (!rangeOk(x, start, stop)) {
    // everSeenIllegalRange.enter();
    // if (start > stop || (start <= 0 && stop <= 0) || (start > x.length() && stop > x.length())) {
    // return "";
    // }
    // if (start <= 0) {
    // actualStart = 1;
    // }
    // if (stop > x.length()) {
    // actualStop = x.length();
    // }
    // }
    // return x.substring(actualStart - 1, actualStop);
    // }

    protected boolean emptyArg(RAbstractStringVector arg) {
        return arg.getLength() == 0;
    }

    protected boolean wrongParams(RAbstractIntVector start, RAbstractIntVector stop) {
        if (start.getLength() == 0 || stop.getLength() == 0) {
            throw RError.error(this, RError.Message.INVALID_ARGUMENTS_NO_QUOTE, "substring");
        }
        return false;
    }
}
