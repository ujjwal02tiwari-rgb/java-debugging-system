package com.example.debugger;

import com.sun.jdi.*;

import java.io.PrintStream;
import java.util.List;

public final class StackPrinter {
    private StackPrinter() {}

    public static void print(ThreadReference t, PrintStream out) {
        try {
            List<StackFrame> frames = t.frames();
            for (int i = 0; i < frames.size(); i++) {
                Location loc = frames.get(i).location();
                out.printf("#%d %s.%s(%s:%d)%n",
                        i,
                        loc.declaringType().name(),
                        loc.method().name(),
                        loc.sourceName(),
                        loc.lineNumber());
            }
        } catch (IncompatibleThreadStateException | AbsentInformationException e) {
            out.println("[stack unavailable: " + e + "]");
        }
    }

    public static void printLocals(ThreadReference t, PrintStream out) {
        try {
            StackFrame f = t.frame(0);
            try {
                for (LocalVariable v : f.visibleVariables()) {
                    Value val = f.getValue(v);
                    out.printf("%s %s = %s%n",
                            v.typeName(), v.name(), VariableFormatter.format(val));
                }
            } catch (AbsentInformationException e) {
                out.println("[locals unavailable: class compiled without -g:vars]");
            }
        } catch (IncompatibleThreadStateException e) {
            out.println("[thread not suspended: " + e + "]");
        }
    }
}
