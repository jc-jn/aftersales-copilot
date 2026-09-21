#!/bin/sh
command -v mvn >/dev/null 2>&1 || { echo "Maven is required but was not found on PATH." >&2; exit 1; }
exec mvn "$@"
