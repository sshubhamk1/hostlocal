# Contributing

## Workflow

1. Branch from `main`. Prefix with your handle (the maintainer uses `shubh/`), e.g. `shubh/fix-oneplus-hotspot-iface`.
2. Make a focused change with tests.
3. Run `./gradlew testDebugUnitTest assembleDebug` and, for UI or service changes, smoke test on a device or emulator ([Testing](testing.md)).
4. Run `mkdocs build --strict` if you touched docs.
5. Open a PR with what changed, why, and how you tested it. Say explicitly if you could not test on real hardware.

## Commit messages

Short imperative subject (about 70 characters), blank line, then a body explaining the *why*. History reads like:

```text
Serve on router Wi-Fi when no hotspot is active

Previously only hotspot interfaces were used. Now FTP and mDNS bind to
the hotspot IP if present, else wlan0/eth IP...
```

## Code style

- Kotlin official style (`kotlin.code.style=official`). Match the surrounding code.
- Keep comments to the *why*: constraints, platform quirks, security boundaries. Don't narrate the code.
- Put logic in plain Kotlin that can be tested on the JVM; keep Android-specific code thin.
- Anything touching the network must run off the main thread.

## Where to be careful

| Area | Rule |
|---|---|
| `DlnaContent.resolve` | Security boundary. Any change needs path-traversal tests. |
| FTP authorities | Read-only is a product promise. Don't add write permissions casually. |
| Binding | Servers bind to the chosen interface IP, never `0.0.0.0`. |
| `HostService.syncToHotspot` | Servers retry independently; keep that. |
| Discovery code | Test on a real hotspot **and** a router; the emulator can't show multicast behaviour. |
| Release config | R8 stays off unless MINA and JmDNS are re-verified on a device. |

## Common tasks

??? example "Support a new video format"
    Add the extension and MIME type to `DlnaContent.VIDEO_MIME`. FTP serves any file already. Add a test case in `DlnaTest`.

??? example "Support a new phone's hotspot interface"
    Add its name to `Hotspot.AP_NAME` and a case to `HotspotTest`.

??? example "Change a port"
    Edit the constant in `Settings.kt`. Update [Reference](reference.md) and any firewall notes. Tests use their own ports.

??? example "Add a setting"
    Add a property in `Settings.kt`, a control in `MainActivity.kt`, and document the key in [UI and settings](components/ui.md).

## Secrets

Never commit `keystore.properties`, `*.jks` or `local.properties` (all gitignored). Don't add credentials or analytics.

## Docs

Docs live in `docs/`, nav in `mkdocs.yml`. Update the relevant page when behaviour changes, and add new pages to `nav` (strict mode fails on pages not linked and on broken links).

## Docs publishing

`.github/workflows/docs.yml` builds the site with `mkdocs build --strict` on every PR that touches `docs/`, `mkdocs.yml` or `requirements-docs.txt`. On a push to `main` it also publishes the built site to the **`build`** branch (`mkdocs gh-deploy --force --remote-branch build`). That branch contains generated files only; never edit it by hand.

To serve it, in the GitHub repo go to Settings → Pages → Source: *Deploy from a branch* → Branch `build`, folder `/ (root)`. The workflow needs `contents: write`, which the workflow file already requests.
