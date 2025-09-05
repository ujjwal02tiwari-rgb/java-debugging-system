package com.example.debugger;

import com.example.debugger.ConfigLoader.Config;
import com.example.debugger.ConfigLoader.ExceptionPolicy;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public final class DebugCLI {
    public static void main(String[] args) throws Exception {
        Map<String, String> a = parseArgs(args);
        if (a.containsKey("help") || (!a.containsKey("launch") && !a.containsKey("attach"))) {
            printUsage();
            return;
        }

        PrintWriter logWriter = null;
        if (a.containsKey("log")) {
            logWriter = new PrintWriter(new BufferedWriter(new FileWriter(a.get("log"), true)));
        }

        Debugger dbg = new Debugger(logWriter);

        // Config / breakpoints
        if (a.containsKey("bp")) {
            Config cfg = ConfigLoader.load(Path.of(a.get("bp")));
            dbg.setExceptionPolicy(cfg.pauseOnException);
            for (BreakpointSpec bp : cfg.breakpoints) dbg.addBreakpoint(bp);
        }
        if (a.containsKey("exception")) {
            dbg.setExceptionPolicy(ExceptionPolicy.valueOf(a.get("exception")));
        }

        // Launch or attach
        if (a.containsKey("launch")) {
            String mainClass = a.get("launch");
            String cp = a.getOrDefault("cp", System.getProperty("java.class.path"));
            List<String> appArgs = listFrom(a.get("appArgs"));
            List<String> vmOpts = listFrom(a.get("vmOpts"));
            dbg.launch(mainClass, cp, appArgs, vmOpts);
        } else {
            String[] hp = a.get("attach").split(":");
            dbg.attach(hp[0], hp[1]);
        }

        // Reader: script or interactive
        BufferedReader reader = a.containsKey("script")
                ? new BufferedReader(new FileReader(a.get("script")))
                : new BufferedReader(new InputStreamReader(System.in));

        if ("on".equalsIgnoreCase(a.getOrDefault("trace","off"))) {
            dbg.enableTracing(true);
        }

        try {
            dbg.startEventLoop(reader);
        } finally {
            dbg.dispose();
        }
    }

    private static List<String> listFrom(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        // Split on one or more whitespace characters.  Double backslash to escape in Java string literal.
        return Arrays.asList(csv.split("\\s+"));
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String k = args[i];
            switch (k) {
                case "--launch" -> m.put("launch", args[++i]);
                case "--attach" -> m.put("attach", args[++i]);
                case "--cp" -> m.put("cp", args[++i]);
                case "--bp" -> m.put("bp", args[++i]);
                case "--exception" -> m.put("exception", args[++i]); // none|caught|uncaught|all
                case "--script" -> m.put("script", args[++i]);
                case "--log" -> m.put("log", args[++i]);
   package com.example.debugger;

import com.example.debugger.ConfigLoader.Config;
import com.example.debugger.ConfigLoader.ExceptionPolicy;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public final class DebugCLI {
    public static void main(String[] args) throws Exception {
        Map<String, String> a = parseArgs(args);
        if (a.containsKey("help") || (!a.containsKey("launch") && !a.containsKey("attach"))) {
            printUsage();
            return;
        }

        PrintWriter logWriter = null;
        if (a.containsKey("log")) {
            logWriter = new PrintWriter(new BufferedWriter(new FileWriter(a.get("log"), true)));
        }

        Debugger dbg = new Debugger(logWriter);

        // Config / breakpoints
        if (a.containsKey("bp")) {
            Config cfg = ConfigLoader.load(Path.of(a.get("bp")));
            dbg.setExceptionPolicy(cfg.pauseOnException);
            for (BreakpointSpec bp : cfg.breakpoints) dbg.addBreakpoint(bp);
        }
        if (a.containsKey("exception")) {
            dbg.setExceptionPolicy(ExceptionPolicy.valueOf(a.get("exception")));
        }

        // Launch or attach
        if (a.containsKey("launch")) {
            String mainClass = a.get("launch");
            String cp = a.getOrDefault("cp", System.getProperty("java.class.path"));
            List<String> appArgs = listFrom(a.get("appArgs"));
            List<String> vmOpts = listFrom(a.get("vmOpts"));
            dbg.launch(mainClass, cp, appArgs, vmOpts);
        } else {
            String[] hp = a.get("attach").split(":");
            dbg.attach(hp[0], hp[1]);
        }

        // Reader: script or interactive
        BufferedReader reader = a.containsKey("script")
                ? new BufferedReader(new FileReader(a.get("script")))
                : new BufferedReader(new InputStreamReader(System.in));

        if ("on".equalsIgnoreCase(a.getOrDefault("trace","off"))) {
            dbg.enableTracing(true);
        }

        try {
            dbg.startEventLoop(reader);
        } finally {
            dbg.dispose();
        }
    }

    private static List<String> listFrom(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        // Split on one or more whitespace characters.  Double backslash to escape in Java string literal.
        return Arrays.asList(csv.split("\\s+"));
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String k = args[i];
            switch (k) {
                case "--launch" -> m.put("launch", args[++i]);
                case "--attach" -> m.put("attach", args[++i]);
                case "--cp" -> m.put("cp", args[++i]);
                case "--bp" -> m.put("bp", args[++i]);
                case "--exception" -> m.put("exception", args[++i]); // none|caught|uncaught|all
                case "--script" -> m.put("script", args[++i]);
                case "--log" -> m.put("log", args[++i]);
                case "--appArgs" -> m.put("appArgs", args[++i]);
                case "--vmOpts" -> m.put("vmOpts", args[++i]);
                case "--trace" -> m.put("trace", args[++i]);
                case "--help" -> m.put("help", "true");
                default -> { /* ignore */ }
            }
        }
        return m;
    }

    private static void printUsage() {
        System.out.println("""
        Usage:
          java -jar java-debugging-system-all.jar [--launch <MainClass> | --attach host:port]
               [--cp <classpath>] [--bp config.json] [--exception none|caught|uncaught|all]
               [--script commands.txt] [--log out.jsonl] [--trace on|off]
               [--appArgs "<args...>"] [--vmOpts "<-Xmx512m ...>"]

        Examples:
          # Launch sample and break on config breakpoints
          --launch com.example.sample.ExampleApp --bp config/breakpoints.json

          # Attach to a remote JVM
          --attach localhost:5005 --exception uncaught

          # Scripted run
          --launch com.example.sample.ExampleApp --script commands.txt --log events.jsonl
        """);
    }
}             case "--appArgs" -> m.put("appArgs", args[++i]);
                case "--vmOpts" -> m.put("vmOpts", args[++i]);
                case "--trace" -> m.put("trace", args[++i]);
                case "--help" -> m.put("help", "true");
                default -> { /* ignore */ }
            }
        }
        return m;
    }

    private static void printUsage() {
        System.out.println("""
        Usage:
          java -jar java-debugging-system-all.jar [--launch <MainClass> | --attach host:port]
               [--cp <classpath>] [--bp config.json] [--exception none|caught|uncaught|all]
               [--script commands.txt] [--log out.jsonl] [--trace on|off]
               [--appArgs "<args...>"] [--vmOpts "<-Xmx512m ...>"]

        Examples:
          # Launch sample and break on config breakpoints
          --launch com.example.sample.ExampleApp --bp config/breakpoints.json

          # Attach to a remote JVM
          --attach localhost:5005 --exception uncaught

          # Scripted run
          --launch com.example.sample.ExampleApp --script commands.txt --log events.jsonl
        """);
    }
}
