/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.function.signature;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RArguments.*;
import com.oracle.truffle.r.runtime.data.*;

public abstract class RArgumentsNode extends Node {

    /*
     * This inline cache is not implemented using Truffle DSL because of null values provided for
     * certain parameters, on which Truffle DSL cannot specialize.
     */

    public abstract Object[] execute(RFunction function, RCaller caller, MaterializedFrame callerFrame, int depth, Object[] evaluatedArgs, ArgumentsSignature signature, S3Args s3Args);

    public static RArgumentsNode create() {
        return new RArgumentsUninitializedNode();
    }

    private static final class RArgumentsUninitializedNode extends RArgumentsNode {
        @Override
        public Object[] execute(RFunction function, RCaller caller, MaterializedFrame callerFrame, int depth, Object[] evaluatedArgs, ArgumentsSignature signature, S3Args s3Args) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return replace(new RArgumentsCachedNode(function)).execute(function, caller, callerFrame, depth, evaluatedArgs, signature, s3Args);
        }
    }

    private static final class RArgumentsCachedNode extends RArgumentsNode {
        private final RFunction cachedFunction;

        public RArgumentsCachedNode(RFunction cachedFunction) {
            this.cachedFunction = cachedFunction;
        }

        @Override
        public Object[] execute(RFunction function, RCaller caller, MaterializedFrame callerFrame, int depth, Object[] evaluatedArgs, ArgumentsSignature signature, S3Args s3Args) {
            if (function == cachedFunction) {
                return RArguments.create(cachedFunction, caller, callerFrame, depth, evaluatedArgs, signature, cachedFunction.getEnclosingFrame(), s3Args);
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return replace(new RArgumentsGenericNode()).execute(function, caller, callerFrame, depth, evaluatedArgs, signature, s3Args);
            }
        }
    }

    private static final class RArgumentsGenericNode extends RArgumentsNode {
        @Override
        public Object[] execute(RFunction function, RCaller caller, MaterializedFrame callerFrame, int depth, Object[] evaluatedArgs, ArgumentsSignature signature, S3Args s3Args) {
            return RArguments.create(function, caller, callerFrame, depth, evaluatedArgs, signature, function.getEnclosingFrame(), s3Args);
        }
    }
}
