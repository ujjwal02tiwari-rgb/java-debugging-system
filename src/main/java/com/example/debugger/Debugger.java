package com.example.debugger;

import com.example.debugger.ConfigLoader.ExceptionPolicy;
import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Debugger {
    private VirtualMachine vm;
    private EventRequestManager erm;
    private volatile boolean quit = false;
    private boolean tracing = false;

    private EventSet currentEventSet = null;
    private ThreadReference currentThread = null;

    private final Map<String, List<BreakpointRequest>> lineBreakpoints = new ConcurrentHashMap<>();
    private final List<MethodEntryRequest> methodEntries = new ArrayList<>();
    private final List<BreakpointSpec> pendingBreakpoints = new ArrayList<>();
    private ExceptionPolicy exceptionPolicy = ExceptionPolicy.uncaught;

    private final PrintWriter logWriter; // may be null

    public Debugger(PrintWriter logWriter) {
        this.logWriter = logWriter;
    }

    // -------------------- Attach/Launch --------------------

    public void launch(String mainClass, String classpath, List<String> appArgs, List<String> vmOpts) throws IOException, IllegalConnectorArgumentsException, VMStartException {
        LaunchingConnector connector = findLaunchingConnector();
        Map<String, Connector.Argument> args = connector.defaultArguments();

        StringBuilder main = new StringBuilder(mainClass);
        if (appArgs != null && !appArgs.isEmpty()) {
            for (String a : appArgs) main.append(' ').append(a);
        }
        args.get("main").setValue(main.toString());

        StringBuilder opts = new StringBuilder();
        if (classpath != null && !classpath.isEmpty()) {
            opts.append("-classpath ").append(classpath).append(' ');
        }
        if (vmOpts != null) {
            for (String o : vmOpts) opts.append(o).append(' ');
        }
        // Ensure debugging friendly settings
        args.get("options").setValue(opts.toString().trim());

        this.vm = connector.launch(args);
        this.erm = vm.eventRequestManager();
        log("VMStartRequested", Map.of("main", mainClass));
    }

    public void attach(String host, String port) throws IOException, IllegalConnectorArgumentsException {
        AttachingConnector socketAttach = findSocketAttachConnector();
        Map<String, Connector.Argument> args = socketAttach.defaultArguments();
        args.get("hostname").setValue(host);
        args.get("port").setValue(port);
        this.vm = socketAttach.attach(args);
        this.erm = vm.eventRequestManager();
        log("VMAttached", Map.of("host", host, "port", port));
    }

    // -------------------- Config --------------------

    public void setExceptionPolicy(ExceptionPolicy policy) {
        this.exceptionPolicy = policy;
        // clear existing and install new
        for (ExceptionRequest r : new ArrayList<>(erm.exceptionRequests())) {
            erm.deleteEventRequest(r);
        }
        boolean caught = (policy == ExceptionPolicy.caught || policy == ExceptionPolicy.all);
        boolean uncaught = (policy == ExceptionPolicy.uncaught || policy == ExceptionPolicy.all);
        if (caught || uncaught) {
            ExceptionRequest er = erm.createExceptionRequest(null, caught, uncaught);
            er.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            er.enable();
        }
    }

    public void addBreakpoint(BreakpointSpec spec) {
        pendingBreakpoints.add(spec);
        installBreakpointIfLoaded(spec);
    }

    public void enableTracing(boolean on) {
        this.tracing = on;
    }

    // -------------------- Event Loop --------------------

    public void startEventLoop(BufferedReader commandReader) throws Exception {
        installClassPrepareForPending();
        EventQueue q = vm.eventQueue();

        while (!quit) {
            EventSet set = q.remove(); // waits
            currentEventSet = set;
            for (Event ev : set) {
                if (ev instanceof VMStartEvent) {
                    println("[VMStart] Target VM started. Type 'help' to see commands.");
                    openRepl(commandReader, "vmstart");
                } else if (ev instanceof BreakpointEvent be) {
                    currentThread = be.thread();
                    String loc = locationString(be.location());
                    println("[Breakpoint] " + loc);
                    log("BreakpointEvent", Map.of("location", loc));
                    openRepl(commandReader, "breakpoint");
                } else if (ev instanceof StepEvent se) {
                    currentThread = se.thread();
                    String loc = locationString(se.location());
                    println("[Step] " + loc);
                    log("StepEvent", Map.of("location", loc));
                    openRepl(commandReader, "step");
                } else if (ev instanceof MethodEntryEvent me) {
                    currentThread = me.thread();
                    Location loc = me.location();
                    String s = loc.declaringType().name() + "#" + loc.method().name() +
                               "(" + safeSource(loc) + ":" + loc.lineNumber() + ")";
                    println("[MethodEntry] " + s);
                    log("MethodEntryEvent", Map.of("location", s));
                    openRepl(commandReader, "method");
                } else if (ev instanceof ExceptionEvent ee) {
                    currentThread = ee.thread();
                    String s = locationString(ee.location());
                    println("[Exception] " + ee.exception().type().name() + " at " + s);
                    log("ExceptionEvent", Map.of("exception", ee.exception().type().name(), "location", s));
                    openRepl(commandReader, "exception");
                } else if (ev instanceof ClassPrepareEvent cpe) {
                    ReferenceType ref = cpe.referenceType();
                    println("[ClassPrepare] " + ref.name());
                    installPendingForClass(ref);
                } else if (ev instanceof VMDeathEvent || ev instanceof VMDisconnectEvent) {
                    println("[VMExit] Target VM exited.");
                    quit = true;
                }
            }
            // If REPL resumed already, set is resumed there; else resume to keep VM running.
            if (!quit && set.suspendPolicy() != EventRequest.SUSPEND_NONE) {
                try { set.resume(); } catch (IllegalThreadStateException ignored) {}
            }
        }
    }

    // -------------------- REPL --------------------

    private void openRepl(BufferedReader in, String reason) throws Exception {
        while (true) {
            System.out.print("dbg> ");
            String line = in.readLine();
            if (line == null) { // end of input
                dispose();
                quit = true;
                return;
            }
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+", 2);
            String cmd = parts[0];
            String arg = parts.length > 1 ? parts[1].trim() : "";

            switch (cmd) {
                case "help" -> printHelp();
                case "break" -> {
                    BreakpointSpec spec = BreakpointSpec.parse(arg);
                    addBreakpoint(spec);
                    System.out.println("Added breakpoint: " + spec);
                }
                case "list" -> listBreakpoints();
                case "run", "resume", "c" -> { resumeSet(); return; }
                case "step" -> {
                    String which = arg.isEmpty() ? "over" : arg;
                    createStep(which);
                    resumeSet();
                    return;
                }
                case "where", "stack" -> {
                    ensureThread();
                    StackPrinter.print(currentThread, System.out);
                }
                case "locals" -> {
                    ensureThread();
                    StackPrinter.printLocals(currentThread, System.out);
                }
                case "print" -> {
                    ensureThread();
                    printVar(arg);
                }
                case "threads" -> listThreads();
                case "trace" -> {
                    if ("on".equalsIgnoreCase(arg)) tracing = true;
                    else if ("off".equalsIgnoreCase(arg)) tracing = false;
                    System.out.println("trace=" + tracing);
                }
                case "quit", "exit" -> {
                    dispose();
                    quit = true;
                    return;
                }
                default -> System.out.println("Unknown command: " + cmd + " (try 'help')");
            }
        }
    }

    private void printHelp() {
        System.out.println("""
            Commands:
              help
              break <Class:line> | <Class#method>
              list
              run | resume | c
              step in|over|out
              where | stack
              locals
              print <name|this.field>
              threads
              trace on|off
              quit
            """);
    }

    private void listThreads() {
        for (ThreadReference t : vm.allThreads()) {
            System.out.printf("[%s] id=%d state=%s suspended=%s%n",
                    t.name(), t.uniqueID(), t.status(), t.isSuspended());
        }
    }

    private void listBreakpoints() {
        if (lineBreakpoints.isEmpty() && methodEntries.isEmpty()) {
            System.out.println("[no breakpoints]");
        }
        lineBreakpoints.forEach((cls, list) -> list.forEach(bp -> {
            Location loc = bp.location();
            System.out.printf("bp %s:%d%n", loc.declaringType().name(), loc.lineNumber());
        }));
        for (MethodEntryRequest m : methodEntries) {
            System.out.println("bp(method) filter=" + m);
        }
        System.out.flush();
    }

    private void printVar(String name) {
        if (name == null || name.isEmpty()) { System.out.println("Usage: print <name|this.field>"); return; }
        try {
            StackFrame f = currentThread.frame(0);
            if (name.startsWith("this.")) {
                ObjectReference thiz = f.thisObject();
                if (thiz == null) { System.out.println("[no this]"); return; }
                String fieldName = name.substring("this.".length());
                Field fld = thiz.referenceType().fieldByName(fieldName);
                if (fld == null) { System.out.println("[no such field]"); return; }
                Value val = thiz.getValue(fld);
                System.out.println(fieldName + " = " + VariableFormatter.format(val));
                return;
            }
            try {
                LocalVariable v = f.visibleVariableByName(name);
                Value val = f.getValue(v);
                System.out.println(name + " = " + VariableFormatter.format(val));
            } catch (AbsentInformationException e) {
                System.out.println("[locals unavailable: " + e.getMessage() + "]");
            }
        } catch (IncompatibleThreadStateException e) {
            System.out.println("[thread not suspended]");
        }
    }

    private void ensureThread() {
        if (currentThread == null) System.out.println("[no current thread: wait for a breakpoint/step]");
    }

    private void resumeSet() {
        if (currentEventSet != null) {
            try { currentEventSet.resume(); } catch (Exception ignored) {}
        } else {
            try { vm.resume(); } catch (Exception ignored) {}
        }
    }

    private void createStep(String which) {
        if (currentThread == null) { System.out.println("[cannot step: no suspended thread]"); return; }
        // Clear old step requests for this thread
        for (StepRequest r : new ArrayList<>(erm.stepRequests())) {
            if (r.thread().equals(currentThread)) erm.deleteEventRequest(r);
        }
        int depth = switch (which) {
            case "in", "into" -> StepRequest.STEP_INTO;
            case "out" -> StepRequest.STEP_OUT;
            default -> StepRequest.STEP_OVER;
        };
        StepRequest sr = erm.createStepRequest(currentThread, StepRequest.STEP_LINE, depth);
        sr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        sr.addCountFilter(1); // one step
        sr.enable();
    }

    // -------------------- Breakpoint installation --------------------

    private void installClassPrepareForPending() {
        if (!pendingBreakpoints.isEmpty()) {
            ClassPrepareRequest cr = erm.createClassPrepareRequest();
            // Limit noise: only classes mentioned
            for (BreakpointSpec spec : pendingBreakpoints) {
                cr.addClassFilter(spec.className);
            }
            cr.enable();
        }
    }

    private void installPendingForClass(ReferenceType ref) {
        List<BreakpointSpec> copy = new ArrayList<>(pendingBreakpoints);
        for (BreakpointSpec spec : copy) {
            if (spec.className.equals(ref.name())) {
                installBreakpointForType(spec, ref);
            }
        }
    }

    private void installBreakpointIfLoaded(BreakpointSpec spec) {
        List<ReferenceType> types = vm.classesByName(spec.className);
        if (types != null && !types.isEmpty()) {
            for (ReferenceType ref : types) installBreakpointForType(spec, ref);
        }
    }

    private void installBreakpointForType(BreakpointSpec spec, ReferenceType ref) {
        if (spec.kind == BreakpointSpec.Kind.LINE) {
            try {
                List<Location> locs = ref.locationsOfLine(spec.line);
                if (!locs.isEmpty()) {
                    Location loc = locs.get(0);
                    BreakpointRequest br = erm.createBreakpointRequest(loc);
                    br.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                    br.enable();
                    lineBreakpoints.computeIfAbsent(ref.name(), k -> new ArrayList<>()).add(br);
                    println("[breakpoint set] " + ref.name() + ":" + spec.line);
                } else {
                    println("[warn] no code at " + ref.name() + ":" + spec.line);
                }
            } catch (AbsentInformationException e) {
                println("[warn] debug info absent for " + ref.name());
            }
        } else {
            MethodEntryRequest mer = erm.createMethodEntryRequest();
            mer.addClassFilter(ref.name());
            mer.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            mer.enable();
            methodEntries.add(mer);
            println("[method-entry set] " + ref.name() + "#" + spec.methodName + " (note: class filtered; refine by method in production)");
        }
    }

    // -------------------- Helpers --------------------

    private static String locationString(Location loc) {
        String src = safeSource(loc);
        return loc.declaringType().name() + "." + loc.method().name() + "(" + src + ":" + loc.lineNumber() + ")";
    }

    private static String safeSource(Location loc) {
        try { return loc.sourceName(); } catch (AbsentInformationException e) { return "UnknownSource"; }
    }

    private static LaunchingConnector findLaunchingConnector() {
        return Bootstrap.virtualMachineManager().defaultConnector();
    }

    private static AttachingConnector findSocketAttachConnector() {
        for (AttachingConnector c : Bootstrap.virtualMachineManager().attachingConnectors()) {
            if (c.name().contains("SocketAttach")) return c;
        }
        throw new RuntimeException("SocketAttach connector not found");
    }

    private void println(String s) {
        System.out.println(s);
        if (tracing) log("trace", Map.of("msg", s));
    }

    private void log(String event, Map<String, ?> fields) {
        if (logWriter == null) return;
        try {
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("ts", Instant.now().toString());
            obj.put("event", event);
            obj.putAll(fields);
            logWriter.println(toJson(obj));
            logWriter.flush();
        } catch (Exception ignored) {}
    }

    private static String toJson(Map<String, ?> m) {
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (var e : m.entrySet()) {
            if (!first) b.append(',');
            first = false;
            b.append('"').append(e.getKey()).append('"').append(':');
            Object v = e.getValue();
            if (v == null) b.append("null");
            else if (v instanceof Number || v instanceof Boolean) b.append(v.toString());
            else b.append('"').append(escape(v.toString())).append('"');
        }
        b.append('}');
        return b.toString();
    }

    /**
     * Escapes backslashes and double quotes in a string for JSON-like output.
     *
     * @param s the input string
     * @return the escaped string with backslashes doubled and double quotes prefixed with a backslash
     */
    private static String escape(String s) {
        // First replace backslashes ("\\") with double backslashes ("\\\\").
        // Then replace double quotes ("\"") with an escaped version ("\\\"").
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void dispose() {
        try { if (vm != null) vm.dispose(); } catch (Exception ignored) {}
        if (logWriter != null) try { logWriter.close(); } catch (Exception ignored) {}
    }
}
