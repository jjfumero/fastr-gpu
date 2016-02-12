package com.oracle.truffle.r.library.gpu;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;

import uk.ac.ed.accelerator.common.*;
import uk.ac.ed.accelerator.ocl.OCLRuntimeUtils;

public abstract class OCLInfo extends RExternalBuiltinNode.Arg0 {

    private static void gpuInitialization() {
        // There is no guarantee the GraalAcceleratorPlatform is prepared.
        // Check is needed.
        try {
            OCLRuntimeUtils.waitForTheOpenCLInitialization();
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("Error");
        }
    }

    @Specialization
    protected String clInfo() {
        StringBuffer s = new StringBuffer();
        gpuInitialization();
        GraalAcceleratorSystem system = GraalAcceleratorSystem.getInstance();
        GraalAcceleratorPlatform platform = system.getPlatform();
        GraalAcceleratorDevice device = platform.getDevice();
        s.append(device.toString());
        return s.toString();
    }
}
