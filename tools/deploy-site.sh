#!/usr/bin/env bash
# Deploy the website (site/) to Cloudflare, with a copy of the current APK and of the install
# scripts, so a TV's owner can download them from a domain instead of GitHub.
#
#   tools/deploy-site.sh
#
# STATUS, 2026-11-04: NOT RUN. Orange is not published (no Release page, no public site, no
# promotion — the current rule is "no outreach, but keep the update channel working"), so there is
# nothing to deploy this for. The script is kept because the pieces work and the decision is
# reversible; do not run it as part of a release. See docs/process/DISTRIBUTION.md.
#
# Needs npx and a Cloudflare login (wrangler.jsonc). The scripts are copied from this checkout:
# deploy from main. The APK comes from update/, the same file the TVs update from, so the site can
# never drift from what is actually shipped.
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f update/orange.apk ] || { echo "error: update/orange.apk is missing; run tools/release.sh first" >&2; exit 1; }
git diff --quiet HEAD -- tools || echo "warning: tools/ has uncommitted changes; they will be published" >&2

rm -rf site/download site/tools site/install.sh
mkdir -p site/download site/tools
cp update/orange.apk site/download/Orange.apk
# The version the APK claims, not a tag: there are no git tags and no Release page.
python3 - <<'PY' > site/download/version.txt
import json, pathlib
print(json.loads(pathlib.Path("update/version.json").read_text())["version"])
PY
cp tools/get.sh site/install.sh
cp tools/install.sh tools/restore.sh tools/tcast-push.py tools/report.sh tools/selftest.sh site/tools/
echo "Publishing $(cat site/download/version.txt): $(ls site/download | tr '\n' ' ')"

# The tile pictures TVs download (IconLibrary). It is off since 26.5.0 and these pictures are not
# ours to put in a public repo, so they are only uploaded if they are sitting in local/.
ICONS=local/dangbei-icons/icons
if [ -d "$ICONS" ]; then
  python3 tools/icon-library.py "$ICONS" site/icons
else
  echo "note: $ICONS is missing; deploying without the icon library (IconLibrary is off anyway)" >&2
fi

npx wrangler@4 deploy
