package com.oracle.truffle.r.library.gpu;

import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.jpai.ArrayFunction;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.library.gpu.cache.MarawaccPackage;
import com.oracle.truffle.r.library.gpu.cache.RMarawaccPromises;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;

public abstract class MarawaccExecuteNode extends RExternalBuiltinNode.Arg1 {

    @SuppressWarnings({"rawtypes", "unchecked", "unused"})
    @Specialization
    public Object executeMarawacc(ArrayFunction<?, ?> marawaccFunction) {

        MarawaccPackage last = RMarawaccPromises.INSTANCE.getLast();
        ArrayFunction<?, ?> arrayFunction = last.getArrayFunction();
        MarawaccPackage first = RMarawaccPromises.INSTANCE.getPackage(0);
        PArray data = (PArray) first.getList().get(0);

        PArray result = arrayFunction.apply(data);
        TypeInfo outTypeInfo = (TypeInfo) last.get(1);

        // Clean promises for the next execution
        RMarawaccPromises.INSTANCE.clean();

        return ASTxUtils.unMarshallResultFromPArrays(outTypeInfo, result);
    }
}
