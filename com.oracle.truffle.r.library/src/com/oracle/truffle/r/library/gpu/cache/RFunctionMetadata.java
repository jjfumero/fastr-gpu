package com.oracle.truffle.r.library.gpu.cache;

import uk.ac.ed.datastructures.interop.InteropTable;
import uk.ac.ed.datastructures.interop.Interoperable;

import com.oracle.truffle.r.library.gpu.types.TypeInfo;

public class RFunctionMetadata {

    private int nArgs;
    private String[] argsName;
    private Object[] argsPackageFirstInput;
    private Object firstValue;

    private TypeInfo outputType;
    private InteropTable interop;

    private Class<?>[] typeObject;
    private Interoperable interoperable;

    public RFunctionMetadata(int nArgs, String[] argsName, Object[] argsPackageFirstInput, Object firstValue, TypeInfo outputType, InteropTable interop, Class<?>[] typeObject,
                    Interoperable interoperable) {
        this.nArgs = nArgs;
        this.argsName = argsName;
        this.argsPackageFirstInput = argsPackageFirstInput;
        this.firstValue = firstValue;
        this.outputType = outputType;
        this.interop = interop;
        this.typeObject = typeObject;
        this.interoperable = interoperable;
    }

    public TypeInfo getOutputType() {
        return outputType;
    }

    public InteropTable getInterop() {
        return interop;
    }

    public Class<?>[] getTypeObject() {
        return typeObject;
    }

    public Interoperable getInteroperable() {
        return interoperable;
    }

    public int getnArgs() {
        return nArgs;
    }

    public String[] getArgsName() {
        return argsName;
    }

    public Object[] getArgsPackageFirstInput() {
        return argsPackageFirstInput;
    }

    public Object getFirstValue() {
        return firstValue;
    }

}
