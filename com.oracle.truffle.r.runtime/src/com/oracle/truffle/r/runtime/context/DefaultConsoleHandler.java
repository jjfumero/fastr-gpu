/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;

public class DefaultConsoleHandler implements ConsoleHandler {

    private final BufferedReader in;
    private final PrintStream out;
    private String prompt;

    public DefaultConsoleHandler(InputStream in, OutputStream out) {
        this.in = new BufferedReader(new InputStreamReader(in));
        this.out = new PrintStream(out);
    }

    public void println(String s) {
        out.println(s);
    }

    public void print(String s) {
        out.print(s);
    }

    public void printErrorln(String s) {
        out.println(s);
    }

    public void printError(String s) {
        out.print(s);
    }

    public String readLine() {
        try {
            if (prompt != null) {
                out.print(prompt);
            }
            return in.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isInteractive() {
        return true;
    }

    public void redirectError() {
        // ?
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public int getWidth() {
        return 80;
    }

    public String getInputDescription() {
        return "<PolyglotEngine env input>";
    }
}
