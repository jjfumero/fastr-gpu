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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import java.util.*;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ffi.*;
import com.oracle.truffle.r.runtime.ffi.DLL.DLLException;
import com.oracle.truffle.r.runtime.ffi.DLL.DLLInfo;

public class DynLoadFunctions {

    private static final String DLLINFOLIST_CLASS = "DLLInfoList";

    @RBuiltin(name = "dyn.load", kind = INTERNAL, parameterNames = {"lib", "local", "now", "unused"})
    public abstract static class DynLoad extends RInvisibleBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected RList doDynLoad(RAbstractStringVector libVec, RAbstractLogicalVector localVec, byte now, @SuppressWarnings("unused") String unused) {
            controlVisibility();
            // Length checked by GnuR
            if (libVec.getLength() > 1) {
                throw RError.error(this, RError.Message.TYPE_EXPECTED, RType.Character.getName());
            }
            String lib = libVec.getDataAt(0);
            // Length not checked by GnuR
            byte local = localVec.getDataAt(0);
            try {
                DLLInfo dllInfo = DLL.loadPackageDLL(lib, asBoolean(local), asBoolean(now));
                return dllInfo.toRList();
            } catch (DLLException ex) {
                throw RError.error(this, ex);
            }
        }

        private static boolean asBoolean(byte b) {
            return b == RRuntime.LOGICAL_TRUE ? true : false;
        }
    }

    @RBuiltin(name = "dyn.unload", kind = INTERNAL, parameterNames = {"lib"})
    public abstract static class DynUnload extends RInvisibleBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected RNull doDynunload(String lib) {
            controlVisibility();
            try {
                DLL.unload(lib);
            } catch (DLLException ex) {
                throw RError.error(this, ex);
            }
            return RNull.instance;
        }

    }

    @RBuiltin(name = "getLoadedDLLs", kind = INTERNAL, parameterNames = {})
    public abstract static class GetLoadedDLLs extends RBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected RList doGetLoadedDLLs() {
            controlVisibility();
            ArrayList<DLLInfo> dlls = DLL.getLoadedDLLs();
            String[] names = new String[dlls.size()];
            Object[] data = new Object[names.length];
            for (int i = 0; i < names.length; i++) {
                DLLInfo dllInfo = dlls.get(i);
                // name field is used a list element name
                names[i] = dllInfo.name;
                data[i] = dllInfo.toRList();
            }
            RList result = RDataFactory.createList(data, RDataFactory.createStringVector(names, RDataFactory.COMPLETE_VECTOR));
            result.setClassAttr(RDataFactory.createStringVectorFromScalar(DLLINFOLIST_CLASS), false);
            return result;
        }
    }

    @RBuiltin(name = "is.loaded", kind = INTERNAL, parameterNames = {"symbol", "package", "type"})
    public abstract static class IsLoaded extends RBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        protected byte isLoaded(String symbol, String packageName, String type) {
            controlVisibility();
            boolean found = DLL.findRegisteredSymbolinInDLL(symbol, packageName) != null;
            return RRuntime.asLogical(found);
        }
    }

    @RBuiltin(name = "getSymbolInfo", kind = INTERNAL, parameterNames = {"symbol", "package", "withReg"})
    public abstract static class GetSymbolInfo extends RBuiltinNode {

        @Specialization
        @TruffleBoundary
        protected Object getSymbolInfo(RAbstractStringVector symbol, String packageName, byte withReg) {
            controlVisibility();
            DLL.SymbolInfo symbolInfo = DLL.findSymbolInfo(RRuntime.asString(symbol), packageName);
            return getResult(symbolInfo, withReg);
        }

        @Specialization(guards = "isDLLInfo(externalPtr)")
        @TruffleBoundary
        protected Object getSymbolInfo(RAbstractStringVector symbol, RExternalPtr externalPtr, byte withReg) {
            controlVisibility();
            DLL.DLLInfo dllInfo = DLL.getDLLInfoForId((int) externalPtr.getAddr());
            if (dllInfo == null) {
                throw RError.error(this, RError.Message.REQUIRES_NAME_DLLINFO);
            }
            DLL.SymbolInfo symbolInfo = DLL.findSymbolInDLL(RRuntime.asString(symbol), dllInfo);
            return getResult(symbolInfo, withReg);
        }

        private static Object getResult(DLL.SymbolInfo symbolInfo, byte withReg) {
            if (symbolInfo != null) {
                return symbolInfo.createRSymbolObject(new DLL.RegisteredNativeType(DLL.NativeSymbolType.Any, null, null), RRuntime.fromLogical(withReg));
            } else {
                return RNull.instance;
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object getSymbolInfo(Object symbol, Object packageName, Object withReg) {
            throw RError.error(this, RError.Message.REQUIRES_NAME_DLLINFO);
        }

        public static boolean isDLLInfo(RExternalPtr externalPtr) {
            return DLL.isDLLInfo(externalPtr);
        }

    }

}
