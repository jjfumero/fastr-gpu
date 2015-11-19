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
package com.oracle.truffle.r.library.fastr;

import java.io.*;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.vm.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RCmdOptions.Client;
import com.oracle.truffle.r.runtime.conn.*;
import com.oracle.truffle.r.runtime.context.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.nodes.*;

public class FastRContext {

    public abstract static class Create extends RExternalBuiltinNode.Arg2 {
        @Specialization
        @TruffleBoundary
        protected int create(RAbstractStringVector args, RAbstractIntVector kindVec) {
            RContext.ContextKind kind = RContext.ContextKind.VALUES[kindVec.getDataAt(0) - 1];
            RCmdOptions options = RCmdOptions.parseArguments(Client.RSCRIPT, args.materialize().getDataCopy());
            return ContextInfo.createDeferred(options, kind, RContext.getInstance(), RContext.getInstance().getConsoleHandler());
        }
    }

    public abstract static class Get extends RExternalBuiltinNode.Arg0 {
        @Specialization
        @TruffleBoundary
        protected Object get() {
            return RContext.getInstance();
        }
    }

    public abstract static class Print extends RExternalBuiltinNode.Arg1 {
        @Specialization
        @TruffleBoundary
        protected RNull print(RAbstractIntVector ctxt) {
            if (ctxt.getLength() != 1) {
                throw RError.error(this, RError.Message.INVALID_ARGUMENT, "context");
            }
            int contextId = ctxt.getDataAt(0);
            ContextInfo info = ContextInfo.get(contextId);
            try {
                if (info == null) {
                    StdConnections.getStdout().writeString("obsolete context: " + contextId, true);
                } else {
                    StdConnections.getStdout().writeString("context: " + contextId, true);
                }
                return RNull.instance;
            } catch (IOException ex) {
                throw RError.error(this, RError.Message.GENERIC, ex.getMessage());
            }
        }
    }

    public abstract static class Spawn extends RExternalBuiltinNode.Arg2 {
        @Specialization
        @TruffleBoundary
        protected RNull eval(RAbstractIntVector contexts, RAbstractStringVector exprs) {
            RContext.EvalThread[] threads = new RContext.EvalThread[contexts.getLength()];
            for (int i = 0; i < threads.length; i++) {
                ContextInfo info = checkContext(contexts.getDataAt(i), this);
                threads[i] = new RContext.EvalThread(info, Source.fromText(exprs.getDataAt(i % threads.length), "<context_eval>").withMimeType(RRuntime.R_APP_MIME));
            }
            for (int i = 0; i < threads.length; i++) {
                threads[i].start();
            }
            return RNull.instance;
        }
    }

    public abstract static class Join extends RExternalBuiltinNode.Arg1 {
        @Specialization
        protected RNull eval(RAbstractIntVector contexts) {
            try {
                for (int i = 0; i < contexts.getLength(); i++) {
                    Thread thread = RContext.EvalThread.threads.get(contexts.getDataAt(i));
                    if (thread == null) {
                        // already done
                        continue;
                    } else {
                        thread.join();
                    }
                }
            } catch (InterruptedException ex) {
                throw RError.error(this, RError.Message.GENERIC, "error finishing eval thread");

            }
            return RNull.instance;
        }
    }

