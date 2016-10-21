/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.library.gpu.phases.scope;

import java.util.ArrayList;

import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCICompiler;
import uk.ac.ed.datastructures.common.PArray;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.api.runtime.GraalJVMCICompiler;
import com.oracle.graal.api.runtime.GraalRuntime;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodePosIterator;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.java.LoadIndexedNode;
import com.oracle.graal.phases.Phase;

/**
 * The array constant node "always" (based on experimentation) is the input for
 * {@link LoadIndexedNode}. One way of detecting is to analyse the inputs for LoadIndexNode. If it
 * receives a @{@link ConstantNode} with its value as an array of primitive data types, we can
 * handle it in the code generation for OpenCL and manage it as {@link PArray}.
 *
 */
public class ScopeDetectionPhase extends Phase {

    private ArrayList<Object> rawData;
    private ArrayList<ConstantNode> arrayConstantNodes;
    private GraalRuntime graal;
    private SnippetReflectionProvider reflectionProvider;

    public ScopeDetectionPhase() {
        initializeRuntime();
        initSnippetReflection();
    }

    @Override
    protected void run(StructuredGraph graph) {
        if (rawData == null) {
            rawData = new ArrayList<>();
        }
        if (arrayConstantNodes == null) {
            arrayConstantNodes = new ArrayList<>();
        }
        checkLoadIndexedNodes(graph);
    }

    public Object[] getDataArray() {
        return rawData.toArray();
    }

    public ArrayList<ConstantNode> getConstantNodes() {
        return arrayConstantNodes;
    }

    // Note: this part depends totally on Graal. Separate the VM
    private GraalRuntime initializeRuntime() {
        JVMCICompiler compiler = JVMCI.getRuntime().getCompiler();
        if (compiler instanceof GraalJVMCICompiler) {
            GraalJVMCICompiler graalCompiler = (GraalJVMCICompiler) compiler;
            graal = graalCompiler.getGraalRuntime();
            return graalCompiler.getGraalRuntime();
        } else {
            return null;
        }
    }

    private void initSnippetReflection() {
        reflectionProvider = graal.getCapability(SnippetReflectionProvider.class);
    }

    /**
     * In R we currently support 3 main data types: double[], int[] and boolean[]
     *
     * @param value
     */
    private void getArrayConstantReference(JavaConstant value) {
        String valueString = value.toValueString();
        if (valueString.startsWith("double[")) {
            double[] asObject = reflectionProvider.asObject(double[].class, value);
            rawData.add(asObject);
        } else if (valueString.startsWith("int[")) {
            int[] asObject = reflectionProvider.asObject(int[].class, value);
            rawData.add(asObject);
        } else if (valueString.startsWith("boolean[")) {
            boolean[] asObject = reflectionProvider.asObject(boolean[].class, value);
            rawData.add(asObject);
        }
    }

    private void analyseConstant(ConstantNode node) {
        JavaConstant value = node.asJavaConstant();
        if (value instanceof HotSpotObjectConstant) {
            if (node.getStackKind() == JavaKind.Object) {
                getArrayConstantReference(value);
                arrayConstantNodes.add(node);
            }
        }
    }

    private void iterateLoadIndexInputs(NodePosIterator iterator) {
        while (iterator.hasNext()) {
            Node scopeNode = iterator.next();
            if (scopeNode instanceof ConstantNode) {
                if (!arrayConstantNodes.contains(scopeNode)) {
                    analyseConstant((ConstantNode) scopeNode);
                }
            }
        }
    }

    private void checkLoadIndexedNodes(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (node instanceof LoadIndexedNode) {
                LoadIndexedNode loadIndexed = (LoadIndexedNode) node;
                NodePosIterator iterator = loadIndexed.inputs().iterator();
                iterateLoadIndexInputs(iterator);
            }
        }
    }
}
