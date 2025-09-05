package com.example.debugger;

import com.sun.jdi.*;

public final class VariableFormatter {
    private VariableFormatter() {}

    public static String format(Value v) {
        if (v == null) return "null";
        if (v instanceof PrimitiveValue) return v.toString();
        if (v instanceof StringReference) {
            // Surround strings with double quotes for readability
            return "\"" + ((StringReference) v).value() + "\"";
        }
        if (v instanceof ArrayReference) {
            ArrayReference a = (ArrayReference) v;
            int n = Math.min(a.length(), 10);
            StringBuilder b = new StringBuilder("Array[len=" + a.length() + "][");
            for (int i = 0; i < n; i++) {
                if (i > 0) b.append(", ");
                b.append(format(a.getValue(i)));
            }
            if (a.length() > n) b.append(", ...");
            b.append("]");
            return b.toString();
        }
        if (v instanceof ObjectReference) {
            ObjectReference o = (ObjectReference) v;
            return o.referenceType().name() + "@" + o.uniqueID();
        }
        return v.toString();
    }
}
