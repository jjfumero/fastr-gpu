package com.oracle.truffle.r.library.gpu.phases;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.calc.IntegerEqualsNode;
import com.oracle.graal.nodes.calc.ReinterpretNode;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;

public class GPUCleanPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        System.out.println("Compilation GPU PHASE");

        ParameterNode parameterNode = graph.getParameter(1);

        System.out.println(GraphUtil.unproxify((parameterNode.asNode())));

        for (Node node : graph.getNodes()) {
            System.out.println(">> Node: " + node.getClass());

            if (node instanceof ReinterpretNode) {
                System.out.println("ReinterpretNode NODE !!!!!!!!!!!!");
                // node.markDeleted();
            } else if (node instanceof ConstantNode) {
                // node.markDeleted();
            }

            for (Node n : node.cfgSuccessors()) {
                System.out.println("\t " + n);
            }
        }

        new DeadCodeEliminationPhase().apply(graph);
    }
}
