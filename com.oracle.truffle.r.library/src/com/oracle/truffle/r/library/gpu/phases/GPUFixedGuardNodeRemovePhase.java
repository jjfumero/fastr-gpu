package com.oracle.truffle.r.library.gpu.phases;

import uk.ac.ed.accelerator.common.GraalAcceleratorOptions;
import uk.ac.ed.marawacc.graal.GraalOCLBackendConnector;

import com.oracle.graal.nodes.FixedGuardNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.tiers.PhaseContext;
import com.oracle.graal.phases.util.Providers;

public class GPUFixedGuardNodeRemovePhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        if (GraalAcceleratorOptions.printMessagesFromFastR) {
            System.out.println("[ASTx] GPUFixedGuardNodeRemovePhase");
        }

        for (FixedGuardNode node : graph.getNodes(FixedGuardNode.TYPE)) {
            node.replaceAtUsages(null);
            node.safeDelete();
        }

        Providers providers = GraalOCLBackendConnector.getProviders();
        new CanonicalizerPhase().apply(graph, new PhaseContext(providers));
        new DeadCodeEliminationPhase().apply(graph);
    }
}
