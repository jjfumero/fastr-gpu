package com.oracle.truffle.r.library.gpu.deoptimization;

import uk.ac.ed.datastructures.common.PArray;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public class MarawaccFrameState {

    RAbstractVector input;
    RFunction function;
    RootCallTarget target;
    RAbstractVector[] additionalArgs;
    Object[] lexicalScopes;
    int numArgumentsOriginalFunction;
    TypeInfoList inputTypeList;
    private PArray<?> inputPArray;

    public MarawaccFrameState(RAbstractVector input, RFunction function, RootCallTarget target, RAbstractVector[] additionalArgs, Object[] lexicalScopes, int numArgumentsOriginalFunction,
                    TypeInfoList inputTypeList, PArray<?> inputPArray) {
        super();
        this.input = input;
        this.function = function;
        this.target = target;
        this.additionalArgs = additionalArgs;
        this.lexicalScopes = lexicalScopes;
        this.numArgumentsOriginalFunction = numArgumentsOriginalFunction;
        this.inputTypeList = inputTypeList;
        this.inputPArray = inputPArray;
    }

    public RAbstractVector getInput() {
        return input;
    }

    public RFunction getFunction() {
        return function;
    }

    public RootCallTarget getTarget() {
        return target;
    }

    public RAbstractVector[] getAdditionalArgs() {
        return additionalArgs;
    }

    public Object[] getLexicalScopes() {
        return lexicalScopes;
    }

    public int getNumArgumentsOriginalFunction() {
        return numArgumentsOriginalFunction;
    }

    public TypeInfoList getTypeInfoList() {
        return inputTypeList;
    }

    public PArray<?> getInputPArray() {
        return inputPArray;
    }
}
