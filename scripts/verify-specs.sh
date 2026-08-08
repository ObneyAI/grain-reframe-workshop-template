#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

if ! java -version >/dev/null 2>&1 && [ -x /opt/homebrew/opt/openjdk@21/bin/java ]; then
  PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
  export PATH
fi
if ! command -v bun >/dev/null 2>&1 && [ -x "$HOME/.bun/bin/bun" ]; then
  PATH="$HOME/.bun/bin:$PATH"
  export PATH
fi

echo "==> Development data lifecycle safety"
./scripts/verify_dev_data.sh

echo "==> Allium specifications"
spec_list=$(find . -type f -name '*.allium' \
  -not -path './.git/*' \
  -not -path './node_modules/*' \
  -not -path './target/*' | sort)

if [ -z "$spec_list" ]; then
  echo "No .allium specifications found" >&2
  exit 1
fi

echo "$spec_list" | while IFS= read -r spec; do
  echo "Checking $spec"
  allium check "$spec"
  allium analyse "$spec"
done

echo "==> Allium + Grain composition and Event Model topology"
clojure -M:dev -e '
(require (quote app.web-api.core)
         (quote [ai.obney.grain.code-agent-tools.interface :as tools])
         (quote [ai.obney.grain.event-model.interface :as event-model]))
(let [model (event-model/registered-model)
      opts {:structural-only true}
      composition (tools/validate-spec-composition
                   model {:project-root "." :event-model-opts opts})
      topology (tools/validate-event-model model opts)]
  (doseq [[label verdict] [["composition" composition] ["topology" topology]]]
    (println label (select-keys verdict [:valid? :summary]))
    (doseq [finding (:findings verdict)
            :when (= :error (:severity finding))]
      (prn finding)))
  (when-not (and (:valid? composition) (:valid? topology))
    (System/exit 1))
  (shutdown-agents))'

echo "==> Polylith tests"
clojure -M:poly test :all-bricks :dev

echo "==> Clojure lint"
clj-kondo --lint bases components development/src ui/web-app/src ui/web-app/test

echo "==> Frontend seam discipline"
if rg -n -i 'datastar|ds-ui|grain-datastar' bases components development/src ui/web-app/src; then
  echo "Removed Datastar integration found in executable source" >&2
  exit 1
fi
if rg -n 'api-client' ui/web-app/src; then
  echo "API clients must not be carried through Re-frame code; use app.api.interface" >&2
  exit 1
fi
http_users=$(rg -l 'cljs-http' ui/web-app/src || true)
if [ "$http_users" != "ui/web-app/src/app/api/core.cljs" ]; then
  echo "cljs-http must stay behind app.api.core; found: $http_users" >&2
  exit 1
fi
if rg -n '@base-ui|@shadcn/react' ui/web-app/src; then
  echo "React primitive packages must stay behind the @grain/shadcn module" >&2
  exit 1
fi

echo "==> shadcn typecheck, Re-frame tests, and production build"
bun run test:ui
bun run build

echo "==> Patch hygiene"
git diff --check

echo "All specification, topology, test, lint, and patch checks passed."
