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

import static com.oracle.truffle.r.runtime.RDispatch.INTERNAL_GENERIC;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.BranchProfile;
import com.oracle.truffle.api.utilities.ConditionProfile;
import com.oracle.truffle.r.nodes.access.AccessFieldNode;
import com.oracle.truffle.r.nodes.access.AccessFieldNodeGen;
import com.oracle.truffle.r.nodes.access.UpdateFieldNode;
import com.oracle.truffle.r.nodes.access.UpdateFieldNodeGen;
import com.oracle.truffle.r.nodes.access.vector.ElementAccessMode;
import com.oracle.truffle.r.nodes.access.vector.ExtractVectorNode;
import com.oracle.truffle.r.nodes.access.vector.ReplaceVectorNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.InfixEmulationFunctionsFactory.PromiseEvaluatorNodeGen;
import com.oracle.truffle.r.nodes.function.PromiseHelperNode;
import com.oracle.truffle.r.nodes.function.RCallNode;
import com.oracle.truffle.r.parser.ast.ASTNode;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.RBuiltinKind;
import com.oracle.truffle.r.runtime.RDeparse;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RLanguage;
import com.oracle.truffle.r.runtime.data.RLogical;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractLogicalVector;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

/**
 * Work-around builtins for infix operators that FastR (currently) does not define as functions.
 * These definitions create the illusion that the definitions exist, even if they are not actually
 * bound to anything useful.
 *
 * One important reason that these must exist as {@link RBuiltin}s is that they occur when deparsing
 * packages and the deparse logic depends on them being found as builtins. See {@link RDeparse}.
 *
 */
public class InfixEmulationFunctions {

    public abstract static class ErrorAdapter extends RBuiltinNode {
        protected RError nyi() throws RError {
            throw RError.nyi(this, String.valueOf(getBuiltin()));
        }
    }

    @NodeChild(value = "op")
    protected abstract static class PromiseEvaluator extends RNode {

        protected PromiseEvaluator create() {
            return PromiseEvaluatorNodeGen.create(null);
        }

        protected abstract Object execute(VirtualFrame frame, Object op);

        @Specialization(guards = {"!isRPromise(op)", "!isRArgsValuesAndNames(op)"})
        protected Object eval(Object op) {
            return op;
        }

        @Specialization
        protected Object eval(VirtualFrame frame, RPromise p, //
                        @Cached("new()") PromiseHelperNode promiseHelper) {
            return promiseHelper.evaluate(frame, p);
        }

        @Specialization(guards = "!args.isEmpty()")
        protected RArgsValuesAndNames eval(VirtualFrame frame, RArgsValuesAndNames args, //
                        @Cached("create()") PromiseEvaluator evalRecursive) {
            Object[] values = args.getArguments();
            for (int i = 0; i < values.length; i++) {
                values[i] = evalRecursive.execute(frame, values[i]);
            }
            return args;
        }

        @Specialization(guards = "args.isEmpty()")
        protected RArgsValuesAndNames evalEmpty(RArgsValuesAndNames args) {
            return args;
        }
    }

    public abstract static class AccessArrayBuiltin extends RBuiltinNode {

        // new
        @Child private ExtractVectorNode extractNode;

        @Override
        protected void createCasts(CastBuilder casts) {
            casts.toLogical(2);
        }

        protected abstract boolean isSubset();

        protected Object access(VirtualFrame frame, Object vector, byte exact, RArgsValuesAndNames inds, Object dropDim) {
            if (extractNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                extractNode = insert(ExtractVectorNode.create(isSubset() ? ElementAccessMode.SUBSET : ElementAccessMode.SUBSCRIPT, false));
            }
            return extractNode.apply(frame, vector, inds.getArguments(), RLogical.valueOf(exact), dropDim);
        }

        protected boolean noInd(RArgsValuesAndNames inds) {
            return inds.isEmpty();
        }
    }

