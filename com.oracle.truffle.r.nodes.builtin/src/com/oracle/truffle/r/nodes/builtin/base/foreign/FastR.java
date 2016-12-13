/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base.foreign;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.library.astx.threads.RAsyncFunctionNodeGen;
import com.oracle.truffle.r.library.astx.threads.RThreadSyncNodeGen;
import com.oracle.truffle.r.library.fastr.FastRCallCountingFactory;
import com.oracle.truffle.r.library.fastr.FastRCompileNodeGen;
import com.oracle.truffle.r.library.fastr.FastRContextFactory;
import com.oracle.truffle.r.library.fastr.FastRDebugNodeGen;
import com.oracle.truffle.r.library.fastr.FastRDumpTreesNodeGen;
import com.oracle.truffle.r.library.fastr.FastRInspect;
import com.oracle.truffle.r.library.fastr.FastRPkgSourceFactory;
import com.oracle.truffle.r.library.fastr.FastRStackTraceNodeGen;
import com.oracle.truffle.r.library.fastr.FastRSyntaxTreeNodeGen;
import com.oracle.truffle.r.library.fastr.FastRThrowItFactory;
import com.oracle.truffle.r.library.fastr.FastRTraceFactory;
import com.oracle.truffle.r.library.fastr.FastRTreeNodeGen;
import com.oracle.truffle.r.library.fastr.FastRTypeofNodeGen;
import com.oracle.truffle.r.library.fastr.InteropExportNodeGen;
import com.oracle.truffle.r.library.fastr.InteropImportNodeGen;
import com.oracle.truffle.r.library.gpu.MarawaccExecuteNodeGen;
import com.oracle.truffle.r.library.gpu.MarawaccGetNodeGen;
import com.oracle.truffle.r.library.gpu.MarawaccInitilizationNodeGen;
import com.oracle.truffle.r.library.gpu.MarawaccMapBuiltin;
import com.oracle.truffle.r.library.gpu.MarawaccOCLInfoBuiltinNodeGen;
import com.oracle.truffle.r.library.gpu.MarawaccReduceBuiltin;
import com.oracle.truffle.r.library.gpu.MarawaccSapplyBuiltin;
import com.oracle.truffle.r.library.gpu.MarawaccTerminalReduceBuiltin;
import com.oracle.truffle.r.library.gpu.OCLVectorMulBuiltinNodeGen;
import com.oracle.truffle.r.library.gpu.OpenCLAvailability;
import com.oracle.truffle.r.library.gpu.OpenCLAvailabilityNodeGen;
import com.oracle.truffle.r.library.gpu.OpenCLMApply;
import com.oracle.truffle.r.library.gpu.PArrayBuiltinNodeGen;
import com.oracle.truffle.r.library.gpu.RListProbeNodeGen;
import com.oracle.truffle.r.library.gpu.TestFunctionNodeGen;
import com.oracle.truffle.r.library.gpu.intrinsics.RRandomBuiltinNodeGen;
import com.oracle.truffle.r.library.gpu.nodes.utils.RGCBuiltinNodeGen;
import com.oracle.truffle.r.library.gpu.nodes.utils.RNanoTimeBuiltinNodeGen;
import com.oracle.truffle.r.library.gpu.sequences.SequenceOfRepetitionsNodeGen;
import com.oracle.truffle.r.library.gpu.tuples.ASTxTuple2NodeGen;
import com.oracle.truffle.r.library.gpu.tuples.ASTxTuple3NodeGen;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.RBuiltinKind;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.model.RAbstractStringVector;

/**
 * This is a FastR-specific primitive that supports the extensions in the {@code fastr} package.
 */
@RBuiltin(name = ".FastR", kind = RBuiltinKind.PRIMITIVE, parameterNames = {".NAME", "..."})
public abstract class FastR extends RBuiltinNode {
    @Override
    public Object[] getDefaultParameterValues() {
        return new Object[]{RMissing.instance, RArgsValuesAndNames.EMPTY};
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "1", guards = {"cached.getDataAt(0).equals(name.getDataAt(0))", "builtin != null"})
    @TruffleBoundary
    protected Object doFastR(RAbstractStringVector name, RArgsValuesAndNames args, @Cached("name") RAbstractStringVector cached, @Cached("lookupName(name)") RExternalBuiltinNode builtin) {
        controlVisibility();
        return builtin.call(args);
    }

    @SuppressWarnings("unused")
    @Fallback
    protected Object doFastR(Object name, Object args) {
        throw RError.error(this, RError.Message.INVALID_OR_UNIMPLEMENTED_ARGUMENTS);
    }

