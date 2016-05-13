package com.oracle.truffle.r.library.gpu.phases;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.calc.ReinterpretNode;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.Phase;

public class GPUCleanPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        System.out.println("Compilation GPU PHASE");

        for (Node node : graph.getNodes()) {
            System.out.println(">> Node: " + node);

            if (node instanceof ReinterpretNode) {
                System.out.println("ReinterpretNode NODE !!!!!!!!!!!!");

                ReinterpretNode n = (ReinterpretNode) node;
                System.out.println(n.getValue());
                GraphUtil.propagateKill(n);
            }

            for (Node n : node.cfgSuccessors()) {
                System.out.println("\t " + n);
            }
        }
    }
}
