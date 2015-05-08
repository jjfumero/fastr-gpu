/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.instrument;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.function.*;
import com.oracle.truffle.r.nodes.instrument.debug.*;
import com.oracle.truffle.r.nodes.instrument.trace.*;
import com.oracle.truffle.r.options.FastROptions;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.env.*;
import com.oracle.truffle.r.runtime.instrument.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles the initialization of the instrumentation system which sets up various instruments
 * depending on command line options.
 *
 */
public class RInstrument {

    /**
     * Collects together all the relevant data for a function, keyed by the {@link FunctionUID},
     * which is unique.
     */
    private static Map<FunctionUID, FunctionData> functionMap = new HashMap<>();

    public static class FunctionIdentification {
        public final Source source;
        public final String name;
        public final String origin;
        public final FunctionDefinitionNode node;

        FunctionIdentification(Source source, String name, String origin, FunctionDefinitionNode node) {
            this.source = source;
            this.name = name;
            this.origin = origin;
            this.node = node;
        }
    }

    private static class FunctionData {
        private final FunctionUID uid;
        private final FunctionDefinitionNode fdn;
        private ArrayList<Probe> probes = new ArrayList<>();
        private FunctionIdentification ident;

        FunctionData(FunctionUID uid, FunctionDefinitionNode fdn) {
            this.uid = uid;
            this.fdn = fdn;
        }

        private FunctionIdentification getIdentification() {
            if (ident == null) {
                SourceSection ss = fdn.getSourceSection();
                /*
                 * The default for "name" is the description associated with "fdn". If the function
                 * was parsed from text this will be the variable name the function value was
                 * assigned to, or the first 40 characters of the definition if anonymous.
                 */
                String idName = fdn.toString();
                Source idSource = null;
                String idOrigin = null;
                if (ss != null) {
                    idSource = ss.getSource();
                    String sourceName = idSource.getName();
                    idOrigin = sourceName;
                    if (sourceName.startsWith("<package:")) {
                        // try to find the name in the package environments
                        // format of sourceName is "<package"xxx deparse>"
                        String functionName = findFunctionName(uid, sourceName.substring(1, sourceName.lastIndexOf(' ')));
                        if (functionName != null) {
                            idName = functionName;
                        }
                    } else {
                        idOrigin = sourceName;
                    }
                } else {
                    /*
                     * This (pseudo) function was not parsed from source, e.g. eval(quote(x+1)). We
                     * deparse it and truncate to 40 for the name, then create a Source for it. N.B.
                     * There is no value in deparsing the funcion signature as it always of the form
                     * "function() { expr }" and only the "expr" in interesting.
                     */
                    RDeparse.State state = RDeparse.State.createPrintableState();
                    fdn.getBody().deparse(state);
                    String functionBody = state.toString();
                    idOrigin = idName;
                    idName = functionBody.substring(0, Math.min(functionBody.length(), 40)).replace("\n", "\\n");
                    idSource = Source.fromText(functionBody, "<deparsed>");
                }
                ident = new FunctionIdentification(idSource, idName, idOrigin, fdn);
            }
            return ident;

        }
    }

    /**
     * Abstracts how nodes are identified in instrumentation maps.
     */
    public static class NodeId {
        public final FunctionUID uid;
        public final int charIndex;

        NodeId(FunctionUID uid, RNode node) {
            this.uid = uid;
            SourceSection ss = node.getSourceSection();
            if (ss == null) {
                throw RInternalError.shouldNotReachHere();
            }
            this.charIndex = ss.getCharIndex();
        }

        @Override
        public int hashCode() {
            return uid.hashCode() ^ charIndex;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof NodeId) {
                NodeId otherNodeId = (NodeId) other;
                return otherNodeId.uid.equals(uid) && otherNodeId.charIndex == charIndex;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return Integer.toString(charIndex);
        }
    }

    private static class RProbeListener implements ProbeListener {

        @Override
        public void startASTProbing(Source source) {
        }

        @Override
        public void newProbeInserted(Probe probe) {
        }

        @Override
        public void probeTaggedAs(Probe probe, SyntaxTag tag, Object tagValue) {
            if (tag == RSyntaxTag.FUNCTION_BODY) {
                putProbe((FunctionUID) tagValue, probe);
                if (REntryCounters.Function.enabled()) {
                    probe.attach(new REntryCounters.Function((FunctionUID) tagValue).instrument);
                }
            } else if (tag == StandardSyntaxTag.START_METHOD) {
                putProbe((FunctionUID) tagValue, probe);
                if (FastROptions.TraceCalls.getValue()) {
                    TraceHandling.attachTraceHandler((FunctionUID) tagValue);
                }
            } else if (tag == StandardSyntaxTag.STATEMENT) {
                if (RNodeTimer.Statement.enabled()) {
                    probe.attach(new RNodeTimer.Statement((NodeId) tagValue).instrument);
                }
            }
        }

        @Override
        public void endASTProbing(Source source) {
        }

    }

    /**
     * Controls whether ASTs are instrumented after parse. The default value controlled by
     * {@link FastROptions#Instrument}.
     */
    @CompilationFinal private static boolean instrumentingEnabled;

