package com.oracle.truffle.r.library.gpu.phases;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.FixedGuardNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.Phase;

public class GPUFixedGuardRemovalPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        for (Node node : graph.getNodes()) {
            if (node instanceof FixedGuardNode) {
                node.replaceAtUsages(null);
                graph.removeFixed((FixedWithNextNode) node);

            }
        }
    }

}
