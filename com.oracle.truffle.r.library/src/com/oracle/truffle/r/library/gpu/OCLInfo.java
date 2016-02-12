package com.oracle.truffle.r.library.gpu;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;

import uk.ac.ed.accelerator.common.*;
import uk.ac.ed.accelerator.ocl.OCLRuntimeUtils;
import uk.ac.ed.accelerator.wocl.OCLDeviceInfo;

public abstract class OCLInfo extends RExternalBuiltinNode.Arg0 {

    private static boolean initialized = false;

    private static void gpuInitialization() {
        try {
            OCLRuntimeUtils.waitForTheOpenCLInitialization();
            initialized = true;
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("Error");
        }
    }

    @Specialization
    protected String clInfo() {
        if (!initialized) {
            gpuInitialization();
        }
        GraalAcceleratorPlatform platform = GraalAcceleratorSystem.getInstance().getPlatform();
        GraalAcceleratorDevice device = platform.getDevice();
        System.out.println(device.toString());
        OCLDeviceInfo deviceInfo = (OCLDeviceInfo) device.getDeviceInfo();
        System.out.println("NAME: " + deviceInfo.getDeviceName());
        System.out.println("VENDOR: " + deviceInfo.getVendorName());
        System.out.println("TYPE: " + deviceInfo.getDeviceType());
        System.out.println("DRIVER: " + deviceInfo.getDriverVersion());
        return "";
    }
}
