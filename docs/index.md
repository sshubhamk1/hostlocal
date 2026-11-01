# HostLocal developer docs

HostLocal is an Android app (Kotlin, Jetpack Compose) that serves one folder of videos from a phone, read-only, to other devices on the same network. The main use is a TV connected to the phone's **mobile hotspot**, playing videos in VLC without copying files. Phone and TV can instead share a home router.

## What it does

- **FTP**: anonymous, read-only, on port 2121. VLC connects with `ftp://<phone-ip>:2121/`.
- **`hostlocal.local`**: a stable name for the phone, advertised with mDNS, so users don't type a changing IP on laptops.
- **DLNA / UPnP**: VLC lists the phone under Local Network, with thumbnails, without typing anything.
- **Follows the network**: serves on the hotspot address if the hotspot is on, otherwise the Wi-Fi address, and rebinds when it changes.

## Who reads what

| You want to... | Read |
|---|---|
| Build and run the app | [Set up and build](getting-started.md) |
| Understand how the pieces fit | [Architecture](architecture.md) |
| Change one server | [Components](components/service.md) |
| Know why a choice was made | [Design decisions](decisions.md) |
| Debug "the TV can't see the phone" | [Networking](networking.md), [Troubleshooting](troubleshooting.md) |
| Run or write tests | [Testing](testing.md) |
| Open a PR | [Contributing](contributing.md) |
| Look up a port, permission or file | [Reference](reference.md) |
| See what is unfinished | [Roadmap and known limits](roadmap.md) |

## Tech at a glance

| Area | Choice |
|---|---|
| Language / UI | Kotlin 2.0.21, Jetpack Compose (Material 3) |
| Build | Gradle 8.10.2, Android Gradle Plugin 8.7.3, JDK 17 |
| SDK | minSdk 26, targetSdk 33, compileSdk 36 |
| FTP | Apache MINA FtpServer 1.2.0 |
| mDNS | JmDNS 3.5.9 |
| DLNA HTTP | NanoHTTPD 2.3.1; UPnP/SSDP code written in-repo |
| Tests | JUnit 4 on the JVM (no emulator needed) |

## The one thing to know first

Discovery is the hard part, not serving files. Serving over FTP and HTTP is plain and works everywhere. Whether another device **finds** the phone depends on multicast traffic, which Android clients, routers and Wi-Fi chips treat inconsistently. Read [How devices find the phone](networking.md) before changing anything in `Ssdp.kt` or `MdnsHost.kt`.
