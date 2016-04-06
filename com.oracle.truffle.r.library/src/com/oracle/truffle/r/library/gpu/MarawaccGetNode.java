package com.oracle.truffle.r.library.gpu;

import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.jpai.ArrayFunction;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.library.gpu.cache.MarawaccPackage;
import com.oracle.truffle.r.library.gpu.cache.RMarawaccFutures;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public abstract class MarawaccGetNode extends RExternalBuiltinNode.Arg1 {

    @SuppressWarnings({"rawtypes"})
    private static PArray executeFunction(ArrayFunction<?, ?> marawaccFunction) {
        PArray pArray = RMarawaccFutures.INSTANCE.getPArray(marawaccFunction);
        return pArray;
    }

    @SuppressWarnings("rawtypes")
    private static RAbstractVector unmarshall(PArray result, ArrayFunction<?, ?> marawaccFunction) {
        MarawaccPackage marawaccPackage = null;
        marawaccPackage = RMarawaccFutures.INSTANCE.getPackageForArrayFunction(marawaccFunction);
        TypeInfo outTypeInfo = marawaccPackage.getTypeInfo();
        return ASTxUtils.unMarshallResultFromPArrays(outTypeInfo, result);
    }

    @Specialization
    public RAbstractVector executeMarawacc(ArrayFunction<?, ?> marawaccFunction) {
        PArray<?> result = executeFunction(marawaccFunction);
        RAbstractVector rResult = unmarshall(result, marawaccFunction);
        RMarawaccFutures.INSTANCE.clean();
        return rResult;
    }
}
