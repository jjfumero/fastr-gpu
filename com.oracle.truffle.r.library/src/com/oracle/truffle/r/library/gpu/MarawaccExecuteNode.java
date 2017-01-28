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
package com.oracle.truffle.r.library.gpu;

import uk.ac.ed.accelerator.truffle.ASTxOptions;
import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.jpai.ArrayFunction;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.library.gpu.cache.MarawaccPackage;
import com.oracle.truffle.r.library.gpu.cache.RMarawaccFutures;
import com.oracle.truffle.r.library.gpu.cache.RMarawaccPromises;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

/**
 * It will execute an operation and unmarshal the data. It will execute everything in the previous
 * pipeline to the given operation. This is a blocking operation.
 */
public abstract class MarawaccExecuteNode extends RExternalBuiltinNode.Arg1 {

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PArray executeFunction(ArrayFunction<?, ?> marawaccFunction) {
        if (ASTxOptions.useAsyncComputation) {
            return RMarawaccFutures.INSTANCE.getPArray(marawaccFunction);
        } else {
            MarawaccPackage first = RMarawaccPromises.INSTANCE.getPackage(0);
            PArray data = first.getpArray();
            PArray<?> result = marawaccFunction.apply(data);
            return result;
        }
    }

    @SuppressWarnings("rawtypes")
    private static RAbstractVector unmarshall(PArray result, ArrayFunction<?, ?> marawaccFunction) {
        MarawaccPackage marawaccPackage = null;
        if (ASTxOptions.useAsyncComputation) {
            marawaccPackage = RMarawaccFutures.INSTANCE.getPackageForArrayFunction(marawaccFunction);
        } else {
            marawaccPackage = RMarawaccPromises.INSTANCE.getPackageForArrayFunction(marawaccFunction);
        }
        TypeInfo outTypeInfo = marawaccPackage.getTypeInfo();
        return ASTxUtils.unMarshallResultFromPArrays(outTypeInfo, result);
    }

    @Specialization
    public RAbstractVector executeMarawacc(ArrayFunction<?, ?> marawaccFunction) {
        PArray<?> result = executeFunction(marawaccFunction);
        RAbstractVector rResult = unmarshall(result, marawaccFunction);

        if (ASTxOptions.useAsyncComputation) {
            RMarawaccFutures.INSTANCE.clean();
        } else {
            RMarawaccPromises.INSTANCE.clean();
        }
        return rResult;
    }
}
