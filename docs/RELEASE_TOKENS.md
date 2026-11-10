# Release tokens and signing secrets

**Never commit secrets.** `RELEASE_TOKEN` is a repository secret, never in the tree.

| Signal / env | Meaning |
|--------------|---------|
| `SEC-RELEASE-TOKEN-MISSING` | Required release automation secret absent |
| `RELEASE_TOKEN` | GitHub token / bot token for release workflows |
| `ANDROID_KEYSTORE_BASE64` | Upload keystore (base64) |
| `GOLDENPATH_UPLOAD_STORE_FILE` | Path after decode |
| `GOLDENPATH_UPLOAD_STORE_PASSWORD` | Keystore password |
| `GOLDENPATH_UPLOAD_KEY_ALIAS` | Key alias |
| `GOLDENPATH_UPLOAD_KEY_PASSWORD` | Key password |

`scripts/setup-github-repo.sh --quiet` prints the secrets checklist once. `release.yml` must pin `--ref` on `workflow_dispatch` and assert it matches the tag.
