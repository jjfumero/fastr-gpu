package com.oracle.truffle.r.library.gpu.phases;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.calc.ReinterpretNode;
import com.oracle.graal.phases.Phase;

public class GPUCleanPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

// ParameterNode parameterNode = graph.getParameter(1);
//
// System.out.println(GraphUtil.unproxify((parameterNode.asNode())));

        for (Node node : graph.getNodes()) {

            if (node instanceof ReinterpretNode) {
                // node.markDeleted();
            } else if (node instanceof ConstantNode) {
                // node.markDeleted();
            }

// for (Node n : node.cfgSuccessors()) {
// System.out.println("\t " + n);
// }
        }

// new DeadCodeEliminationPhase().apply(graph);
    }
}
