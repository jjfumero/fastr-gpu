package com.oracle.truffle.r.library.gpu.phases;

import uk.ac.ed.marawacc.graal.GraalOCLBackendConnector;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.calc.ReinterpretNode;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.tiers.PhaseContext;
import com.oracle.graal.phases.util.Providers;

public class GPURemoveInterpreterPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        for (Node node : graph.getNodes()) {
            if (node instanceof ReinterpretNode) {
                node.replaceAtUsages(null);
                node.safeDelete();
            }
        }

        Providers providers = GraalOCLBackendConnector.getProviders();
        new CanonicalizerPhase().apply(graph, new PhaseContext(providers));
        new DeadCodeEliminationPhase().apply(graph);
    }
}
