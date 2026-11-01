# HostLocal

Android app that shares one folder of videos from your phone, read-only, so a TV or tablet on the same network can play them in VLC. Built for the case where the phone is the **mobile hotspot** and the TV connects to it. It also works when the phone and TV are on the same router Wi-Fi.

It runs three servers at once, all bound to the phone's current network address:

| Server | Port | How a client uses it |
|---|---|---|
| FTP, anonymous, read-only | TCP 2121 (data 50000-50010) | VLC: `ftp://<phone-ip>:2121/` |
| mDNS hostname `hostlocal.local` | UDP 5353 | Laptops: `ftp://hostlocal.local:2121/` |
| DLNA / UPnP media server | HTTP TCP 8200, discovery UDP 1900 | VLC → Browse → Local Network → "HostLocal" |

!!! note "Android clients can't resolve `.local`"
    Android's resolver does not do mDNS hostnames, so `hostlocal.local` works on laptops but not in VLC on Android or Android TV. Use the DLNA entry or the phone's IP there. See [How devices find the phone](docs/networking.md).

## Status

Milestones M0 to M6 are done (see [docs/roadmap.md](docs/roadmap.md)). Verified on an Android emulator and in unit tests. On real devices: the phone's own VLC lists HostLocal (UPnP and FTP), and on the phone's hotspot other devices find it. On a router Wi-Fi, VLC on a tablet and a Sony Bravia did not list it; adding `ftp://<phone-ip>:2121` by hand works. Details in [docs/networking.md](docs/networking.md).

## Quick start

You need JDK 17 and the Android SDK (platform 36).

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On the phone: open HostLocal, grant the setup items, pick a folder, turn on the hotspot (or join Wi-Fi), tap **Start**. Full instructions, including a workaround if the Gradle wrapper can't download, are in [docs/getting-started.md](docs/getting-started.md).

## Documentation

Developer docs are a [Material for MkDocs](https://squidfunnel.github.io/mkdocs-material/) site in `docs/`.

```bash
python3 -m venv .venv-docs && source .venv-docs/bin/activate
pip install -r requirements-docs.txt
mkdocs serve                     # http://127.0.0.1:8000
```

Start with [docs/index.md](docs/index.md), then [Architecture](docs/architecture.md).

## Project layout

```
app/src/main/java/com/hostlocal/
  MainActivity.kt      Compose UI, permissions, live status
  HostService.kt       foreground service; owns all servers
  Hotspot.kt           picks the network address to serve on
  FtpHost.kt           Apache MINA FTP server (anonymous, read-only)
  MdnsHost.kt          JmDNS: hostlocal.local + _ftp._tcp
  dlna/                DLNA: SSDP, ContentDirectory, HTTP streaming
  VideoThumbs.kt       JPEG thumbnails for DLNA
  Settings.kt          shared-folder path, DLNA UUID, constants
app/src/test/          JVM unit tests (no device needed)
docs/                  MkDocs site
```

## License

No license file yet. Add one before sharing the code outside the team.
