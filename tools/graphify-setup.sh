#!/usr/bin/env bash
# Install graphify and the Kotlin tree-sitter grammar it needs.
#
# graphify does not bundle a Kotlin grammar. Without one, every .kt and
# .kts file fails AST extraction ("Grammar not found for kotlin") and the
# graph comes out as git history plus the one .mjs test file — useless for
# an Android project. This script drops a compatible Kotlin wasm where
# graphify's resolver looks for it.
#
# The wasm from the `tree-sitter-wasms` bundle is too old (ABI 13) for the
# web-tree-sitter that graphify ships; @tree-sitter-grammars/tree-sitter-kotlin
# publishes an ABI 14 build, which is the one we use.
#
# Usage: tools/graphify-setup.sh
set -euo pipefail

GRAPHIFY_PKG="@sentropic/graphify"
KOTLIN_PKG="@tree-sitter-grammars/tree-sitter-kotlin"

if ! command -v graphify >/dev/null 2>&1; then
  echo "Installing $GRAPHIFY_PKG..."
  npm install -g "$GRAPHIFY_PKG"
fi

GRAPHIFY_ROOT="$(npm root -g)/$GRAPHIFY_PKG"
GRAMMAR_DIR="$GRAPHIFY_ROOT/node_modules/tree-sitter-kotlin"

if [ ! -f "$GRAMMAR_DIR/tree-sitter-kotlin.wasm" ]; then
  echo "Installing the Kotlin grammar into $GRAMMAR_DIR..."
  WORK="$(mktemp -d)"
  trap 'rm -rf "$WORK"' EXIT
  ( cd "$WORK" && npm pack "$KOTLIN_PKG" >/dev/null && tar xzf ./*.tgz )
  mkdir -p "$GRAMMAR_DIR"
  cp "$WORK/package/tree-sitter-kotlin.wasm" "$GRAMMAR_DIR/"
  # graphify resolves the wasm through require.resolve, so the directory
  # needs to look like a package.
  cat > "$GRAMMAR_DIR/package.json" <<'JSON'
{
  "name": "tree-sitter-kotlin",
  "version": "0.0.0-wasm",
  "main": "tree-sitter-kotlin.wasm"
}
JSON
fi

echo "Verifying the Kotlin grammar loads..."
( cd "$GRAPHIFY_ROOT" && node -e '
const TS = require("web-tree-sitter");
(async () => {
  await TS.Parser.init();
  const lang = await TS.Language.load(process.cwd() + "/node_modules/tree-sitter-kotlin/tree-sitter-kotlin.wasm");
  console.log("  Kotlin grammar OK (ABI " + lang.abiVersion + ")");
})().catch((e) => { console.error("  Kotlin grammar FAILED:", e.message); process.exit(1); });
' )

echo
echo "Done. Build the graph with:  graphify update ."
