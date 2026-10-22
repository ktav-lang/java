#!/usr/bin/env bash
# Thin wrapper — all logic lives in scripts/check_publish_metadata.py.
set -euo pipefail
exec python3 scripts/check_publish_metadata.py "$@"
