#!/usr/bin/env bash
# Isolated preflight regression test: never executes deployment or contacts a VPS.
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf -- "$fixture"' EXIT
eval "$(sed -n '/^validate_candidate_environment() {/,/^}/p; /^prepare_uploaded_release() {/,/^}/p' "$repo/app/src/main/assets/deploy.sh" | tr -d '\r')"
die() { echo "$1" >&2; exit 1; }
log_info() { :; }
EXIT_PREFLIGHT_FAILED=1
PRESERVE_CONFIG=1
CSQTT_APPLY_PANEL=1
CSQTT_CONFIG_DIR="$fixture"
CSQTT_DATABASE_FILE=csqtt.db
CSQTT_ENV_FILE="$fixture/old.env"
CSQTT_DEPLOY_OVERRIDES_FILE="$fixture/old.json"
UPLOAD_ENV_FILE="$fixture/upload.env"
UPLOAD_OVERRIDES_FILE="$fixture/upload.json"
UPLOAD_BINARY="$fixture/binary"
printf 'client-data-fixture' > "$fixture/csqtt.db"
printf '#!/bin/sh\nexit 0\n' > "$UPLOAD_BINARY"
printf 'CSQTT_WEB_USER=admin\nCSQTT_WEB_PASS=new-panel\n' > "$UPLOAD_ENV_FILE"
printf '{"main_password":"new-tunnel"}\n' > "$UPLOAD_OVERRIDES_FILE"
printf '{"main_password":"old-pending-password"}\n' > "$CSQTT_DEPLOY_OVERRIDES_FILE"
CSQTT_APPLY_TUNNEL_PASSWORD=1
prepare_uploaded_release
grep -q 'new-tunnel' "$UPLOAD_OVERRIDES_FILE"
grep -q 'new-panel' "$UPLOAD_ENV_FILE"
test "$(cat "$fixture/csqtt.db")" = client-data-fixture
# A missing explicit payload must fail rather than silently reuse an old password.
rm "$UPLOAD_OVERRIDES_FILE"
if (prepare_uploaded_release) 2>/dev/null; then exit 1; fi
# Ordinary updates discard stale uploads and preserve the stored password.
CSQTT_APPLY_TUNNEL_PASSWORD=0
printf '{"main_password":"stale-upload"}\n' > "$UPLOAD_OVERRIDES_FILE"
prepare_uploaded_release
grep -q 'old-pending-password' "$UPLOAD_OVERRIDES_FILE"
rm "$CSQTT_DEPLOY_OVERRIDES_FILE"
prepare_uploaded_release
grep -q '"main_password":""' "$UPLOAD_OVERRIDES_FILE"
test "$(cat "$fixture/csqtt.db")" = client-data-fixture
echo 'Deploy password preflight tests passed'
