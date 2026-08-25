#!/usr/bin/env bash
# Push a freshly built OSTree commit from the Yocto deploy dir to the OTA server.
#
#   ./push-ostree.sh <local-repo> <server-repo-url> <branch>
#
# Server side must expose an archive-mode repo over HTTP(S) with write auth.
set -euo pipefail

LOCAL_REPO="${1:?path to DEPLOY_DIR_IMAGE/ostree_repo}"
SERVER_URL="${2:?e.g. https://ota.example.com/repo}"
BRANCH="${3:?e.g. raspberrypi4-64-main}"
TOKEN="${OTA_PUSH_TOKEN:?export OTA_PUSH_TOKEN}"

COMMIT=$(ostree --repo="${LOCAL_REPO}" rev-parse "${BRANCH}")
echo "commit: ${COMMIT}"

# Sign the commit before publishing. Key must NOT live in this repo.
if [ -n "${OSTREE_GPG_KEY_ID:-}" ]; then
    ostree --repo="${LOCAL_REPO}" gpg-sign "${COMMIT}" "${OSTREE_GPG_KEY_ID}"
fi

ostree --repo="${LOCAL_REPO}" summary --update

# Generate static deltas from the last N published commits -> small downloads.
PREV=$(curl -fsSL "${SERVER_URL}/../api/v1/targets/${BRANCH}/latest" \
        -H "Authorization: Bearer ${TOKEN}" | sed -n 's/.*"commit":"\([a-f0-9]*\)".*/\1/p' || true)
if [ -n "${PREV}" ] && [ "${PREV}" != "${COMMIT}" ]; then
    echo "static delta ${PREV} -> ${COMMIT}"
    ostree --repo="${LOCAL_REPO}" static-delta generate --from="${PREV}" --to="${COMMIT}"
    ostree --repo="${LOCAL_REPO}" summary --update
fi

# Transport: rsync/ssh to the server's repo dir, or an HTTP upload endpoint.
# rsync is the simplest correct option — content-addressed objects, safe to merge.
rsync -a --info=progress2 "${LOCAL_REPO}/" "${OTA_RSYNC_TARGET:?export OTA_RSYNC_TARGET}/"

# Register the new target so the server can offer it to devices.
curl -fsS -X POST "${SERVER_URL%/repo}/api/v1/targets" \
     -H "Authorization: Bearer ${TOKEN}" \
     -H 'Content-Type: application/json' \
     -d "{\"branch\":\"${BRANCH}\",\"commit\":\"${COMMIT}\",\"version\":\"${BUILD_VERSION:-dev}\"}"

echo "published ${BRANCH} @ ${COMMIT}"
