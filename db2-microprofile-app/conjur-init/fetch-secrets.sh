#!/bin/sh
set -e

echo "=== Conjur Kubernetes Init Container ==="

CONJUR_URL="${CONJUR_APPLIANCE_URL:?CONJUR_APPLIANCE_URL is required}"
CONJUR_ACCOUNT="${CONJUR_ACCOUNT:?CONJUR_ACCOUNT is required}"
SERVICE_ID="${CONJUR_AUTHN_JWT_SERVICE_ID:-kubernetes}"
SECRET_MAPPINGS="${CONJUR_SECRET_MAPPINGS:?CONJUR_SECRET_MAPPINGS is required}"
OUTPUT_DIR="${CONJUR_SECRETS_DIR:-/conjur/secrets}"
SA_TOKEN_PATH="/var/run/secrets/kubernetes.io/serviceaccount/token"

echo "Conjur URL:  ${CONJUR_URL}"
echo "Account:     ${CONJUR_ACCOUNT}"
echo "JWT Service: ${SERVICE_ID}"
echo "Output dir:  ${OUTPUT_DIR}"

# Read Kubernetes service account JWT
if [ ! -f "${SA_TOKEN_PATH}" ]; then
    echo "ERROR: SA token not found at ${SA_TOKEN_PATH}"
    exit 1
fi
JWT=$(cat "${SA_TOKEN_PATH}")
echo "JWT loaded (${#JWT} chars)"

# Authenticate with Conjur via authn-jwt
echo ""
echo "Authenticating with Conjur authn-jwt/${SERVICE_ID}..."
HTTP_RESPONSE=$(curl -sk -w "\n%{http_code}" \
    --data "jwt=${JWT}" \
    "${CONJUR_URL}/authn-jwt/${SERVICE_ID}/${CONJUR_ACCOUNT}/authenticate" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -H "Accept-Encoding: base64")

HTTP_CODE=$(echo "$HTTP_RESPONSE" | tail -1)
TOKEN=$(echo "$HTTP_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" != "200" ] || [ -z "$TOKEN" ]; then
    echo "ERROR: Authentication failed (HTTP ${HTTP_CODE})"
    echo "Response: ${TOKEN}"
    exit 1
fi
echo "Authenticated successfully"

# Create output
mkdir -p "${OUTPUT_DIR}"
: > "${OUTPUT_DIR}/secrets.properties"

# Fetch each secret
# Format: CONJUR_SECRET_MAPPINGS="prop.key=conjur/path,prop.key2=conjur/path2"
echo ""
echo "Fetching secrets..."
SAVED_IFS="$IFS"
IFS=','
for MAPPING in $SECRET_MAPPINGS; do
    MAPPING=$(echo "$MAPPING" | sed 's/^ *//;s/ *$//')

    PROP_KEY=$(echo "$MAPPING" | cut -d'=' -f1)
    SECRET_PATH=$(echo "$MAPPING" | cut -d'=' -f2-)

    ENCODED=$(echo "$SECRET_PATH" | sed 's|/|%2F|g')

    VALUE=$(curl -sk \
        -H "Authorization: Token token=\"${TOKEN}\"" \
        "${CONJUR_URL}/secrets/${CONJUR_ACCOUNT}/variable/${ENCODED}")

    echo "${PROP_KEY}=${VALUE}" >> "${OUTPUT_DIR}/secrets.properties"

    case "$PROP_KEY" in
        *password*|*secret*|*key*)
            echo "  ${PROP_KEY} = ****"
            ;;
        *)
            echo "  ${PROP_KEY} = ${VALUE}"
            ;;
    esac
done
IFS="$SAVED_IFS"

LINES=$(wc -l < "${OUTPUT_DIR}/secrets.properties")
echo ""
echo "=== Init complete: ${LINES} secrets written to ${OUTPUT_DIR}/secrets.properties ==="
