#!/usr/bin/env bash
#
# Verifies that the imported ili2db core, the shared tests, the shared test data
# and the other flavors are still identical to upstream claeis/ili2db.
#
# Intentional divergences must be listed in tools/upstream-drift-allowlist.txt
# (one repository-relative path per line, '#' for comments) together with a
# reason in the same file.
#
# Run on a clean working tree (after ./gradlew clean) so that test artifacts do
# not show up as local modifications.
#
set -euo pipefail

UPSTREAM_REF="${UPSTREAM_REF:-upstream/master}"
ALLOWLIST="tools/upstream-drift-allowlist.txt"

if ! git rev-parse --verify --quiet "${UPSTREAM_REF}" >/dev/null; then
  echo "error: upstream ref '${UPSTREAM_REF}' not found." >&2
  echo "run: git fetch --depth=1 upstream master:refs/remotes/upstream/master" >&2
  exit 2
fi

paths=(
  src
  test/java
  test/data
  ili2pg
  ili2gpkg
  ili2mysql
  ili2h2gis
  ili2duckdb
  ili2fgdb
  ili2mssql
  ili2ora
  gui
  gui-noop
  other
  sample
  benchmark
)

changed=$(git diff --name-only "${UPSTREAM_REF}" -- "${paths[@]}" || true)

allowed=""
if [ -f "${ALLOWLIST}" ]; then
  allowed=$(sed 's/#.*//' "${ALLOWLIST}" | sed 's/[[:space:]]*$//' | sed '/^[[:space:]]*$/d' | sed 's/^[[:space:]]*//' || true)
fi

violations=""
while IFS= read -r file; do
  [ -z "${file}" ] && continue
  if [ -n "${allowed}" ] && printf '%s\n' "${allowed}" | grep -qxF "${file}"; then
    continue
  fi
  violations="${violations}${file}"$'\n'
done <<< "${changed}"

if [ -n "${violations}" ]; then
  echo "upstream drift detected (${UPSTREAM_REF}):" >&2
  printf '%s' "${violations}" >&2
  echo "" >&2
  echo "The ili2db core must stay unmodified. If a divergence is intentional," >&2
  echo "add its path to ${ALLOWLIST} with a reason." >&2
  exit 1
fi

echo "upstream drift check OK (${UPSTREAM_REF})"
