/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.ffi.jnr;

import com.oracle.truffle.r.runtime.context.*;
import com.oracle.truffle.r.runtime.context.RContext.ContextState;
import com.oracle.truffle.r.runtime.ffi.*;
import com.oracle.truffle.r.runtime.ffi.generic.Generic_Grid;
import com.oracle.truffle.r.runtime.ffi.generic.Generic_Tools;

/**
 * JNR/JNI-based factory. The majority of the FFI instances are instantiated on demand.
 */
public class JNR_RFFIFactory extends RFFIFactory implements RFFI {

    public JNR_RFFIFactory() {
    }

    @Override
    protected void initialize() {
        // This must load early as package libraries reference symbols in it.
        getCallRFFI();
        /*
         * Some package C code calls these functions and, therefore, expects the linpack symbols to
         * be available, which will not be the case unless one of the functions has already been
         * called from R code. So we eagerly load the library to define the symbols.
         */
        JNR_RAppl.linpack();
    }

    /**
     * Placeholder class for context-specific native state.
     */
    private static class ContextStateImpl implements RContext.ContextState {

    }

    @Override
    public ContextState newContext(RContext context) {
        return new ContextStateImpl();
    }

    @Override
    protected RFFI createRFFI() {
        return this;
    }

    private BaseRFFI baseRFFI;

    @Override
    public BaseRFFI getBaseRFFI() {
        if (baseRFFI == null) {
            baseRFFI = new JNR_Base();
        }
        return baseRFFI;
    }

    private LapackRFFI lapackRFFI;

    @Override
    public LapackRFFI getLapackRFFI() {
        if (lapackRFFI == null) {
            lapackRFFI = new JNR_Lapack();
        }
        return lapackRFFI;
    }

    private RApplRFFI rApplRFFI;

    @Override
    public RApplRFFI getRApplRFFI() {
        if (rApplRFFI == null) {
            rApplRFFI = new JNR_RAppl();
        }
        return rApplRFFI;
    }

    private StatsRFFI statsRFFI;

    @Override
    public StatsRFFI getStatsRFFI() {
        if (statsRFFI == null) {
            statsRFFI = new JNR_Stats();
        }
        return statsRFFI;
    }

    private ToolsRFFI toolsRFFI;

    @Override
    public ToolsRFFI getToolsRFFI() {
        if (toolsRFFI == null) {
            toolsRFFI = new Generic_Tools();
        }
        return toolsRFFI;
    }

    private GridRFFI gridRFFI;

    @Override
    public GridRFFI getGridRFFI() {
        if (gridRFFI == null) {
            gridRFFI = new Generic_Grid();
        }
        return gridRFFI;
    }

    private UserRngRFFI userRngRFFI;

    @Override
    public UserRngRFFI getUserRngRFFI() {
        if (userRngRFFI == null) {
            userRngRFFI = new JNR_UserRng();
        }
        return userRngRFFI;
    }

    private CRFFI cRFFI;

    @Override
    public CRFFI getCRFFI() {
        if (cRFFI == null) {
            cRFFI = new CRFFI_JNR_Invoke();
        }
        return cRFFI;
    }

    private CallRFFI callRFFI;

    @Override
    public CallRFFI getCallRFFI() {
        if (callRFFI == null) {
            callRFFI = new CallRFFIWithJNI();
        }
        return callRFFI;
    }

    private ZipRFFI zipRFFI;

    @Override
    public ZipRFFI getZipRFFI() {
        if (zipRFFI == null) {
            zipRFFI = new JNR_Zip();
        }
        return zipRFFI;
    }

    private PCRERFFI pcreRFFI;

    @Override
    public PCRERFFI getPCRERFFI() {
        if (pcreRFFI == null) {
            pcreRFFI = new JNR_PCRE();
        }
        return pcreRFFI;
    }

}
