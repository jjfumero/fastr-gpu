/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.library.astx.threads;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;

public abstract class RThreadFunction extends RExternalBuiltinNode.Arg2 {

    @Specialization
    public Object createThreadAndRun(RFunction function, RAbstractIntVector input) {

        int nArgs = ASTxUtils.getNumberOfArguments(function);
        String[] argsName = ASTxUtils.getArgumentsNames(function);
        Object[] argsPackage = ASTxUtils.getArgsPackage(nArgs, function, input, null, argsName, 0);

        Thread thread = new Thread(() -> {
            System.out.println("[ASTx] Thread: " + Thread.currentThread().getName());
            function.getTarget().call(argsPackage);
            System.out.println("[ASTx] Thread: " + Thread.currentThread().getName());
        });

        thread.start();     // Annotate the thread into a Hash for runtime management

        RThreadManager.INSTANCE.addThread(thread);

        return RNull.instance;
    }
}
