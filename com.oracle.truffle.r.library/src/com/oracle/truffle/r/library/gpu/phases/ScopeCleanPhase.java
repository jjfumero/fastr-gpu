package com.oracle.truffle.r.library.gpu.phases;

import java.util.ArrayList;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.phases.Phase;

public class ScopeCleanPhase extends Phase {

    private ArrayList<Node> scopedNodes;

    public ScopeCleanPhase(ArrayList<Node> scopedNodes) {
        this.scopedNodes = scopedNodes;
    }

    private void removeNodes(StructuredGraph graph) {
        System.out.println(scopedNodes);
        for (Node node : graph.getNodes()) {

            for (Node s : scopedNodes) {
                if (s.equals(node)) {
                    if (node instanceof FixedWithNextNode) {
                        node.replaceAtUsages(null);
                        graph.removeFixed((FixedWithNextNode) node);

                    } else if (node instanceof FloatingNode) {
                        node.replaceAtUsages(null);
                        node.safeDelete();
                    }
                    break;
                }
            }
        }
    }

    @Override
    protected void run(StructuredGraph graph) {
        if (scopedNodes != null) {
            removeNodes(graph);
        }
    }
}
