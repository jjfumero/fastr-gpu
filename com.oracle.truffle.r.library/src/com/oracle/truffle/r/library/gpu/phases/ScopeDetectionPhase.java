package com.oracle.truffle.r.library.gpu.phases;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.Phase;

public class ScopeDetectionPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        System.out.println("Running SCOPE!!!!");

        for (Node node : graph.getNodes()) {

            if (node instanceof ConstantNode) {
                ConstantNode constant = (ConstantNode) node;
                String valueString = constant.asJavaConstant().toValueString();
                System.out.println(node + " --> " + valueString);
                System.out.println(node + " --> " + constant.asJavaConstant().getJavaKind());
                System.out.println(node + " --> " + constant.asJavaConstant().getClass());
            }
        }
    }
}
