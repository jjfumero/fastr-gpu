package com.oracle.truffle.r.library.gpu.phases;

import uk.ac.ed.marawacc.graal.GraalOCLBackendConnector;

import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.loop.DefaultLoopPolicies;
import com.oracle.graal.loop.phases.LoopPeelingPhase;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.ConditionalEliminationPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.common.inlining.InliningPhase;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.PhaseContext;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.virtual.phases.ea.PartialEscapePhase;

public class GPUBoxingEliminationPhase extends Phase {

    private static PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite(Backend backend) {
        return backend.getSuites().getDefaultGraphBuilderSuite().copy();
    }

    /**
     * Boxing elimination phase.
     *
     * @param graph
     * @param context
     * @param providers
     */
    @SuppressWarnings("unused")
    private static void boxingRemoval(StructuredGraph graph, HighTierContext context, Providers providers) {
        new InliningPhase(new CanonicalizerPhase()).apply(graph, context);
        new PartialEscapePhase(false, new CanonicalizerPhase()).apply(graph, context);
        new CanonicalizerPhase().apply(graph, new PhaseContext(providers));
        new DeadCodeEliminationPhase().apply(graph);
    }

    /**
     * Another way of boxing elimination.
     *
     * @param graph
     * @param context
     */
    private static void boxingRemoval2(StructuredGraph graph, HighTierContext context) {
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        canonicalizer.apply(graph, context);
        new InliningPhase(new CanonicalizerPhase()).apply(graph, context);
        new LoopPeelingPhase(new DefaultLoopPolicies()).apply(graph, context);
        new DeadCodeEliminationPhase().apply(graph);
        canonicalizer.apply(graph, context);
        new PartialEscapePhase(false, canonicalizer).apply(graph, context);
        new DeadCodeEliminationPhase().apply(graph);
        canonicalizer.apply(graph, context);

        new ConditionalEliminationPhase().apply(graph);
        new DeadCodeEliminationPhase().apply(graph);
        canonicalizer.apply(graph, context);
    }

    @Override
    protected void run(StructuredGraph graph) {

        System.out.println("GPUBoxingElimination phase");

        Backend backend = GraalOCLBackendConnector.getHostBackend();
        Providers providers = backend.getProviders();
        PhaseSuite<HighTierContext> graphBuilderSuite = getDefaultGraphBuilderSuite(backend);

        HighTierContext context = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.ALL);

        boxingRemoval2(graph, context);
    }
}
