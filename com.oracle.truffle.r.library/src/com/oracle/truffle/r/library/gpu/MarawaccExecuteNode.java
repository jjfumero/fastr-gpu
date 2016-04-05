package com.oracle.truffle.r.library.gpu;

import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.jpai.ArrayFunction;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.library.gpu.cache.MarawaccPackage;
import com.oracle.truffle.r.library.gpu.cache.RMarawaccPromises;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public abstract class MarawaccExecuteNode extends RExternalBuiltinNode.Arg1 {

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PArray executeFunction(ArrayFunction<?, ?> marawaccFunction) {
        MarawaccPackage first = RMarawaccPromises.INSTANCE.getPackage(0);
        PArray data = first.getpArray();
        PArray<?> result = marawaccFunction.apply(data);
        return result;
    }

    @SuppressWarnings("rawtypes")
    private static RAbstractVector unmarshall(PArray result, ArrayFunction<?, ?> marawaccFunction) {
        MarawaccPackage marawaccPackage = RMarawaccPromises.INSTANCE.getPackageForArrayFunction(marawaccFunction);
        TypeInfo outTypeInfo = marawaccPackage.getTypeInfo();
        return ASTxUtils.unMarshallResultFromPArrays(outTypeInfo, result);
    }

    @Specialization
    public RAbstractVector executeMarawacc(ArrayFunction<?, ?> marawaccFunction) {
        PArray<?> result = executeFunction(marawaccFunction);
        RAbstractVector rResult = unmarshall(result, marawaccFunction);
        RMarawaccPromises.INSTANCE.clean();
        return rResult;
    }
}
