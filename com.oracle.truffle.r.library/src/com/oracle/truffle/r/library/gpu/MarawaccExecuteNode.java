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

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Specialization
    public Object executeMarawacc(ArrayFunction<?, ?> marawaccFunction) {

        MarawaccPackage marawaccPackage = RMarawaccPromises.INSTANCE.getPackageForArrayFunction(marawaccFunction);

        MarawaccPackage first = RMarawaccPromises.INSTANCE.getPackage(0);
        PArray data = (PArray) first.getList().get(0);

        PArray result = marawaccFunction.apply(data);
        TypeInfo outTypeInfo = (TypeInfo) marawaccPackage.get(1);

        // Clean promises for the next execution
        RMarawaccPromises.INSTANCE.clean();

        // Do the unmarshalling
        return ASTxUtils.unMarshallResultFromPArrays(outTypeInfo, result);
    }
}
