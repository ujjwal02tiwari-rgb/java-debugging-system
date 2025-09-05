package com.example.debugger;

import java.util.Objects;

public final class BreakpointSpec {
    public enum Kind { LINE, METHOD }

    public final Kind kind;
    public final String className;
    public final Integer line;       // if LINE
    public final String methodName;  // if METHOD

    private BreakpointSpec(Kind kind, String className, Integer line, String methodName) {
        this.kind = kind;
        this.className = className;
        this.line = line;
        this.methodName = methodName;
    }

    public static BreakpointSpec line(String className, int line) {
        return new BreakpointSpec(Kind.LINE, className, line, null);
    }

    public static BreakpointSpec method(String className, String methodName) {
        return new BreakpointSpec(Kind.METHOD, className, null, methodName);
    }

    /** Accepts "pkg.Class:42" or "pkg.Class#method" */
    public static BreakpointSpec parse(String spec) {
        Objects.requireNonNull(spec, "spec");
        spec = spec.trim();
        int hash = spec.indexOf('#');
        int colon = spec.lastIndexOf(':');
        if (hash > 0) {
            String cls = spec.substring(0, hash).trim();
            String method = spec.substring(hash + 1).trim();
            if (cls.isEmpty() || method.isEmpty()) throw new IllegalArgumentException("Invalid method breakpoint: " + spec);
            return method(cls, method);
        } else if (colon > 0) {
            String cls = spec.substring(0, colon).trim();
            String lineStr = spec.substring(colon + 1).trim();
            return line(cls, Integer.parseInt(lineStr));
        }
        throw new IllegalArgumentException("Expected Class:line or Class#method, got: " + spec);
    }

    @Override public String toString() {
        return kind == Kind.LINE
                ? className + ":" + line
                : className + "#" + methodName;
    }
}
