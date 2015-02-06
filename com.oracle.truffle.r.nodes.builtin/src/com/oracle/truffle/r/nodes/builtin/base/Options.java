/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.data.*;

@RBuiltin(name = "options", kind = INTERNAL, parameterNames = {"..."})
/**
 * This could be refactored using a recursive child node to handle simple cases, but it's unlikely to be the fast path.
 */
public abstract class Options extends RBuiltinNode {

    private final ConditionProfile argNameNull = ConditionProfile.createBinaryProfile();

    @TruffleBoundary
    @Specialization
    protected RList options(@SuppressWarnings("unused") RMissing x) {
        controlVisibility();
        Set<Map.Entry<String, Object>> optionSettings = ROptions.getValues();
        Object[] data = new Object[optionSettings.size()];
        String[] names = new String[data.length];
        int i = 0;
        for (Map.Entry<String, Object> entry : optionSettings) {
            names[i] = entry.getKey();
            data[i] = entry.getValue();
            i++;
        }
        return RDataFactory.createList(data, RDataFactory.createStringVector(names, RDataFactory.COMPLETE_VECTOR));
    }

    @Specialization(guards = "isMissing")
    protected Object optionsMissing(@SuppressWarnings("unused") RArgsValuesAndNames args) {
        return options(RMissing.instance);
    }

    @Specialization(guards = "!isMissing")
    @TruffleBoundary
    protected Object options(RArgsValuesAndNames args) {
        Object[] values = args.getValues();
        String[] argNames = args.getNames();
        Object[] data = new Object[values.length];
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            String argName = argNames[i];
            Object value = values[i];
            if (argNameNull.profile(argName == null)) {
                // getting
                String optionName = null;
                if (value instanceof RStringVector) {
                    optionName = ((RStringVector) value).getDataAt(0); // ignore rest (cf GnuR)
                } else if (value instanceof String) {
                    optionName = (String) value;
                } else if (value instanceof RList) {
                    // setting
                    RList list = (RList) value;
                    RStringVector thisListnames = null;
                    Object nn = list.getNames();
                    if (nn instanceof RStringVector) {
                        thisListnames = (RStringVector) nn;
                    } else {
                        throw RInternalError.shouldNotReachHere();
                    }
                    Object[] listData = new Object[list.getLength()];
                    String[] listNames = new String[listData.length];
                    for (int j = 0; j < listData.length; j++) {
                        String name = thisListnames.getDataAt(j);
                        Object previousVal = ROptions.getValue(name);
                        listData[j] = previousVal == null ? RNull.instance : previousVal;
                        listNames[j] = name;
                        ROptions.setValue(name, list.getDataAtAsObject(j));
                    }
                    // if this is the only argument, no need to copy, can just return
                    if (values.length == 1) {
                        data = listData;
                        names = listNames;
                        break;
                    } else {
                        // resize and copy
                        int newSize = values.length - 1 + listData.length;
                        Object[] newData = new Object[newSize];
                        String[] newNames = new String[newSize];
                        System.arraycopy(data, 0, newData, 0, i);
                        System.arraycopy(names, 0, newNames, 0, i);
                        System.arraycopy(listData, 0, newData, i, listData.length);
                        System.arraycopy(listNames, 0, newNames, i, listNames.length);
                        data = newData;
                        names = newNames;
                    }
                } else {
                    throw RError.error(getEncapsulatingSourceSection(), Message.INVALID_UNNAMED_ARGUMENT);
                }
                Object optionVal = ROptions.getValue(optionName);
                data[i] = optionVal == null ? RNull.instance : optionVal;
                names[i] = optionName;
            } else {
                // setting
                Object previousVal = ROptions.getValue(argName);
                data[i] = previousVal == null ? RNull.instance : previousVal;
                names[i] = argName;
                ROptions.setValue(argName, value);
                // any settings means result is invisible
                RContext.setVisible(false);
            }
        }
        return RDataFactory.createList(data, RDataFactory.createStringVector(names, RDataFactory.COMPLETE_VECTOR));
    }

    protected boolean isMissing(RArgsValuesAndNames args) {
        return args.isMissing();    // length() == 1 && args.getValues()[0] == RMissing.instance;
    }

}
