# java-debugging-system

A minimal yet practical Java CLI debugger built with the Java Debug Interface (JDI). Perfect for learning JVM debugging internals and showcasing systems design on GitHub.

## Features
- Launch or attach to JVMs.
- Breakpoints by `Class:line` or `Class#method` (method entry).
- Step in/over/out; show stack, threads, locals; print variables/fields.
- Pause on exceptions (caught/uncaught/all).
- Structured JSONL logs for events.
- Script mode for automated workflows and tests.

## Architecture Diagrams

### Flowchart (core lifecycle)
```mermaid
flowchart TD
    A[Start CLI] --> B{Launch or Attach?}
    B -->|Launch| C[Start Target VM via JDI LaunchingConnector]
    B -->|Attach| D[Attach to JVM via SocketAttach]
    C --> E[Install Requests: Breakpoints, Exceptions, MethodEntry]
    D --> E
    E --> F[Enter Event Loop]
    F --> G{Event?}
    G -->|VMStart| H[Offer REPL before run]
    G -->|Breakpoint/Step/Exception/MethodEntry| I[Mark SUSPENDED; Open REPL]
    G -->|VMDeath/Disconnect| Z[Cleanup & Exit]
    I --> J[REPL: list/add bps, locals, stack, step, resume, quit]
    J -->|resume/step| F
```

### Sequence (user–debugger–target JVM)
```mermaid
sequenceDiagram
  participant User
  participant CLI as DebugCLI
  participant D as Debugger
  participant VM as Target JVM

  User->>CLI: dbg --launch com.example.sample.ExampleApp --bp config/breakpoints.json
  CLI->>D: init + load config
  D->>VM: Launch (suspend=y)
  VM-->>D: VMStartEvent
  D->>User: REPL (set breakpoints then 'run')
  User->>D: run
  D->>VM: resume()
  VM-->>D: BreakpointEvent
  D->>User: show location + locals
  User->>D: step over
  D->>VM: StepRequest + resume()
  VM-->>D: StepEvent
  D->>User: show new line
  User->>D: quit
  D->>VM: dispose()
```

## Quick Start (Local)
```bash
# build & run tests
./scripts/build.sh

# run debugger against the sample app with config breakpoints
./scripts/run_debugger.sh
```

Or manually:
```bash
./gradlew clean test shadowJar
java -jar build/libs/java-debugging-system-all.jar   --launch com.example.sample.ExampleApp   --bp config/breakpoints.json
```

## Quick Start (Docker)
```bash
docker build -t java-debugging-system .
# Building the image runs all tests in the build stage.
# To run the packaged app:
docker run --rm -it java-debugging-system
```

> **Note:** The runtime image uses a JDK (not a JRE) so JDI classes are available at runtime.

## CLI Commands (REPL)
- `help` — show commands
- `break <Class:line>` — line breakpoint
- `break <Class#method>` — break on method entry (class-filtered)
- `list` — list breakpoints
- `run` / `resume` / `c` — continue
- `step in|over|out` — single step
- `where` / `stack` — show stack
- `locals` — current frame locals
- `print <name|this.field>` — inspect a variable or field
- `threads` — list threads
- `trace on|off` — toggle event tracing to log/console
- `quit` — detach/exit

## Config
See `config/breakpoints.json`.

## Author
**Ujjwal <ujjwal02tiwari@gmail.com >**

## License
MIT
