---
name: nrepl-connect
description: Connect to the project's nREPL server using the nrepl MCP tool
---

# Connect to nREPL

Connect to **this sandbox's own** running nREPL using the `mcp__nrepl__connect` tool.

This is an isolated sandbox: its nREPL listens on a **port assigned at boot**, not a fixed one.
Other unrelated JVMs may be running on the host — connecting to the wrong one means you read and
modify the wrong application. So connect to the EXACT port for this sandbox and nothing else.

## Steps

1. **Use the port from your task** if it gives one (e.g. "connect the nrepl MCP to localhost port NNNNN").
2. Otherwise read the **`.nrepl-port`** file in the workspace root — that number is this sandbox's
   nREPL port.
3. Connect with `mcp__nrepl__connect` using host `localhost` and that port.
4. **Never default to 7888** (or any guessed port). 7888 is the conventional dev port and is very
   likely a *different* project's JVM on this host — connecting there is a serious mistake.
5. Verify you're on the right JVM: eval `(+ 1 1)`, then confirm the catalog matches THIS sandbox —
   `(require '[ai.obney.grain.code-agent-tools.interface :as tools]) (keys (tools/catalog))`. A clean
   starter shows only its own components; if you see unrelated ones (crm, student-ops, …) you're on
   the WRONG nREPL — reconnect to the correct port.
