import { defineConfig } from "deepsec/config";
import { generatedMatchersPlugin } from "./generated-matchers.js";

export default defineConfig({
  defaultAgent: "codex",
  defaultModel: "gpt-5.5",
  defaultThinkingLevel: "medium",
  projects: [
    { id: "grain-reframe-workshop-template", root: ".." },
    // <deepsec:projects-insert-above>
  ],
  plugins: [generatedMatchersPlugin],
});
