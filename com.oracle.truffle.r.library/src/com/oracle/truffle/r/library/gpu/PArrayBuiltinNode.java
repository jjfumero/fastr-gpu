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

import uk.ac.ed.datastructures.common.PArray;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccTypeException;
import com.oracle.truffle.r.library.gpu.options.ASTxOptions;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public abstract class PArrayBuiltinNode extends RExternalBuiltinNode.Arg1 {

    @Specialization
    public PArray<?> createPArray(RAbstractVector input) {

        PArray<?> parray = null;
        TypeInfoList inputTypeList = null;
        RAbstractVector[] additionalArgs = null;
        try {
            inputTypeList = ASTxUtils.typeInferenceWithPArray(input, additionalArgs);
        } catch (MarawaccTypeException e) {
            e.printStackTrace();
        }

        if (ASTxOptions.optimizeRSequence) {
            // Rsequence optimization
            parray = ASTxUtils.marshalWithReferencesAndSequenceOptimize(input, additionalArgs, inputTypeList);
        } else {
            parray = ASTxUtils.marshal(input, additionalArgs, inputTypeList);
        }

        System.out.println(parray);
        return parray;
    }
}