    /**
     * The function names that were requested to be used in implicit {@code debug(f)} calls, when
     * those functions are defined.
     */
    @CompilationFinal private static String[] debugFunctionNames;

    /**
     * Called back from {@link RASTProber} so that we can record the {@link FunctionUID} and use
     * {@code fdn} as the canonical {@link FunctionDefinitionNode}.
     *
     * @param fdn
     */
    public static void registerFunctionDefinition(FunctionDefinitionNode fdn) {
        FunctionUID uid = fdn.getUID();
        FunctionData fd = functionMap.get(uid);
        assert fd == null;
        functionMap.put(uid, new FunctionData(uid, fdn));
    }

    public static FunctionIdentification getFunctionIdentification(FunctionUID uid) {
        return functionMap.get(uid).getIdentification();
    }

    private static void putProbe(FunctionUID uid, Probe probe) {
        ArrayList<Probe> list = functionMap.get(uid).probes;
        list.add(probe);
    }

    /**
     * Initialize the instrumentation system. {@link RASTProber} is registered to tag interesting
     * nodes. {@link RProbeListener} is added to (optionally) add probes to nodes tagged by
     * {@link RASTProber}.
     *
     * As a convenience we force {@link #instrumentingEnabled} on if those {@code RPerfStats}
     * features that need it are also enabled.
     */
    public static void initialize() {
        // @formatter:off
        instrumentingEnabled = FastROptions.Instrument.getValue() || FastROptions.TraceCalls.getValue() || FastROptions.Rdebug.getValue() != null ||
                        REntryCounters.Function.enabled() || RNodeTimer.Statement.enabled();
        // @formatter:on
        if (instrumentingEnabled) {
            Probe.registerASTProber(RASTProber.getRASTProber());
            Probe.addProbeListener(new RProbeListener());
            PackageSource.initialize();
        }
        String rdebugValue = FastROptions.Rdebug.getValue();
        if (rdebugValue != null) {
            debugFunctionNames = rdebugValue.split(",");
        }
    }

    public static boolean instrumentingEnabled() {
        return instrumentingEnabled;
    }

    public static void checkDebugRequested(String name, RFunction func) {
        if (debugFunctionNames != null) {
            for (String debugFunctionName : debugFunctionNames) {
                if (debugFunctionName.equals(name)) {
                    DebugHandling.enableDebug(func, "", RNull.instance, false);
                }
            }
        }
    }

    /**
     * Returns the {@link Probe} with the given tag for the given function, or {@code null} if not
     * found.
     */
    public static Probe findSingleProbe(FunctionUID uid, SyntaxTag tag) {
        if (!instrumentingEnabled) {
            return null;
        }
        ArrayList<Probe> list = functionMap.get(uid).probes;
        if (list != null) {
            for (Probe probe : list) {
                if (probe.isTaggedAs(tag)) {
                    return probe;
                }
            }
        }
        return null;
    }

    private static Map<FunctionUID, String> functionNameMap;

    /**
     * Attempts to locate a name for an (assumed) builtin or global function. Returns {@code null}
     * if not found.
     */
    private static String findFunctionName(FunctionUID uid, String packageName) {
        if (functionNameMap == null) {
            functionNameMap = new HashMap<>();
        }
        String name = functionNameMap.get(uid);
        if (name == null) {
            name = findFunctionInPackage(uid, packageName);
        }
        return name;
    }

    /**
     * Try to find the function identified by uid in the given package. N.B. If we have the uid, the
     * promise identifying the lazily loaded function must have been evaluated! So there is no need
     * to evaluate any promises. N.B. For packages, we must use the namespace env as that contains
     * public and private functions.
     */
    private static String findFunctionInPackage(FunctionUID uid, String packageName) {
        if (packageName == null) {
            return findFunctionInEnv(uid, REnvironment.globalEnv());
        }
        REnvironment env = REnvironment.lookupOnSearchPath(packageName);
        env = env.getPackageNamespaceEnv();
        return findFunctionInEnv(uid, env);
    }

    private static String findFunctionInEnv(FunctionUID uid, REnvironment env) {
        // This is rather inefficient, but it doesn't matter
        RStringVector names = env.ls(true, null, false);
        for (int i = 0; i < names.getLength(); i++) {
            String name = names.getDataAt(i);
            Object val = env.get(name);
            if (val instanceof RPromise) {
                RPromise prVal = (RPromise) val;
                if (prVal.isEvaluated()) {
                    val = prVal.getValue();
                } else {
                    continue;
                }
            }
            if (val instanceof RFunction) {
                RFunction func = (RFunction) val;
                RootNode rootNode = func.getRootNode();
                if (rootNode instanceof FunctionDefinitionNode) {
                    FunctionDefinitionNode fdn = (FunctionDefinitionNode) rootNode;
                    if (fdn.getUID().equals(uid)) {
                        functionNameMap.put(fdn.getUID(), name);
                        return name;
                    }
                }
            }
        }
        // Most likely a nested function, which is ok
        // because they are not lazy and so have names from the parser.
        return null;
    }
}
