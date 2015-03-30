/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.data;

import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.model.*;

public class RExpression implements RShareable, RAbstractContainer {

    private RList data;

    public RExpression(RList data) {
        this.data = data;
    }

    public RType getRType() {
        return RType.Expression;
    }

    public RList getList() {
        return data;
    }

    public Object getDataAt(int index) {
        return data.getDataAt(index);
    }

    @Override
    public RAttributes initAttributes() {
        return data.initAttributes();
    }

    @Override
    public RAttributes getAttributes() {
        return data.getAttributes();
    }

    @Override
    public final RAttributes resetAllAttributes(boolean nullify) {
        return data.resetAllAttributes(nullify);
    }

    public boolean isComplete() {
        return data.isComplete();
    }

    @Override
    public int getLength() {
        return data.getLength();
    }

    @Override
    public RAbstractContainer resize(int size) {
        return data.resize(size);
    }

    public boolean hasDimensions() {
        return data.hasDimensions();
    }

    public int[] getDimensions() {
        return data.getDimensions();
    }

    @Override
    public void setDimensions(int[] newDimensions) {
        data.setDimensions(newDimensions);
    }

    public Class<?> getElementClass() {
        return RExpression.class;
    }

    public RExpression materializeNonShared() {
        RVector d = data.materializeNonShared();
        return data != d ? RDataFactory.createExpression((RList) d) : this;
    }

    public Object getDataAtAsObject(int index) {
        return data.getDataAtAsObject(index);
    }

    @Override
    public RStringVector getNames(RAttributeProfiles attrProfiles) {
        return data.getNames(attrProfiles);
    }

    @Override
    public void setNames(RStringVector newNames) {
        data.setNames(newNames);
    }

    @Override
    public RList getDimNames() {
        return data.getDimNames();
    }

    @Override
    public void setDimNames(RList newDimNames) {
        data.setDimNames(newDimNames);
    }

    @Override
    public Object getRowNames(RAttributeProfiles attrProfiles) {
        return data.getRowNames(attrProfiles);
    }

    @Override
    public void setRowNames(RAbstractVector rowNames) {
        data.setRowNames(rowNames);
    }

    public RStringVector getClassHierarchy() {
        return data.getClassHierarchy();
    }

    public boolean isObject(RAttributeProfiles attrProfiles) {
        return false;
    }

    @Override
    public void markNonTemporary() {
        data.markNonTemporary();
    }

    @Override
    public boolean isTemporary() {
        return data.isTemporary();
    }

    @Override
    public boolean isShared() {
        return data.isShared();
    }

    @Override
    public RVector makeShared() {
        return data.makeShared();
    }

    @Override
    public RExpression copy() {
        return RDataFactory.createExpression((RList) data.copy());
    }

    @Override
    public RShareable materializeToShareable() {
        return this;
    }

    @Override
    public RAbstractContainer setClassAttr(RStringVector classAttr, boolean convertToInt) {
        return data.setClassAttr(classAttr, convertToInt);
    }

}
