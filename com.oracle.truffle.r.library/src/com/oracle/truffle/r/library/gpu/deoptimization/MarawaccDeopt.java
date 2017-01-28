package com.oracle.truffle.r.library.gpu.deoptimization;

import uk.ac.ed.accelerator.truffle.ASTxOptions;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccRuntimeDeoptException;
import com.oracle.truffle.r.library.gpu.utils.__LINE__;
import com.oracle.truffle.r.runtime.context.Engine.ParseException;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RDoubleSequence;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RIntSequence;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public class MarawaccDeopt {

    /**
     * Deoptimisation to LApply R function. It reconstructs the source code and launch the R
     * application in the same RContext.
     *
     * @param input
     * @param function
     * @return {@link RAbstractVector}
     */
    public static RAbstractVector deoptToLApply(RAbstractVector input, RFunction function) {

        if (ASTxOptions.debug) {
            System.out.println("DEOPTIMIZING");
        }
        StringBuffer buffer = new StringBuffer("sapply(");
        if (input instanceof RIntSequence) {
            RIntSequence ref = (RIntSequence) input;
            buffer.append(ref.getStart() + ":");
            buffer.append(ref.getEnd() + " ,");
        } else if (input instanceof RDoubleSequence) {
            RDoubleSequence ref = (RDoubleSequence) input;
            buffer.append(ref.getStart() + ":");
            buffer.append(ref.getEnd() + " ,");
        } else {
            throw new MarawaccRuntimeDeoptException("Data type for deoptimization not supported yet: " + __LINE__.print());
        }

        buffer.append(function.getRootNode().getSourceSection().getCode() + ")");
        System.out.println(buffer.toString());
        Source source = Source.fromText(buffer.toString(), "<eval>").withMimeType("application/x-r");
        try {
            return (RAbstractVector) RContext.getEngine().parseAndEval(source, false);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }
}
