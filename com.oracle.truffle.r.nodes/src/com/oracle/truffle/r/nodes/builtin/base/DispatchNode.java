/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, Oracle and/or its affiliates
 *
 * All rights reserved.
 */

package com.oracle.truffle.r.nodes.builtin.base;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.function.*;
import com.oracle.truffle.r.runtime.data.*;

public abstract class DispatchNode extends RNode {

    protected RStringVector type;
    @CompilationFinal protected String genericName;

    static final class FunctionCall {
        RFunction function;
        CallArgumentsNode args;

        FunctionCall(RFunction function, CallArgumentsNode args) {
            this.function = function;
            this.args = args;
        }
    }

    protected abstract void unsetEnvironment(VirtualFrame frame);
}