    public abstract static class Eval extends RExternalBuiltinNode.Arg3 {
        @Specialization
        @TruffleBoundary
        protected Object eval(RAbstractIntVector contexts, RAbstractStringVector exprs, byte par) {
            Object[] results = new Object[contexts.getLength()];
            if (RRuntime.fromLogical(par)) {
                RContext.EvalThread[] threads = new RContext.EvalThread[contexts.getLength()];
                for (int i = 0; i < threads.length; i++) {
                    ContextInfo info = checkContext(contexts.getDataAt(i), this);
                    threads[i] = new RContext.EvalThread(info, Source.fromText(exprs.getDataAt(i % threads.length), "<context_eval>").withMimeType(RRuntime.R_APP_MIME));
                }
                for (int i = 0; i < threads.length; i++) {
                    threads[i].start();
                }
                try {
                    for (int i = 0; i < threads.length; i++) {
                        threads[i].join();
                        results[i] = threads[i].getReturnValue();
                    }
                } catch (InterruptedException ex) {
                    throw RError.error(this, RError.Message.GENERIC, "error finishing eval thread");
                }
            } else {
                for (int i = 0; i < contexts.getLength(); i++) {
                    ContextInfo info = checkContext(contexts.getDataAt(i), this);
                    PolyglotEngine vm = info.apply(PolyglotEngine.newBuilder()).build();
                    try {
                        Source source = Source.fromText(exprs.getDataAt(i % exprs.getLength()), "<eval>").withMimeType(RRuntime.R_APP_MIME);
                        PolyglotEngine.Value resultValue = vm.eval(source);
                        Object result = resultValue.get();
                        if (result == null) {
                            // this means an error occurred and there is no result
                            results[i] = RRuntime.LOGICAL_NA;
                        } else if (result instanceof TruffleObject) {
                            Object returnValue = resultValue.as(Object.class);
                            results[i] = returnValue;
                        } else {
                            results[i] = result;
                        }
                    } catch (IOException e) {
                        throw RInternalError.shouldNotReachHere(e);
                    } finally {
                        vm.dispose();
                    }
                }
            }
            if (results.length == 1) {
                return results[0];
            } else {
                return RDataFactory.createList(results);
            }
        }
    }

    private static ContextInfo checkContext(int contextId, RBaseNode invokingNode) throws RError {
        ContextInfo info = ContextInfo.get(contextId);
        if (info == null) {
            throw RError.error(invokingNode, RError.Message.GENERIC, "no context: " + contextId);
        } else {
            return info;
        }
    }

    private static int wrongChannelArg(RBaseNode baseNode, Object arg, String argName) {
        if (!(arg instanceof RAbstractIntVector)) {
            throw RError.error(baseNode, RError.Message.INVALID_ARG_TYPE);
        } else {
            // guard failed
            throw RError.error(baseNode, RError.Message.WRONG_LENGTH_ARG, argName);
        }
    }

    public abstract static class CreateChannel extends RExternalBuiltinNode.Arg1 {
        @Specialization(guards = "key.getLength() == 1")
        @TruffleBoundary
        protected int createChannel(RAbstractIntVector key) {
            return RChannel.createChannel(key.getDataAt(0));
        }

        @Fallback
        protected int error(Object key) {
            return wrongChannelArg(this, key, "key");
        }
    }

    public abstract static class GetChannel extends RExternalBuiltinNode.Arg1 {
        @Specialization(guards = "key.getLength() == 1")
        @TruffleBoundary
        protected int getChannel(RAbstractIntVector key) {
            return RChannel.getChannel(key.getDataAt(0));
        }

        @Fallback
        protected int error(Object key) {
            return wrongChannelArg(this, key, "key");
        }
    }

    public abstract static class CloseChannel extends RExternalBuiltinNode.Arg1 {
        @Specialization(guards = "id.getLength() == 1")
        @TruffleBoundary
        protected RNull getChannel(RAbstractIntVector id) {
            RChannel.closeChannel(id.getDataAt(0));
            return RNull.instance;
        }

        @Fallback
        protected int error(Object id) {
            return wrongChannelArg(this, id, "id");
        }
    }

    public abstract static class ChannelSend extends RExternalBuiltinNode.Arg2 {
        @Specialization(guards = "id.getLength() == 1")
        @TruffleBoundary
        protected RNull send(RAbstractIntVector id, Object data) {
            RChannel.send(id.getDataAt(0), data);
            return RNull.instance;
        }

        @Fallback
        protected int error(Object id, @SuppressWarnings("unused") Object data) {
            return wrongChannelArg(this, id, "id");
        }
    }

    public abstract static class ChannelReceive extends RExternalBuiltinNode.Arg1 {
        @Specialization(guards = "id.getLength() == 1")
        @TruffleBoundary
        protected Object receive(RAbstractIntVector id) {
            return RChannel.receive(id.getDataAt(0));
        }

        @Fallback
        protected int error(Object id) {
            return wrongChannelArg(this, id, "id");
        }
    }
}
