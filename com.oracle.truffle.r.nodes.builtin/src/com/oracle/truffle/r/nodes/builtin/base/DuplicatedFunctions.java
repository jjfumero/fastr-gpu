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

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.binary.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.nodes.unary.TypeofNodeGen;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

public class DuplicatedFunctions {

    protected abstract static class Adapter extends RBuiltinNode {
        @Child protected CastTypeNode castTypeNode;
        @Child protected TypeofNode typeof;

        /**
         * Code sharing vehicle for the slight differences in behavior between {@code duplicated}
         * and {@code anyDuplicated} and whether {@code fromLast} is {@code TRUE/FALSE}.
         */
        protected static class DupState {
            final RAbstractContainer x;
            final HashSet<Object> vectorContents = new HashSet<>();
            final HashSet<Object> incompContents;
            final byte[] dupVec;
            int index;

            DupState(RAbstractContainer x, RAbstractContainer incomparables, boolean justIndex, boolean fromLast) {
                this.x = x;
                vectorContents.add(x.getDataAtAsObject(fromLast ? x.getLength() - 1 : 0));

                if (incomparables != null) {
                    incompContents = new HashSet<>();
                    for (int i = 0; i < incomparables.getLength(); i++) {
                        incompContents.add(incomparables.getDataAtAsObject(i));
                    }
                } else {
                    incompContents = null;
                }
                dupVec = justIndex ? null : new byte[x.getLength()];
            }

            boolean doIt(int i) {
                if (incompContents == null || !incompContents.contains(x.getDataAtAsObject(i))) {
                    if (vectorContents.contains(x.getDataAtAsObject(i))) {
                        if (dupVec == null) {
                            index = i + 1;
                            return true;
                        } else {
                            dupVec[i] = RRuntime.LOGICAL_TRUE;
                        }
                    } else {
                        vectorContents.add(x.getDataAtAsObject(i));
                    }
                } else {
                    if (dupVec != null) {
                        dupVec[i] = RRuntime.LOGICAL_FALSE;
                    }
                }
                return false;
            }

        }

        @TruffleBoundary
        protected static DupState analyze(RAbstractContainer x, RAbstractContainer incomparables, boolean justIndex, boolean fromLast) {
            DupState ds = new DupState(x, incomparables, justIndex, fromLast);
            if (fromLast) {
                for (int i = x.getLength() - 2; i >= 0; i--) {
                    if (ds.doIt(i)) {
                        break;
                    }
                }
            } else {
                for (int i = 1; i < x.getLength(); i++) {
                    if (ds.doIt(i)) {
                        break;
                    }
                }
            }
            return ds;
        }

        protected boolean isIncomparable(byte incomparables) {
            return incomparables == RRuntime.LOGICAL_TRUE;
        }

        protected boolean empty(RAbstractContainer x) {
            return x.getLength() == 0;
        }

        protected void initChildren() {
            if (castTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castTypeNode = insert(CastTypeNodeGen.create(null, null));
                typeof = insert(TypeofNodeGen.create(null));
            }
        }

    }

    @RBuiltin(name = "duplicated", kind = RBuiltinKind.INTERNAL, parameterNames = {"x", "imcomparables", "fromLast", "nmax"})
    public abstract static class Duplicated extends Adapter {
        @CreateCast("arguments")
        protected RNode[] castArguments(RNode[] arguments) {
            arguments[2] = CastLogicalNodeGen.create(arguments[2], false, false, false);
            arguments[3] = CastIntegerNodeGen.create(arguments[3], false, false, false);
            return arguments;
        }

        @TruffleBoundary
        protected static RLogicalVector analyzeAndCreateResult(RAbstractContainer x, RAbstractContainer incomparables, byte fromLast) {
            DupState ds = analyze(x, incomparables, false, RRuntime.fromLogical(fromLast));
            return RDataFactory.createLogicalVector(ds.dupVec, RDataFactory.COMPLETE_VECTOR);
        }

        @Specialization(guards = {"!isIncomparable(incomparables)", "!empty(x)"})
        protected RLogicalVector duplicatedFalseIncomparables(RAbstractVector x, @SuppressWarnings("unused") byte incomparables, byte fromLast, @SuppressWarnings("unused") int nmax) {
            return analyzeAndCreateResult(x, null, fromLast);
        }

        @Specialization(guards = {"isIncomparable(incomparables)", "!empty(x)"})
        protected RLogicalVector duplicatedTrueIncomparables(VirtualFrame frame, RAbstractVector x, byte incomparables, byte fromLast, @SuppressWarnings("unused") int nmax) {
            initChildren();
            RType xType = typeof.execute(x);
            RAbstractVector vector = (RAbstractVector) (castTypeNode.execute(frame, incomparables, xType));
            return analyzeAndCreateResult(x, vector, fromLast);
        }

        @Specialization(guards = {"!empty(x)"})
        protected RLogicalVector duplicated(VirtualFrame frame, RAbstractContainer x, RAbstractContainer incomparables, byte fromLast, @SuppressWarnings("unused") int nmax) {
            initChildren();
            RType xType = typeof.execute(x);
            return analyzeAndCreateResult(x, (RAbstractContainer) (castTypeNode.execute(frame, incomparables, xType)), fromLast);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "empty(x)")
        protected RLogicalVector duplicatedEmpty(RAbstractContainer x, RAbstractContainer incomparables, byte fromLast, int nmax) {
            return RDataFactory.createLogicalVector(0);
        }

    }

    @RBuiltin(name = "anyDuplicated", kind = RBuiltinKind.INTERNAL, parameterNames = {"x", "imcomparables", "fromLast"})
    public abstract static class AnyDuplicated extends Adapter {

        @CreateCast("arguments")
        public RNode[] castArguments(RNode[] arguments) {
            arguments[2] = CastLogicalNodeGen.create(arguments[2], true, false, false);
            return arguments;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isIncomparable(incomparables)", "!empty(x)"})
        protected int anyDuplicatedFalseIncomparables(RAbstractVector x, byte incomparables, byte fromLast) {
            return analyze(x, null, true, RRuntime.fromLogical(fromLast)).index;
        }

        @Specialization(guards = {"isIncomparable(incomparables)", "!empty(x)"})
        protected int anyDuplicatedTrueIncomparables(VirtualFrame frame, RAbstractVector x, byte incomparables, byte fromLast) {
            initChildren();
            RType xType = typeof.execute(x);
            RAbstractVector vector = (RAbstractVector) (castTypeNode.execute(frame, incomparables, xType));
            return analyze(x, vector, true, RRuntime.fromLogical(fromLast)).index;
        }

        @Specialization(guards = {"!empty(x)"})
        protected int anyDuplicated(VirtualFrame frame, RAbstractContainer x, RAbstractContainer incomparables, byte fromLast) {
            initChildren();
            RType xType = typeof.execute(x);
            return analyze(x, (RAbstractContainer) (castTypeNode.execute(frame, incomparables, xType)), true, RRuntime.fromLogical(fromLast)).index;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "empty(x)")
        protected int anyDuplicatedEmpty(RAbstractContainer x, RAbstractContainer incomparables, byte fromLast) {
            return 0;
        }

    }

}