    @RBuiltin(name = "[", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x", "...", "drop"}, dispatch = INTERNAL_GENERIC)
    public abstract static class AccessArraySubsetBuiltin extends AccessArrayBuiltin {

        @Override
        protected boolean isSubset() {
            return true;
        }

        protected Object getInternal(VirtualFrame frame, Object x, RArgsValuesAndNames inds, Object dropVec) {
            return access(frame, x, RRuntime.LOGICAL_TRUE, inds, dropVec);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected RNull get(RNull x, Object inds, Object dropVec) {
            return x;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "noInd(inds)")
        protected Object getNoInd(Object x, RArgsValuesAndNames inds, Object dropVec) {
            return x;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected Object get(Object x, RMissing inds, Object dropVec) {
            return x;
        }

        @Specialization(guards = "!noInd(inds)")
        protected Object get(VirtualFrame frame, Object x, RArgsValuesAndNames inds, Object dropVec) {
            return getInternal(frame, x, inds, dropVec);
        }

    }

    @RBuiltin(name = ".subset", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"", "...", "drop"})
    public abstract static class AccessArraySubsetDefaultBuiltin {

    }

    @RBuiltin(name = "[[", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"", "...", "exact", "drop"}, dispatch = INTERNAL_GENERIC)
    public abstract static class AccessArraySubscriptBuiltin extends AccessArrayBuiltin {

        @Override
        protected boolean isSubset() {
            return false;
        }

        protected final ConditionProfile emptyExactProfile = ConditionProfile.createBinaryProfile();

        @Override
        public Object[] getDefaultParameterValues() {
            return new Object[]{RMissing.instance, RArgsValuesAndNames.EMPTY, RRuntime.LOGICAL_TRUE, RRuntime.LOGICAL_TRUE};
        }

        protected Object getInternal(VirtualFrame frame, Object x, RArgsValuesAndNames inds, RAbstractLogicalVector exactVec) {
            /*
             * TODO this should not be handled here. The new vector access nodes handle this, remove
             * this check as soon as its the default and the old implementation is gone.
             */
            byte exact;
            if (emptyExactProfile.profile(exactVec.getLength() == 0)) {
                exact = RRuntime.LOGICAL_FALSE;
            } else {
                exact = exactVec.getDataAt(0);
            }
            return access(frame, x, exact, inds, RRuntime.LOGICAL_TRUE);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected RNull getNoInd(RNull x, Object inds, Object exactVec, Object dropVec) {
            return x;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "noInd(inds)")
        protected Object getNoInd(Object x, RArgsValuesAndNames inds, RAbstractLogicalVector exactVec, RAbstractLogicalVector dropVec) {
            throw RError.error(this, RError.Message.NO_INDEX);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected Object get(Object x, RMissing inds, RAbstractLogicalVector exactVec, RAbstractLogicalVector dropVec) {
            throw RError.error(this, RError.Message.NO_INDEX);
        }

        @Specialization(guards = "!noInd(inds)")
        protected Object get(VirtualFrame frame, Object x, RArgsValuesAndNames inds, RAbstractLogicalVector exactVec, @SuppressWarnings("unused") RAbstractLogicalVector dropVec) {
            return getInternal(frame, x, inds, exactVec);
        }

    }

    @RBuiltin(name = ".subset2", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x", "...", "exact", "drop"})
    public abstract static class AccessArraySubscriptDefaultBuiltin {
    }

    public abstract static class UpdateArrayBuiltin extends RBuiltinNode {

        @Child private ReplaceVectorNode replaceNode;

        private final ConditionProfile argsLengthLargerThanOneProfile = ConditionProfile.createBinaryProfile();

        protected Object update(VirtualFrame frame, Object vector, RArgsValuesAndNames args, Object value, boolean isSubset) {
            if (replaceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                replaceNode = insert(ReplaceVectorNode.create(isSubset ? ElementAccessMode.SUBSET : ElementAccessMode.SUBSCRIPT, false));
            }
            Object[] pos;
            if (argsLengthLargerThanOneProfile.profile(args.getLength() > 1)) {
                pos = Arrays.copyOf(args.getArguments(), args.getLength() - 1);
            } else {
                pos = new Object[]{RMissing.instance};
            }
            return replaceNode.apply(frame, vector, pos, value);
        }

        @Specialization(guards = "noInd(args)")
        @SuppressWarnings("unused")
        protected Object getNoInd(Object x, RArgsValuesAndNames args) {
            throw RError.error(this, RError.Message.INVALID_ARG_NUMBER, "SubAssignArgs");
        }

        protected boolean noInd(RArgsValuesAndNames args) {
            return args.isEmpty();
        }
    }

    @RBuiltin(name = "[<-", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"", "..."}, dispatch = INTERNAL_GENERIC)
    public abstract static class UpdateArraySubsetBuiltin extends UpdateArrayBuiltin {

        private static final boolean IS_SUBSET = true;

        @Specialization(guards = "!noInd(args)")
        protected Object update(VirtualFrame frame, Object x, RArgsValuesAndNames args) {
            Object value = args.getArgument(args.getLength() - 1);
            return update(frame, x, args, value, IS_SUBSET);
        }
    }

    @RBuiltin(name = "[[<-", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"", "..."}, dispatch = INTERNAL_GENERIC)
    public abstract static class UpdateArrayNodeSubscriptBuiltin extends UpdateArrayBuiltin {

        private static final boolean IS_SUBSET = false;

        @Specialization(guards = "!noInd(args)")
        protected Object update(VirtualFrame frame, Object x, RArgsValuesAndNames args) {
            Object value = args.getArgument(args.getLength() - 1);
            return update(frame, x, args, value, IS_SUBSET);
        }
    }

    @RBuiltin(name = "<-", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x", "i"})
    public abstract static class AssignBuiltin extends ErrorAdapter {
        @SuppressWarnings("unused")
        @Specialization
        protected Object doIt(Object x, Object i) {
            throw nyi();
        }
    }

    @RBuiltin(name = "=", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x", "i"})
    public abstract static class AssignBuiltinEq extends ErrorAdapter {
        @SuppressWarnings("unused")
        @Specialization
        protected Object doIt(Object x, Object i) {
            throw nyi();
        }
    }

    @RBuiltin(name = "<<-", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x", "i"})
    public abstract static class AssignOuterBuiltin extends ErrorAdapter {
        @SuppressWarnings("unused")
        @Specialization
        protected Object doIt(Object x, Object i) {
            throw nyi();
        }
    }

    @RBuiltin(name = "$", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"", ""}, dispatch = INTERNAL_GENERIC)
    public abstract static class AccessFieldBuiltin extends RBuiltinNode {

        @Child private AccessFieldNode accessNode;
        private final BranchProfile errorProfile = BranchProfile.create();

        @Specialization
        protected Object accessField(VirtualFrame frame, Object container, Object field) {
            if (field instanceof String) {
                if (accessNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    accessNode = insert(AccessFieldNodeGen.create(null, null));
                }
                return accessNode.executeAccess(frame, container, (String) field);
            } else {
                errorProfile.enter();
                // TODO: the error message is not quite correct for all types;
                // for example: x<-list(a=7); `$<-`(x, c("a"), 42);)
                throw RError.error(this, RError.Message.INVALID_SUBSCRIPT_TYPE, RRuntime.classToString(field.getClass()));
            }
        }
    }

    @RBuiltin(name = "$<-", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"", "", ""}, dispatch = INTERNAL_GENERIC)
    public abstract static class UpdateFieldBuiltin extends RBuiltinNode {

        @Child private UpdateFieldNode updateNode;
        private final BranchProfile errorProfile = BranchProfile.create();

        @Specialization
        protected Object accessField(VirtualFrame frame, Object container, Object field, Object value) {
            if (field instanceof String) {
                if (updateNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    updateNode = insert(UpdateFieldNodeGen.create(null, null, null));
                }
                return updateNode.executeUpdate(frame, container, value, (String) field);
            } else {
                errorProfile.enter();
                // TODO: the error message is not quite correct for all types;
                // for example: x<-list(a=7); `$<-`(x, c("a"), 42);)
                throw RError.error(this, RError.Message.INVALID_SUBSCRIPT_TYPE, RRuntime.classToString(field.getClass()));
            }
        }
    }

    @RBuiltin(name = ":", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"from", "to"})
    public abstract static class ColonBuiltin extends ErrorAdapter {
        @SuppressWarnings("unused")
        @Specialization
        protected Object doIt(Object from, Object to) {
            throw nyi();
        }
    }

    @RBuiltin(name = "{", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x"})
    public abstract static class BraceBuiltin extends ErrorAdapter {
        @SuppressWarnings("unused")
        @Specialization
        protected Object doIt(Object x) {
            throw nyi();
        }
    }

    @RBuiltin(name = "(", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x"})
    public abstract static class ParenBuiltin extends ErrorAdapter {
        @SuppressWarnings("unused")
        @Specialization
        protected Object doIt(Object x) {
            throw nyi();
        }
    }

    /**
     * This a rather strange function. It is where, in GnuR, that the "formula" class is set and the
     * ".Environment" attribute on the "call". Unfortunately, in FastR we have lost access to the
     * {@link RCallNode} that the parser created, so we recreate it and wrap it up an
     * {@link RLanguage}. object. N.B. the "response" can be missing, which is actually handled by
     * an evaluated argument of type {@link RMissing}, although it appears as if the "model"
     * argument is missing, i.e. {@code ~ x} result in {@code `~`(x)}.
     */
    @RBuiltin(name = "~", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x", "y"}, nonEvalArgs = {0, 1})
    public abstract static class TildeBuiltin extends RBuiltinNode {
        private static final RStringVector FORMULA_CLASS = RDataFactory.createStringVectorFromScalar(RRuntime.FORMULA_CLASS);

        @Override
        public Object[] getDefaultParameterValues() {
            return new Object[]{RMissing.instance, RMissing.instance};
        }

        @Specialization
        protected RLanguage tilde(VirtualFrame frame, RPromise response, @SuppressWarnings("unused") RMissing model) {
            return doTilde(frame, null, ((RNode) response.getRep()).asRSyntaxNode());
        }

        @Specialization
        protected RLanguage tilde(VirtualFrame frame, RPromise response, RPromise model) {
            return doTilde(frame, ((RNode) response.getRep()).asRSyntaxNode(), ((RNode) model.getRep()).asRSyntaxNode());
        }

        private RLanguage doTilde(VirtualFrame frame, RSyntaxNode response, RSyntaxNode model) {
            RSyntaxNode[] tildeArgs = new RSyntaxNode[response == null ? 1 : 2];
            int ix = 0;
            if (response != null) {
                tildeArgs[ix++] = response;
            }
            tildeArgs[ix++] = model;
            SourceSection formulaSrc = this.getSourceSection();
            String formulaCode = formulaSrc.getCode();
            int tildeIndex = formulaCode.indexOf('~');
            SourceSection tildeSrc = ASTNode.adjustedSource(formulaSrc, formulaSrc.getCharIndex() + tildeIndex, 1);
            RCallNode call = RCallNode.createOpCall(formulaSrc, tildeSrc, "~", tildeArgs);
            RLanguage lang = RDataFactory.createLanguage(call);
            lang.setClassAttr(FORMULA_CLASS, false);
            REnvironment env = REnvironment.frameToEnvironment(frame.materialize());
            lang.setAttr(RRuntime.DOT_ENVIRONMENT, env);
            return lang;
        }
    }

    @RBuiltin(name = "if", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x"})
    public abstract static class IfBuiltin extends ErrorAdapter {
        @SuppressWarnings("unused")
        @Specialization
        protected Object doIt(Object x) {
            throw nyi();
        }
    }

    @RBuiltin(name = "while", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x"})
    public abstract static class WhileBuiltin extends ErrorAdapter {
        @SuppressWarnings("unused")
        @Specialization
        protected Object doIt(Object x) {
            throw nyi();
        }
    }

    @RBuiltin(name = "repeat", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x"})
    public abstract static class RepeatBuiltin extends ErrorAdapter {
        @SuppressWarnings("unused")
        @Specialization
        protected Object doIt(Object x) {
            throw nyi();
        }
    }

    @RBuiltin(name = "for", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x"})
    public abstract static class ForBuiltin extends ErrorAdapter {
        @SuppressWarnings("unused")
        @Specialization
        protected Object doIt(Object x) {
            throw nyi();
        }
    }

    @RBuiltin(name = "break", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x"})
    public abstract static class BreakBuiltin extends ErrorAdapter {
        @SuppressWarnings("unused")
        @Specialization
        protected Object doIt(Object x) {
            throw nyi();
        }
    }

    @RBuiltin(name = "next", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x"})
    public abstract static class NextBuiltin extends ErrorAdapter {
        @SuppressWarnings("unused")
        @Specialization
        protected Object doIt(Object x) {
            throw nyi();
        }
    }

    @RBuiltin(name = "function", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x"})
    public abstract static class FunctionBuiltin extends ErrorAdapter {
        @SuppressWarnings("unused")
        @Specialization
        protected Object doIt(Object x) {
            throw nyi();
        }
    }

}
