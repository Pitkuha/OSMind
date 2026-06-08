#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ -d "$SCRIPT_DIR/../scripts" ]; then
  ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
else
  ROOT=$(CDPATH= cd -- "$SCRIPT_DIR" && pwd)
fi

cd "$ROOT"
exec sh scripts/osmind-gui
