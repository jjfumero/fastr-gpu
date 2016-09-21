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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.closures.RClosures;
import com.oracle.truffle.r.runtime.data.model.RAbstractComplexVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

@ValueType
public final class RComplex extends RScalarVector implements RAbstractComplexVector {

    public static final RComplex NA = new RComplex(RRuntime.COMPLEX_NA_REAL_PART, RRuntime.COMPLEX_NA_IMAGINARY_PART);
    public static final RComplex DEFAULT = new RComplex(0.0, 0.0);

    private final double realPart;
    private final double imaginaryPart;

    private RComplex(double realPart, double imaginaryPart) {
        this.realPart = realPart;
        this.imaginaryPart = imaginaryPart;
    }

    public static RComplex valueOf(double real, double imaginary) {
        return new RComplex(real, imaginary);
    }

    @Override
    public RAbstractVector castSafe(RType type) {
        switch (type) {
            case Complex:
                return this;
            case Character:
                return RClosures.createComplexToStringVector(this);
            case List:
                return RClosures.createAbstractVectorToListVector(this);
            default:
                return null;
        }
    }

    @Override
    public RComplexVector materialize() {
        return RDataFactory.createComplexVectorFromScalar(this);
    }

    public RComplex getDataAt(int index) {
        assert index == 0;
        return this;
    }

    @Override
    public RType getRType() {
        return RType.Complex;
    }

    public double getRealPart() {
        return realPart;
    }

    public double getImaginaryPart() {
        return imaginaryPart;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return toString(RRuntime.doubleToString(realPart), RRuntime.doubleToString(imaginaryPart));
    }

    public String toString(String realPartString, String imaginaryPartString) {
        CompilerAsserts.neverPartOfCompilation();
        return isNA() ? "NA" : realPartString + (imaginaryPart < 0.0 ? "" : "+") + imaginaryPartString + "i";
    }

    @Override
    public boolean isNA() {
        return RRuntime.isNA(realPart);
    }

    public boolean isZero() {
        return realPart == 0.0 && imaginaryPart == 0.0;
    }

    public boolean isRealOne() {
        return realPart == 1.0 && imaginaryPart == 0.0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RComplex)) {
            return false;
        }
        RComplex other = (RComplex) obj;
        return isNA() && other.isNA() || (realPart == other.getRealPart() && imaginaryPart == other.getImaginaryPart());
    }

    @Override
    public int hashCode() {
        return Double.hashCode(realPart) ^ Double.hashCode(imaginaryPart);
    }

    public double abs() {
        if (!RRuntime.isFinite(realPart) || !RRuntime.isFinite(imaginaryPart)) {
            if (Double.isInfinite(realPart) || Double.isInfinite(imaginaryPart)) {
                return Double.POSITIVE_INFINITY;
            } else if (Double.isNaN(imaginaryPart)) {
                return imaginaryPart;
            } else {
                return realPart;
            }
        } else {
            return Math.sqrt(realPart * realPart + imaginaryPart * imaginaryPart);
        }
    }
}
