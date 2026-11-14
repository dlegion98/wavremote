#!/usr/bin/env bash
# One-time setup: creates a GitHub repo for WavRemote, stores the signing key as secrets,
# and pushes the code. After that, every `./publish-to-github.sh` (or git push) ships an update.
#
# Usage:  ./publish-to-github.sh [repo-name]      (default: wavremote)
set -euo pipefail
cd "$(dirname "$0")"
REPO_NAME="${1:-wavremote}"

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI isn't installed. Install it with:  brew install gh"
  exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
  echo "Sign in to GitHub (a browser window will open)…"
  gh auth login --web --git-protocol https --scopes workflow
fi
# Pushing .github/workflows needs the "workflow" permission on the gh login.
if ! gh auth status 2>&1 | grep -q "workflow"; then
  echo "Granting GitHub CLI permission to publish the build workflow (browser will open)…"
  gh auth refresh -h github.com -s workflow
fi

if [ ! -f signing/keystore.properties ] || [ ! -f signing/wavremote.jks ]; then
  echo "Missing signing/ folder (wavremote.jks + keystore.properties). Ask Claude to copy it again."
  exit 1
fi

OWNER="$(gh api user --jq .login)"

# --- git repo
if [ ! -d .git ]; then
  git init -b main >/dev/null
fi
if [ -z "$(git config user.email || true)" ]; then
  git config user.name "$OWNER"
  git config user.email "$OWNER@users.noreply.github.com"
fi
git add -A
if git diff --cached --quiet; then
  echo "Nothing new to commit."
else
  git commit -m "${COMMIT_MSG:-Update WavRemote}" >/dev/null
fi

# --- GitHub repo (public, so the app can read releases without a password)
if ! gh repo view "$OWNER/$REPO_NAME" >/dev/null 2>&1; then
  echo "Creating github.com/$OWNER/$REPO_NAME …"
  gh repo create "$OWNER/$REPO_NAME" --public --description "WavRemote: Wavlink mesh router app" >/dev/null
fi
if ! git remote get-url origin >/dev/null 2>&1; then
  git remote add origin "https://github.com/$OWNER/$REPO_NAME.git"
fi

# --- signing secrets (only needs doing once, harmless to repeat)
get() { grep "^$1=" signing/keystore.properties | cut -d= -f2-; }
echo "Saving the signing key to the repo's secrets…"
base64 -i signing/wavremote.jks | tr -d '\n' | gh secret set KEYSTORE_BASE64 -R "$OWNER/$REPO_NAME"
gh secret set KEYSTORE_PASSWORD -R "$OWNER/$REPO_NAME" -b "$(get storePassword)"
gh secret set KEY_ALIAS -R "$OWNER/$REPO_NAME" -b "$(get keyAlias)"
gh secret set KEY_PASSWORD -R "$OWNER/$REPO_NAME" -b "$(get keyPassword)"

# --- push (triggers the build + release). Use the gh login for git, not an old saved password.
gh auth setup-git >/dev/null 2>&1 || true
git push -u origin main

echo
echo "Pushed. GitHub is building the app now (about 5 minutes)."
echo "Follow it with:   gh run watch -R $OWNER/$REPO_NAME"
echo "First install on your phone: open https://github.com/$OWNER/$REPO_NAME/releases/latest and tap the .apk."
echo "After that, updates appear inside the app (More → App updates)."
