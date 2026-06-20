#!/bin/sh
# Fly.io entrypoint.
#
# The Oracle ADB wallet is never committed or baked into the image. On Fly the wallet files are
# provided as base64 **secrets**; decode them into WALLET_DIR at startup, point the wallet location
# at that dir, then run the given command. Locally / in docker-compose the wallet is bind-mounted
# (or a non-wallet DB_URL is used) and these vars are unset, so this step is skipped.
#
# Required Fly secrets when using a wallet:
#   WALLET_CWALLET_SSO_B64  = base64 of cwallet.sso
#   WALLET_TNSNAMES_B64     = base64 of tnsnames.ora
#   WALLET_SQLNET_B64       = base64 of sqlnet.ora
# Plus DB_URL (e.g. jdbc:oracle:thin:@<tns_alias>?TNS_ADMIN=/app/wallet), DB_USERNAME, DB_PASSWORD.
set -e

WALLET_DIR="${WALLET_DIR:-/app/wallet}"

if [ -n "$WALLET_CWALLET_SSO_B64" ]; then
  mkdir -p "$WALLET_DIR"
  printf '%s' "$WALLET_CWALLET_SSO_B64" | base64 -d > "$WALLET_DIR/cwallet.sso"
  printf '%s' "$WALLET_TNSNAMES_B64"    | base64 -d > "$WALLET_DIR/tnsnames.ora"
  printf '%s' "$WALLET_SQLNET_B64"      | base64 -d > "$WALLET_DIR/sqlnet.ora"
  # Force the wallet location to our dir, whatever the downloaded sqlnet.ora pointed at.
  sed -i "s#(DIRECTORY=[^)]*)#(DIRECTORY=\"$WALLET_DIR\")#" "$WALLET_DIR/sqlnet.ora" 2>/dev/null || true
  chmod 600 "$WALLET_DIR/cwallet.sso"
fi

exec "$@"
