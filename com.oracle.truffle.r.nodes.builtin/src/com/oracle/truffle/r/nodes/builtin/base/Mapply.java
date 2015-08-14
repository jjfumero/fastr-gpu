/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.*;
import com.oracle.truffle.r.nodes.access.WriteVariableNode.Mode;
import com.oracle.truffle.r.nodes.access.variables.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.builtin.base.InfixEmulationFunctions.AccessArraySubscriptBuiltin;
import com.oracle.truffle.r.nodes.builtin.base.MapplyNodeGen.MapplyInternalNodeGen;
import com.oracle.truffle.r.nodes.function.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.nodes.*;

/**
 * Multivariate lapply. Essentially invokes
 * {@code fun(dots[0][X], dots[1][X], , dots[N][X], MoreArgs)} for {@code X=1..M} where {@code M} is
 * the longest vector, with the usual recycling rule.
 */
@RBuiltin(name = "mapply", kind = INTERNAL, parameterNames = {"FUN", "dots", "MoreArgs"}, splitCaller = true)
public abstract class Mapply extends RBuiltinNode {

    protected static class ElementNode extends Node {
        @Child Length lengthNode;
        @Child AccessArraySubscriptBuiltin indexedLoadNode;
        @Child WriteVariableNode writeVectorElementNode;
        private final String vectorElementName;

        ElementNode(String vectorElementName) {
            this.vectorElementName = AnonymousFrameVariable.create(vectorElementName);
            this.lengthNode = insert(LengthNodeGen.create(null, null, null));
            this.indexedLoadNode = insert(InfixEmulationFunctionsFactory.AccessArraySubscriptBuiltinNodeGen.create(null, null, null));
            this.writeVectorElementNode = insert(WriteVariableNode.createAnonymous(this.vectorElementName, null, Mode.REGULAR));
        }
    }

    @Child private MapplyInternalNode mapply = MapplyInternalNodeGen.create(null, null, null);

    @Specialization
    protected Object mApply(VirtualFrame frame, RFunction fun, RList dots, RList moreArgs) {
        if (moreArgs.getLength() > 0) {
            throw RError.nyi(this, "moreArgs");
        }
        Object[] result = mapply.execute(frame, dots, fun, moreArgs);
        // set here else it gets overridden by the iterator evaluation
        controlVisibility();
        return RDataFactory.createList(result);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected Object mApply(VirtualFrame frame, RFunction fun, RList dots, RNull moreArgs) {
        return mApply(frame, fun, dots, RDataFactory.createList());
    }

    @NodeChildren({@NodeChild(type = RNode.class), @NodeChild(type = RNode.class), @NodeChild(type = RNode.class)})
    protected abstract static class MapplyInternalNode extends RNode {

        private static final String VECTOR_ELEMENT_PREFIX = "MAPPLY_VEC_ELEM_";
        private static final RLogicalVector DROP = RDataFactory.createLogicalVectorFromScalar(true);
        private static final RLogicalVector EXACT = RDataFactory.createLogicalVectorFromScalar(true);
        private static final ArgumentsSignature I_INDEX = ArgumentsSignature.get("i");
        private static final RArgsValuesAndNames[] INDEX_CACHE = new RArgsValuesAndNames[32];

        static {
            for (int i = 0; i < INDEX_CACHE.length; i++) {
                INDEX_CACHE[i] = new RArgsValuesAndNames(new Object[]{i + 1}, I_INDEX);
            }
        }

        public abstract Object[] execute(VirtualFrame frame, RList dots, RFunction function, RList additionalArguments);

        @SuppressWarnings("unused")
        @Specialization(limit = "5", guards = {"function.getTarget() == cachedTarget"})
        protected Object[] cachedMApply(VirtualFrame frame, RList dots, RFunction function, RList moreArgs, @Cached("function.getTarget()") RootCallTarget cachedTarget,
                        @Cached("createElementNodeArray(dots.getLength())") ElementNode[] cachedElementNodeArray,
                        @Cached("createCallNode(cachedTarget, cachedElementNodeArray, moreArgs)") RCallNode callNode) {

            int dotsLength = dots.getLength();
            int[] lengths = new int[dotsLength];
            int maxLength = -1;
            for (int i = 0; i < dotsLength; i++) {
                int length = cachedElementNodeArray[i].lengthNode.executeInt(frame, dots.getDataAt(i));
                if (length > maxLength) {
                    maxLength = length;
                }
                lengths[i] = length;
            }
            Object[] result = new Object[maxLength];
            for (int i = 0; i < maxLength; i++) {
                /* Evaluate and store the arguments */
                for (int listIndex = 0; listIndex < dotsLength; listIndex++) {
                    Object listElem = dots.getDataAt(listIndex);
                    RAbstractContainer vec = null;
                    if (listElem instanceof RAbstractContainer) {
                        vec = (RAbstractContainer) listElem;
                    } else {
                        // TODO scalar types are a nuisance!
                        if (listElem instanceof String) {
                            vec = RDataFactory.createStringVectorFromScalar((String) listElem);
                        } else {
                            throw RInternalError.unimplemented();
                        }
                    }

                    int adjIndex = i % lengths[listIndex];
                    RArgsValuesAndNames indexArg;
                    if (adjIndex < INDEX_CACHE.length) {
                        indexArg = INDEX_CACHE[adjIndex];
                    } else {
                        indexArg = new RArgsValuesAndNames(new Object[]{adjIndex + 1}, I_INDEX);
                    }
                    Object vecElement = cachedElementNodeArray[listIndex].indexedLoadNode.execute(frame, vec, indexArg, EXACT, DROP);
                    cachedElementNodeArray[listIndex].writeVectorElementNode.execute(frame, vecElement);
                }
                /* Now call the function */
                result[i] = callNode.execute(frame, function);
            }
            return result;
        }

        @SuppressWarnings("unused")
        @Specialization(contains = "cachedMApply")
        protected Object[] genericMApply(Object vector, RFunction function, RArgsValuesAndNames additionalArguments) {
            throw RError.nyi(this, "generic mApply");
        }

        /**
         * Creates the {@link RCallNode} for this target.
         *
         * TODO names and moreArgs
         *
         */
        protected RCallNode createCallNode(RootCallTarget callTarget, ElementNode[] elementNodeArray, @SuppressWarnings("unused") RList moreArgs) {
            @SuppressWarnings("unused")
            FormalArguments formalArgs = ((RRootNode) callTarget.getRootNode()).getFormalArguments();

            RSyntaxNode[] readVectorElementNodes = new RSyntaxNode[elementNodeArray.length];
            for (int i = 0; i < readVectorElementNodes.length; i++) {
                readVectorElementNodes[i] = ReadVariableNode.create(elementNodeArray[i].vectorElementName, false);
            }
            ArgumentsSignature argsSig = ArgumentsSignature.empty(readVectorElementNodes.length);
            // Errors can be thrown from the modified call so a SourceSection is required
            SourceSection ss = Lapply.createCallSourceSection(callTarget, argsSig, readVectorElementNodes);
            return RCallNode.createCall(ss, null, argsSig, readVectorElementNodes);
        }

        protected ElementNode[] createElementNodeArray(int length) {
            ElementNode[] elementNodes = new ElementNode[length];
            for (int i = 0; i < length; i++) {
                elementNodes[i] = insert(new ElementNode(VECTOR_ELEMENT_PREFIX + (i + 1)));
            }
            return elementNodes;
        }
    }

}
