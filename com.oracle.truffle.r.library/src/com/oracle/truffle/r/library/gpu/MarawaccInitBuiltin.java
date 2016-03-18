package com.oracle.truffle.r.library.gpu;

import uk.ac.ed.accelerator.ocl.OCLRuntimeUtils;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;

public abstract class MarawaccInitBuiltin extends RExternalBuiltinNode.Arg0 {

    private static boolean initializated = false;

    private static void communicateToMarawaccInit() {
        try {
            OCLRuntimeUtils.waitForTheOpenCLInitialization();
            initializated = true;
        } catch (InterruptedException e) {
            System.err.println("Error during Marawacc Initialization");
            e.printStackTrace();
        }
    }

    public static void marawaccInitialization() {
        if (!initializated) {
            communicateToMarawaccInit();
        }
    }

    @Specialization
    public Object doInitialization() {
        marawaccInitialization();
        return "";
    }
}
