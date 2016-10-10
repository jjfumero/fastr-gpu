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
package com.oracle.truffle.r.library.gpu.cache;

import com.oracle.truffle.api.RootCallTarget;

public class RCacheObjects {

    private RootCallTarget rootCallTarget;
    private String[] scopeVars;
    private Object[] lexicalScopeVars;
    private boolean gpuExecution = false;

    private RFunctionMetadata rfunctionMetadata;
    private int idExecution;

    public RCacheObjects(RootCallTarget rootCallTarget) {
        this.rootCallTarget = rootCallTarget;
        this.idExecution = 0;
    }

    public RCacheObjects(RootCallTarget rootCallTarget, String[] scopeVarsName, Object[] lexicalScopeVars) {
        this(rootCallTarget);
        this.scopeVars = scopeVarsName;
        this.lexicalScopeVars = lexicalScopeVars;
    }

    public RootCallTarget getRootCallTarget() {
        return rootCallTarget;
    }

    public String[] getScopeVars() {
        return scopeVars;
    }

    public Object[] getLexicalScopeVars() {
        return lexicalScopeVars;
    }

    public void insertRFuctionMetadata(RFunctionMetadata metadata) {
        this.rfunctionMetadata = metadata;
    }

    public RFunctionMetadata getRFunctionMetadata() {
        return this.rfunctionMetadata;
    }

    public void incID() {
        idExecution++;
    }

    public int getIDExecution() {
        return idExecution;
    }

    public void enableGPUExecution() {
        this.gpuExecution = true;
    }

    public boolean isGPUExecution() {
        return gpuExecution;
    }

    public void deoptimize() {
        this.gpuExecution = false;
        this.rootCallTarget.resetIDForOpenCL();
        this.idExecution = 0;
    }
}
