#!/bin/bash
# fetch-secrets.sh — Conjur secret fetcher for init container
#
# Authenticates to Conjur, fetches secrets defined in CONJUR_SECRETS,
# and writes them to /conjur/secrets/ as individual files.
#
# Required env vars:
#   CONJUR_APPLIANCE_URL  — Conjur server URL
#   CONJUR_ACCOUNT        — Conjur account name
#   CONJUR_AUTHN_LOGIN    — authentication identity (e.g., host/apps/myapp)
#   CONJUR_AUTHN_API_KEY  — API key
#   CONJUR_SECRETS        — comma-separated key=path mappings
#                           e.g., db.username=db/empdb-uid,db.password=db/empdb-pwd
#
# Output: /conjur/secrets/{configKey} files + /conjur/secrets/.env

set -euo pipefail

SECRET_DIR="${CONJUR_SECRET_DIR:-/conjur/secrets}"
mkdir -p "$SECRET_DIR"

# Validate required env vars
for var in CONJUR_APPLIANCE_URL CONJUR_ACCOUNT CONJUR_AUTHN_LOGIN CONJUR_AUTHN_API_KEY CONJUR_SECRETS; do
    if [ -z "${!var:-}" ]; then
        echo "ERROR: $var is not set" >&2
        exit 1
    fi
done

echo "Conjur secrets init: authenticating as '${CONJUR_AUTHN_LOGIN}'..."

# URL-encode the login identity
ENCODED_LOGIN=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${CONJUR_AUTHN_LOGIN}', safe=''))" 2>/dev/null || \
    echo "${CONJUR_AUTHN_LOGIN}" | sed 's|/|%2F|g')

# Authenticate to Conjur
TOKEN_RAW=$(curl -s --fail \
    --connect-timeout 10 \
    --max-time 15 \
    -X POST \
    -d "${CONJUR_AUTHN_API_KEY}" \
    "${CONJUR_APPLIANCE_URL}/authn/${CONJUR_ACCOUNT}/${ENCODED_LOGIN}/authenticate")

if [ -z "$TOKEN_RAW" ]; then
    echo "ERROR: Authentication failed — empty response" >&2
    exit 1
fi

TOKEN=$(echo -n "$TOKEN_RAW" | base64 -w0)
echo "Authenticated successfully."

# Parse CONJUR_SECRETS and fetch each secret
FETCHED=0
FAILED=0
ENV_FILE="$SECRET_DIR/.env"
> "$ENV_FILE"

IFS=',' read -ra PAIRS <<< "$CONJUR_SECRETS"
for pair in "${PAIRS[@]}"; do
    pair=$(echo "$pair" | xargs)  # trim whitespace
    CONFIG_KEY="${pair%%=*}"
    CONJUR_PATH="${pair#*=}"

    if [ -z "$CONFIG_KEY" ] || [ -z "$CONJUR_PATH" ]; then
        echo "WARNING: Skipping invalid mapping: '$pair'" >&2
        continue
    fi

    ENCODED_PATH=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${CONJUR_PATH}', safe=''))" 2>/dev/null || \
        echo "${CONJUR_PATH}" | sed 's|/|%2F|g')

    SECRET_VALUE=$(curl -s --fail \
        --connect-timeout 10 \
        --max-time 15 \
        -H "Authorization: Token token=\"${TOKEN}\"" \
        "${CONJUR_APPLIANCE_URL}/secrets/${CONJUR_ACCOUNT}/variable/${ENCODED_PATH}" 2>/dev/null) || true

    if [ -n "$SECRET_VALUE" ]; then
        echo -n "$SECRET_VALUE" > "$SECRET_DIR/$CONFIG_KEY"
        echo "${CONFIG_KEY}=${SECRET_VALUE}" >> "$ENV_FILE"
        echo "  Fetched: $CONFIG_KEY (from $CONJUR_PATH)"
        FETCHED=$((FETCHED + 1))
    else
        echo "  WARNING: Could not fetch: $CONJUR_PATH" >&2
        FAILED=$((FAILED + 1))
    fi
done

chmod 600 "$SECRET_DIR"/*
echo "Done: $FETCHED secrets fetched, $FAILED failed."
echo "Secrets written to $SECRET_DIR/"
