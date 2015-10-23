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
package com.oracle.truffle.r.library.fastr;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrument.QuitException;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.BrowserQuitException;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractStringVector;

public class FastRThrowIt {
    public abstract static class ThrowIt extends RExternalBuiltinNode.Arg1 {
        @Specialization
        @TruffleBoundary
        protected RNull throwit(RAbstractStringVector x) {
            String name = x.getDataAt(0);
            switch (name) {
                case "AIX":
                    throw new ArrayIndexOutOfBoundsException();
                case "NPE":
                    throw new NullPointerException();
                case "ASE":
                    throw new AssertionError();
                case "RTE":
                    throw new RuntimeException();
                case "RINT":
                    throw RInternalError.shouldNotReachHere();
                case "DBE":
                    throw new Utils.DebugExitException();
                case "Q":
                    throw new QuitException();
                case "BRQ":
                    throw new BrowserQuitException();
                default:
                    throw RError.error(RError.NO_NODE, RError.Message.GENERIC, "unknown case: " + name);
            }
        }
    }
}
