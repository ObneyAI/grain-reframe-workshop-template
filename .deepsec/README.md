# deepsec

This directory holds the [deepsec](https://www.npmjs.com/package/deepsec)
config for the parent repo. Checked into git so teammates inherit
project context (auth shape, threat model, custom matchers); generated
scan output is gitignored.

Currently configured project: `grain-reframe-workshop-template` (target: `..`).

## Setup

`npx deepsec init --scaffold-only` created this workspace. It uses the
machine's existing ChatGPT-authenticated Codex session for local analysis;
no Vercel project or separately billed API key is required for that path.

Install the isolated workspace with `bun install`. Deepsec itself requires
Node.js 22 or newer and runs through its Node shebang; Bun remains the package
manager for this repository.

Use `--model-auth direct --ai-provider <provider>
--ai-api-key-env <ENV_NAME>` to use a user-owned model credential; secret
values remain in the environment or `.env.local`.

## Daily commands

```bash
bunx --no-install deepsec scan
bunx --no-install deepsec process --agent codex --concurrency 1
bunx --no-install deepsec revalidate --agent codex --concurrency 1
bunx --no-install deepsec export --format md-dir --out ./findings
```

`--project-id` is auto-resolved while there's only one project in
`deepsec.config.ts`. Once you've added a second project, pass
`--project-id grain-reframe-workshop-template` (or whichever id you want) explicitly.

`scan` is free (regex only). `process` is the AI stage (≈$0.30/file
on Opus by default). Run state goes to `data/grain-reframe-workshop-template/`.

## Adding another project

To scan another codebase from this same `.deepsec/`:

```bash
bunx --no-install deepsec init-project ../some-other-package
```

Appends an entry to `deepsec.config.ts` and writes
`data/<id>/{INFO.md,SETUP.md,project.json}`. Open the new SETUP.md
in your agent to fill in INFO.md.

## Layout

```
deepsec.config.ts        Project list (one entry per scanned repo)
data/grain-reframe-workshop-template/
  INFO.md                Repo context — checked into git, hand-curated
  SETUP.md               Agent setup prompt — checked in, deletable
  project.json           Generated (gitignored)
  files/                 One JSON per scanned source file (gitignored)
  runs/                  Run metadata (gitignored)
  reports/               Generated markdown reports (gitignored)
  revalidation/          Revalidation evidence (gitignored)
  tech.json              Detected technology cache (gitignored)
AGENTS.md                Pointer for coding agents
.env.local               Tokens (gitignored)
```

## Docs

After `bun install`:

- Skill: `node_modules/deepsec/SKILL.md`
- Full docs: `node_modules/deepsec/dist/docs/{getting-started,configuration,models,writing-matchers,plugins,architecture,data-layout,vercel-setup,faq}.md`

Or browse on
[GitHub](https://github.com/vercel/deepsec/tree/main/docs).
