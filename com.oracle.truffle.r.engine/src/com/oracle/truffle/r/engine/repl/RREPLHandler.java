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
package com.oracle.truffle.r.engine.repl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrument.KillException;
import com.oracle.truffle.api.instrument.QuitException;
import com.oracle.truffle.api.instrument.Visualizer;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.r.engine.TruffleRLanguage;
import com.oracle.truffle.r.runtime.AnonymousFrameVariable;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.tools.debug.shell.REPLMessage;
import com.oracle.truffle.tools.debug.shell.client.SimpleREPLClient;
import com.oracle.truffle.tools.debug.shell.server.FrameDebugDescription;
import com.oracle.truffle.tools.debug.shell.server.REPLHandler;
import com.oracle.truffle.tools.debug.shell.server.REPLServerContext;

/**
 * Instantiation of the "server handler" part of the "REPL*" debugger for R.
 * <p>
 * These handlers implement debugging commands that require language-specific support.
 *
 * @see SimpleREPLClient
 */
public abstract class RREPLHandler extends REPLHandler {

    public RREPLHandler(String op) {
        super(op);
    }

    public static final RREPLHandler EVAL_HANDLER = new RREPLHandler(REPLMessage.EVAL) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServerContext serverContext) {
            final String sourceName = request.get(REPLMessage.SOURCE_NAME);
            final REPLMessage message = new REPLMessage(REPLMessage.OP, REPLMessage.EVAL);
            message.put(REPLMessage.SOURCE_NAME, sourceName);
            message.put(REPLMessage.DEBUG_LEVEL, Integer.toString(serverContext.getLevel()));
            final Visualizer visualizer = serverContext.getVisualizer();

            Source source = Source.fromText(request.get(REPLMessage.CODE), "<REPL>").withMimeType(TruffleRLanguage.MIME);
            MaterializedFrame frame = null;
            final Integer frameNumber = request.getIntValue(REPLMessage.FRAME_NUMBER);
            if (frameNumber != null) {
                final List<FrameDebugDescription> stack = serverContext.getStack();
                if (frameNumber < 0 || frameNumber >= stack.size()) {
                    return finishReplyFailed(message, "invalid frame number");
                }
                final FrameDebugDescription frameDescription = stack.get(frameNumber);
                frame = (MaterializedFrame) frameDescription.frameInstance().getFrame(FrameAccess.MATERIALIZE, true);
            }
            try {
                Object returnValue = TruffleRLanguage.INSTANCE.internalEvalInContext(source, null, frame);
                return finishReplySucceeded(message, visualizer.displayValue(returnValue, 0));
            } catch (KillException ex) {
                return finishReplySucceeded(message, "eval (" + sourceName + ") killed");
            } catch (Exception ex) {
                return finishReplyFailed(message, ex.toString());
            }
        }
    };

    public static final RREPLHandler INFO_HANDLER = new RREPLHandler(REPLMessage.INFO) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServerContext serverContext) {
            final String topic = request.get(REPLMessage.TOPIC);

            if (topic == null || topic.isEmpty()) {
                final REPLMessage message = new REPLMessage(REPLMessage.OP, REPLMessage.INFO);
                return finishReplyFailed(message, "No info topic specified");
            }

            switch (topic) {
                case REPLMessage.LANGUAGE:
                    return createLanguageInfoReply();

                default:
                    final REPLMessage message = new REPLMessage(REPLMessage.OP, REPLMessage.INFO);
                    return finishReplyFailed(message, "No info about topic \"" + topic + "\"");
            }
        }
    };

    private static REPLMessage[] createLanguageInfoReply() {
        final ArrayList<REPLMessage> langMessages = new ArrayList<>();

        return langMessages.toArray(new REPLMessage[0]);
    }

    /**
     * Returns a general description of the frame, plus a textual summary of the slot values: one
     * per line. Custom version for FastR that does not show anonymous frame slots.
     */
    public static final REPLHandler R_FRAME_HANDLER = new REPLHandler(REPLMessage.FRAME) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServerContext serverContext) {
            final REPLMessage reply = createReply();
            final Integer frameNumber = request.getIntValue(REPLMessage.FRAME_NUMBER);
            if (frameNumber == null) {
                return finishReplyFailed(reply, "no frame number specified");
            }
            final List<FrameDebugDescription> stack = serverContext.getStack();
            if (frameNumber < 0 || frameNumber >= stack.size()) {
                return finishReplyFailed(reply, "frame number " + frameNumber + " out of range");
            }
            final FrameDebugDescription frameDescription = stack.get(frameNumber);
            final REPLMessage frameMessage = createFrameInfoMessage(serverContext, frameDescription);
            final Frame frame = RArguments.unwrap(frameDescription.frameInstance().getFrame(FrameInstance.FrameAccess.READ_ONLY, true));
            final FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
            Visualizer visualizer = serverContext.getVisualizer();
            try {
                final StringBuilder sb = new StringBuilder();
                for (FrameSlot slot : frameDescriptor.getSlots()) {
                    if (slot.getIdentifier() instanceof String) {
                        String slotName = slot.getIdentifier().toString();
                        if (!AnonymousFrameVariable.isAnonymous(slotName)) {
                            sb.append(Integer.toString(slot.getIndex()) + ": " + visualizer.displayIdentifier(slot) + " = ");
                            try {
                                final Object value = frame.getValue(slot);
                                sb.append(visualizer.displayValue(value, 0));
                            } catch (Exception ex) {
                                sb.append("???");
                            }
                            sb.append("\n");
                        }
                    }
                }
                return finishReplySucceeded(frameMessage, sb.toString());
            } catch (Exception ex) {
                return finishReplyFailed(frameMessage, ex.toString());
            }
        }
    };

    public static final RREPLHandler LOAD_RUN_FILE_HANDLER = new RREPLHandler(REPLMessage.LOAD_RUN) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServerContext serverContext) {
            final REPLMessage reply = new REPLMessage(REPLMessage.OP, REPLMessage.LOAD_RUN);
            final REPLMessage message = new REPLMessage(REPLMessage.OP, REPLMessage.LOAD_RUN);
            final String fileName = request.get(REPLMessage.SOURCE_NAME);

            try {
                final File file = new File(fileName);
                if (!file.canRead()) {
                    return finishReplyFailed(reply, "can't find file \"" + fileName + "\"");
                }
                final PolyglotEngine vm = serverContext.engine();
                Source source = Source.fromFileName(fileName, true).withMimeType(TruffleRLanguage.MIME);
                vm.eval(source);
                final String path = file.getCanonicalPath();
                message.put(REPLMessage.FILE_PATH, path);
                return finishReplySucceeded(message, fileName + "  exited");
            } catch (IOException ex) {
                throw (QuitException) ex.getCause();
            } catch (KillException ex) {
                return finishReplySucceeded(message, fileName + " killed");
            } catch (Exception ex) {
                return finishReplyFailed(message, "error loading file \"" + fileName + "\": " + ex.getMessage());
            }
        }
    };

}
