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

import uk.ac.ed.accelerator.common.GraalAcceleratorDevice;
import uk.ac.ed.accelerator.common.GraalAcceleratorPlatform;
import uk.ac.ed.accelerator.common.GraalAcceleratorSystem;
import uk.ac.ed.accelerator.wocl.OCLDeviceInfo;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;

/**
 * Node built-in for communication with Marawacc (GPU Backend for Graal).
 *
 */
public abstract class MarawaccOCLInfoBuiltin extends RExternalBuiltinNode.Arg0 {

    @TruffleBoundary
    private static void printInfo(OCLDeviceInfo deviceInfo) {
        System.out.println("NAME             : " + deviceInfo.getDeviceName().replaceAll("^[ ]+", ""));
        System.out.println("VENDOR           : " + deviceInfo.getVendorName());
        System.out.println("TYPE             : " + deviceInfo.getDeviceType());
        System.out.println("DRIVER           : " + deviceInfo.getDriverVersion());
        System.out.println("MAX COMPUTE UNITS: " + deviceInfo.getMaxComputeUnits());
        System.out.println("MAX FREQUENCY    : " + deviceInfo.getMaxClockFrequency());
        System.out.println("GLOBAL MEMORY    : " + deviceInfo.getGlobalMemSize());
        System.out.println("LOCAL  MEMORY    : " + deviceInfo.getLocalMemSize());
        System.out.println("ENDIANESS        : " + deviceInfo.getEndianess());
    }

    @Specialization
    protected String clInfo() {
        MarawaccInitilizationNodeGen.marawaccInitialization();
        GraalAcceleratorPlatform platform = GraalAcceleratorSystem.getInstance().getPlatform();
        GraalAcceleratorDevice device = platform.getDevice();
        OCLDeviceInfo deviceInfo = (OCLDeviceInfo) device.getDeviceInfo();
        printInfo(deviceInfo);
        return "";
    }
}
