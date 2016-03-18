package com.oracle.truffle.r.library.gpu;

import uk.ac.ed.accelerator.common.GraalAcceleratorDevice;
import uk.ac.ed.accelerator.common.GraalAcceleratorPlatform;
import uk.ac.ed.accelerator.common.GraalAcceleratorSystem;
import uk.ac.ed.accelerator.wocl.OCLDeviceInfo;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;

/**
 * Node builtin for communication with Marawacc (GPU Backend for Graal).
 *
 */
public abstract class MarawaccOCLInfoBuiltin extends RExternalBuiltinNode.Arg0 {

    @Specialization
    protected String clInfo() {
        MarawaccInitBuiltinNodeGen.marawaccInitialization();
        GraalAcceleratorPlatform platform = GraalAcceleratorSystem.getInstance().getPlatform();
        GraalAcceleratorDevice device = platform.getDevice();
        // System.out.println(device.toString());
        OCLDeviceInfo deviceInfo = (OCLDeviceInfo) device.getDeviceInfo();
        System.out.println("NAME: " + deviceInfo.getDeviceName());
        System.out.println("VENDOR: " + deviceInfo.getVendorName());
        System.out.println("TYPE: " + deviceInfo.getDeviceType());
        System.out.println("DRIVER: " + deviceInfo.getDriverVersion());
        return "";
    }
}
