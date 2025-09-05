package com.example.debugger;

import com.google.gson.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ConfigLoader {
    public enum ExceptionPolicy { none, caught, uncaught, all }

    public static final class Config {
        public ExceptionPolicy pauseOnException = ExceptionPolicy.uncaught;
        public List<BreakpointSpec> breakpoints = new ArrayList<>();
    }

    public static Config load(Path path) throws IOException {
        try (Reader r = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            Config cfg = new Config();
            if (root.has("pauseOnException")) {
                cfg.pauseOnException = ExceptionPolicy.valueOf(root.get("pauseOnException").getAsString());
            }
            if (root.has("breakpoints")) {
                for (JsonElement e : root.getAsJsonArray("breakpoints")) {
                    JsonObject o = e.getAsJsonObject();
                    String cls = o.get("class").getAsString();
                    if (o.has("line")) {
                        cfg.breakpoints.add(BreakpointSpec.line(cls, o.get("line").getAsInt()));
                    } else if (o.has("method")) {
                        cfg.breakpoints.add(BreakpointSpec.method(cls, o.get("method").getAsString()));
                    }
                }
            }
            return cfg;
        }
    }
}
