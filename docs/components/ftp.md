# FTP server

Source: `FtpHost.kt`, test: `FtpHostTest.kt`

Wraps Apache MINA FtpServer.

## Behaviour

- Port **2121** (unprivileged; port 21 needs root). Constant: `Settings.FTP_PORT`.
- Bound to the address chosen by [network selection](network-selection.md).
- **Anonymous login only**, one user `anonymous` whose home is the shared folder.
- **Read-only**: the user is created with an empty `authorities` list, so MINA rejects STOR, DELE, RNFR, MKD and the rest. An upload returns `550`, a delete `450`.
- **Passive mode** ports `50000-50010` (`Settings.PASSIVE_PORTS`). Clients open data connections here, so a firewall in between must allow them.

## Why these choices

Anonymous and read-only because the hotspot is already password-protected and the goal is zero setup on a TV. If you add authentication, keep VLC's behaviour in mind: its "Add server" screen asks for a username, but many users leave it blank.

## Changing it

- **Port**: change `Settings.FTP_PORT`. The UI and the DLNA `_ftp._tcp` advertisement read the constant.
- **Write access**: add `WritePermission()` to `authorities`. Update `FtpHostTest.uploadDenied`, which asserts the opposite.
- **Different root**: `FtpHost(rootDir, bindAddress, port)`; tests use `127.0.0.1` and port 2129.

## Test notes

`FtpHostTest` starts a real server on `127.0.0.1:2129` and uses `java.net.URL("ftp://...")`. It checks anonymous download, upload rejection and that the file is intact. Don't run it in parallel with another process using port 2129.

## Gotchas

- MINA on Android needs `META-INF` excludes in `packaging` (already in `app/build.gradle.kts`), and `slf4j-android` for logging.
- The shared folder is read with real file paths, which requires all-files access ([decision](../decisions.md#storage-all-files-access)).