    protected RExternalBuiltinNode lookupName(RAbstractStringVector name) {
        switch (name.getDataAt(0)) {
            case "Interop.import":
                return InteropImportNodeGen.create();
            case "Interop.export":
                return InteropExportNodeGen.create();
            case "createcc":
                return FastRCallCountingFactory.CreateCallCounterNodeGen.create();
            case "getcc":
                return FastRCallCountingFactory.GetCallCounterNodeGen.create();
            case "compile":
                return FastRCompileNodeGen.create();
            case "dumptrees":
                return FastRDumpTreesNodeGen.create();
            case "syntaxtree":
                return FastRSyntaxTreeNodeGen.create();
            case "tree":
                return FastRTreeNodeGen.create();
            case "typeof":
                return FastRTypeofNodeGen.create();
            case "stacktrace":
                return FastRStackTraceNodeGen.create();
            case "debug":
                return FastRDebugNodeGen.create();
            case "inspect":
                return new FastRInspect();
            case "pkgsource.pre":
                return FastRPkgSourceFactory.PreLoadNodeGen.create();
            case "pkgsource.post":
                return FastRPkgSourceFactory.PostLoadNodeGen.create();
            case "pkgsource.done":
                return FastRPkgSourceFactory.DoneNodeGen.create();
            case "context.get":
                return FastRContextFactory.GetNodeGen.create();
            case "context.create":
                return FastRContextFactory.CreateNodeGen.create();
            case "context.print":
                return FastRContextFactory.PrintNodeGen.create();
            case "context.spawn":
                return FastRContextFactory.SpawnNodeGen.create();
            case "context.join":
                return FastRContextFactory.JoinNodeGen.create();
            case "context.eval":
                return FastRContextFactory.EvalNodeGen.create();
            case "fastr.channel.create":
                return FastRContextFactory.CreateChannelNodeGen.create();
            case "fastr.channel.get":
                return FastRContextFactory.GetChannelNodeGen.create();
            case "fastr.channel.close":
                return FastRContextFactory.CloseChannelNodeGen.create();
            case "fastr.channel.send":
                return FastRContextFactory.ChannelSendNodeGen.create();
            case "fastr.channel.receive":
                return FastRContextFactory.ChannelReceiveNodeGen.create();
            case "fastr.throw":
                return FastRThrowItFactory.ThrowItNodeGen.create();
            case "fastr.trace":
                return FastRTraceFactory.TraceNodeGen.create();

                /*
                 * ***************************************************
                 * Marawacc builtins: GPU/CPU parallel execution
                 * ***************************************************
                 */
            case "builtin.nanotime":
                return RNanoTimeBuiltinNodeGen.create();
            case "system.gc":
                return RGCBuiltinNodeGen.create();
            case "marawacc.deviceInfo":
                return MarawaccOCLInfoBuiltinNodeGen.create();
            case "marawacc.testGPU":
                return new OpenCLMApply();
            case "marawacc.mapply":
                return new OpenCLMApply();
            case "marawacc.sapply":
                return new MarawaccSapplyBuiltin();
            case "marawacc.execute":
                return MarawaccExecuteNodeGen.create();
            case "marawacc.get":
                return MarawaccGetNodeGen.create();
            case "marawacc.map":
                return new MarawaccMapBuiltin();
            case "marawacc.reduce":
                return new MarawaccReduceBuiltin();
            case "marawacc.terminalReduce": // Blocking reduction - just for experiments? Need to
                                            // decide
                return new MarawaccTerminalReduceBuiltin();
            case "marawacc.vectorMul":
                return OCLVectorMulBuiltinNodeGen.create();
            case "marawacc.init":
                return MarawaccInitilizationNodeGen.create();
            case "builtin.random":
                return RRandomBuiltinNodeGen.create();

            case "marawacc.isOpenCL": // Check if OpenCL is enabled
                return OpenCLAvailabilityNodeGen.create();

            case "mylist":
                return RListProbeNodeGen.create();

            case "compiler.printAST":
                return TestFunctionNodeGen.create();

                /*
                 * ***************************************************
                 * Efficient index types for OpenCL
                 * ***************************************************
                 */
            case "seqOfRepetitions":
                return SequenceOfRepetitionsNodeGen.create();

                /*
                 * ***************************************************
                 * PArrays: interface for marawacc parrays
                 * ***************************************************
                 */
            case "marawacc.parray":
                return PArrayBuiltinNodeGen.create();

                /*
                 * ***************************************************
                 * ASTx thread model. It manages Java Threads from R.
                 * ***************************************************
                 */
            case "astx.async":
                return RAsyncFunctionNodeGen.create();
            case "astx.sync":
                return RThreadSyncNodeGen.create();

                /*
                 * ***************************************************
                 * ASTx Tuples library: it connects to Marawacc Tuples
                 * ***************************************************
                 */
            case "astx.tuple2":
                return ASTxTuple2NodeGen.create();
            case "astx.tuple3":
                return ASTxTuple3NodeGen.create();
            default:
                return null;
        }
    }
}
