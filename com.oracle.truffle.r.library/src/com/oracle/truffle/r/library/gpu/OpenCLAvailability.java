package com.oracle.truffle.r.library.gpu;

import uk.ac.ed.accelerator.common.GraalAcceleratorDevice;
import uk.ac.ed.accelerator.common.GraalAcceleratorPlatform;
import uk.ac.ed.accelerator.common.GraalAcceleratorSystem;
import uk.ac.ed.accelerator.wocl.OCLDeviceInfo;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RIntVector;

public abstract class OpenCLAvailability extends RExternalBuiltinNode.Arg0 {

    public static class OpenCLInit {
        private static OCLDeviceInfo getDeviceInfo() {
            MarawaccInitilizationNodeGen.marawaccInitialization();
            GraalAcceleratorPlatform platform = GraalAcceleratorSystem.getInstance().getPlatform();
            GraalAcceleratorDevice device = platform.getDevice();
            OCLDeviceInfo deviceInfo = (OCLDeviceInfo) device.getDeviceInfo();
            return deviceInfo;
        }
    }

    public static boolean isOpenCLEnabled() {
        OCLDeviceInfo deviceInfo = OpenCLInit.getDeviceInfo();
        if (deviceInfo != null) {
            return true;
        } else {
            return false;
        }
    }

    @Specialization
    public RIntVector isOpenCLAvailable() {
        OCLDeviceInfo deviceInfo = OpenCLInit.getDeviceInfo();
        if (deviceInfo != null) {
            return RDataFactory.createIntVector(new int[]{1}, true);
        } else {
            return RDataFactory.createIntVector(new int[]{0}, true);
        }
    }
}
