package com.oracle.truffle.r.library.gpu;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_event;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RIntSequence;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public abstract class OCLVectorMulBuiltin extends RExternalBuiltinNode.Arg2 {

    // @formatter:off
    private static final String SAXPY_KERNELS=
                    "__kernel void saxpyDouble( __global double *a, __global double *b, __global double *c)\n" +
                    "{\n" +
                    "   int idx = get_global_id(0);\n" +
                    "   c[idx]  = a[idx] * b[idx];" +
                    "}\n" +
                    "__kernel void saxpyInteger( __global int *a, __global int *b, __global int *c)\n" +
                    "{\n" +
                    "   int idx = get_global_id(0);\n" +
                    "   c[idx]  = a[idx] * b[idx];" +
                    "}";
    // @formatter:on

    private static cl_context context;
    private static cl_command_queue commandQueue;
    private static cl_kernel kernelDouble;
    private static cl_kernel kernelInteger;
    private static cl_program program;
    private static boolean initializationDone;

    private static boolean getGPUPlatform(cl_platform_id platform) {
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL.CL_CONTEXT_PLATFORM, platform);
        context = CL.clCreateContextFromType(contextProperties, CL.CL_DEVICE_TYPE_GPU, null, null, null);
        if (context == null) {
            return false;
        }
        return true;
    }

    private static void oclInitialization() {

        // Obtain the number of platforms
        int numPlatformsArray[] = new int[1];
        CL.clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
        CL.clGetPlatformIDs(platforms.length, platforms, null);

        // get the first GPU platform
        boolean found = false;
        for (int i = 0; i < numPlatforms; i++) {
            // System.out.println("Checking platform: " + i);
            if (getGPUPlatform(platforms[i])) {
                found = true;
                break;
            }
        }

        if (!found) {
            cl_context_properties contextProperties = new cl_context_properties();
            contextProperties.addProperty(CL.CL_CONTEXT_PLATFORM, platforms[0]);
            // System.out.println("No GPU. Using CPU instead");
            context = CL.clCreateContextFromType(contextProperties, CL.CL_DEVICE_TYPE_CPU, null, null, null);
            if (context == null) {
                System.out.println("Unable to create a context");
                return;
            }
        }

        CL.setExceptionsEnabled(true);
        long numBytes[] = new long[1];
        CL.clGetContextInfo(context, CL.CL_CONTEXT_DEVICES, 0, null, numBytes);
        int numDevices = (int) numBytes[0] / Sizeof.cl_device_id;
        cl_device_id devices[] = new cl_device_id[numDevices];
        CL.clGetContextInfo(context, CL.CL_CONTEXT_DEVICES, numBytes[0], Pointer.to(devices), null);
        commandQueue = CL.clCreateCommandQueue(context, devices[0], CL.CL_QUEUE_PROFILING_ENABLE, null);
        String programSource = SAXPY_KERNELS;
        program = CL.clCreateProgramWithSource(context, 1, new String[]{programSource}, null, null);
        CL.clBuildProgram(program, 0, null, null, null, null);
        kernelDouble = CL.clCreateKernel(program, "saxpyDouble", null);
        kernelInteger = CL.clCreateKernel(program, "saxpyInteger", null);
        initializationDone = true;
    }

    private static void saxpyJOCLInteger(int size, int[] x, int[] y, int[] z) {

        final Pointer srcX = Pointer.to(x);
        final Pointer srcY = Pointer.to(y);

        cl_event[] writeEvents = new cl_event[2];
        writeEvents[0] = new cl_event();
        writeEvents[1] = new cl_event();

        // Memory allocation on GPU
        final int totalSize = Sizeof.cl_int * size;
        cl_mem srcMemX = CL.clCreateBuffer(context, CL.CL_MEM_READ_ONLY, totalSize, null, null);
        CL.clEnqueueWriteBuffer(commandQueue, srcMemX, CL.CL_TRUE, 0, totalSize, srcX, 0, null, writeEvents[0]);
        cl_mem srcMemY = CL.clCreateBuffer(context, CL.CL_MEM_READ_ONLY, totalSize, null, null);
        CL.clEnqueueWriteBuffer(commandQueue, srcMemY, CL.CL_TRUE, 0, totalSize, srcY, 0, null, writeEvents[1]);
        cl_mem srcMemZ = CL.clCreateBuffer(context, CL.CL_MEM_WRITE_ONLY, totalSize, null, null);

        CL.clSetKernelArg(kernelInteger, 0, Sizeof.cl_mem, Pointer.to(srcMemX));
        CL.clSetKernelArg(kernelInteger, 1, Sizeof.cl_mem, Pointer.to(srcMemY));
        CL.clSetKernelArg(kernelInteger, 2, Sizeof.cl_mem, Pointer.to(srcMemZ));

        long global_work_size[] = new long[]{size};

        cl_event kernelEvent = new cl_event();
        CL.clEnqueueNDRangeKernel(commandQueue, kernelInteger, 1, null, global_work_size, null, 0, null, kernelEvent);

        cl_event[] readEvents = new cl_event[1];
        readEvents[0] = new cl_event();
        CL.clEnqueueReadBuffer(commandQueue, srcMemZ, true, 0, Sizeof.cl_int * size, Pointer.to(z), 0, null, readEvents[0]);

        CL.clReleaseMemObject(srcMemX);
        CL.clReleaseMemObject(srcMemY);
        CL.clReleaseMemObject(srcMemZ);
    }

    @SuppressWarnings("unused")
    private static void saxpyJOCLDouble(int size, double[] x, double[] y, double[] z) {

        final Pointer srcX = Pointer.to(x);
        final Pointer srcY = Pointer.to(y);

        cl_event[] writeEvents = new cl_event[2];
        writeEvents[0] = new cl_event();
        writeEvents[1] = new cl_event();

        // Memory allocation on GPU
        final int totalSize = Sizeof.cl_double * size;
        cl_mem srcMemX = CL.clCreateBuffer(context, CL.CL_MEM_READ_ONLY, totalSize, null, null);
        CL.clEnqueueWriteBuffer(commandQueue, srcMemX, CL.CL_TRUE, 0, totalSize, srcX, 0, null, writeEvents[0]);
        cl_mem srcMemY = CL.clCreateBuffer(context, CL.CL_MEM_READ_ONLY, totalSize, null, null);
        CL.clEnqueueWriteBuffer(commandQueue, srcMemY, CL.CL_TRUE, 0, totalSize, srcY, 0, null, writeEvents[1]);
        cl_mem srcMemZ = CL.clCreateBuffer(context, CL.CL_MEM_WRITE_ONLY, totalSize, null, null);

        CL.clSetKernelArg(kernelDouble, 0, Sizeof.cl_mem, Pointer.to(srcMemX));
        CL.clSetKernelArg(kernelDouble, 1, Sizeof.cl_mem, Pointer.to(srcMemY));
        CL.clSetKernelArg(kernelDouble, 2, Sizeof.cl_mem, Pointer.to(srcMemZ));

        long global_work_size[] = new long[]{size};

        cl_event kernelEvent = new cl_event();
        CL.clEnqueueNDRangeKernel(commandQueue, kernelDouble, 1, null, global_work_size, null, 0, null, kernelEvent);
        CL.clWaitForEvents(1, new cl_event[]{kernelEvent});
        long[] kernelStart = new long[1];
        long[] kernelEnd = new long[1];
        CL.clGetEventProfilingInfo(kernelEvent, CL.CL_PROFILING_COMMAND_START, Sizeof.cl_ulong, Pointer.to(kernelStart), null);
        CL.clGetEventProfilingInfo(kernelEvent, CL.CL_PROFILING_COMMAND_END, Sizeof.cl_ulong, Pointer.to(kernelEnd), null);

        cl_event[] readEvents = new cl_event[1];
        readEvents[0] = new cl_event();

        CL.clEnqueueReadBuffer(commandQueue, srcMemZ, true, 0, Sizeof.cl_double * size, Pointer.to(z), 0, null, readEvents[0]);

        CL.clReleaseMemObject(srcMemX);
        CL.clReleaseMemObject(srcMemY);
        CL.clReleaseMemObject(srcMemZ);
    }

    @Specialization
    public RAbstractVector compute(RIntVector v1, RIntVector v2) {

        if (!initializationDone) {
            oclInitialization();
        }

        if (v1.getLength() != v2.getLength()) {
            return null;
        }

        int[] a = new int[v1.getLength()];
        int[] b = new int[v1.getLength()];
        for (int i = 0; i < v1.getLength(); i++) {
            a[i] = (int) v1.getDataAtAsObject(i);
            b[i] = (int) v2.getDataAtAsObject(i);
        }
        int[] c = new int[v1.getLength()];
        saxpyJOCLInteger(c.length, a, b, c);
        return RDataFactory.createIntVector(c, false);

    }

    @Specialization
    public RAbstractVector compute(RIntSequence v1, RIntSequence v2) {

        if (!initializationDone) {
            oclInitialization();
        }

        if (v1.getLength() != v2.getLength()) {
            return null;
        }

        int[] a = new int[v1.getLength()];
        int[] b = new int[v1.getLength()];
        for (int i = 0; i < v1.getLength(); i++) {
            a[i] = (int) v1.getDataAtAsObject(i);
            b[i] = (int) v2.getDataAtAsObject(i);
        }
        int[] c = new int[v1.getLength()];
        saxpyJOCLInteger(c.length, a, b, c);
        return RDataFactory.createIntVector(c, false);
    }
}
