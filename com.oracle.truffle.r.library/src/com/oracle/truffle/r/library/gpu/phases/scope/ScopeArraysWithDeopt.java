package com.oracle.truffle.r.library.gpu.phases.scope;

import java.util.ArrayList;
import java.util.Iterator;

import jdk.vm.ci.meta.ResolvedJavaField;

import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.iterators.NodeIterable;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.java.LoadFieldNode;
import com.oracle.graal.phases.Phase;

public class ScopeArraysWithDeopt extends Phase {
    private ArrayList<Node> notValidNodes;

    public ScopeArraysWithDeopt() {
        notValidNodes = new ArrayList<>();
    }

    @Override
    protected void run(StructuredGraph graph) {

        NodeIterable<Node> nodes = graph.getNodes();
        Iterator<Node> iterator = nodes.iterator();
        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (node instanceof LoadFieldNode) {
                LoadFieldNode loadFieldNode = (LoadFieldNode) node;
                ResolvedJavaField field = loadFieldNode.field();
                String name2 = loadFieldNode.field().getName();
                System.out.println(field);
                System.out.println("\t" + name2);
                System.out.println("\tField stamp: " + field.format("%H"));

                String fieldFormat = field.format("%H");
                if (fieldFormat.equals("com.oracle.truffle.r.runtime.data.RDoubleVector")) {
                    System.out.println("SCOPE!");
                }

                String loadingType = field.format("%T");
                System.out.println("LoadingType: " + loadingType);

                // System.out.println("\t" + field.getType());
                // System.out.println("\t" + loadFieldNode.getValue().getClass());
            }
        }
    }

    public boolean isScopeDetected() {
        return !notValidNodes.isEmpty();
    }

    public ArrayList<Node> getScopedNodes() {
        return notValidNodes;
    }

}
