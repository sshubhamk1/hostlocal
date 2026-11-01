# Reference

## Ports

| Port | Proto | Used by | Direction |
|---|---|---|---|
| 2121 | TCP | FTP control | clients → phone |
| 50000-50010 | TCP | FTP passive data | clients → phone |
| 8200 | TCP | DLNA HTTP (description, SOAP, media, thumbs) | clients → phone |
| 1900 | UDP | SSDP (multicast `239.255.255.250`, broadcast, unicast) | both |
| 5353 | UDP | mDNS (multicast `224.0.0.251`) | both |

## Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Sockets and interface state |
| `CHANGE_WIFI_MULTICAST_STATE` | `MulticastLock` |
| `WAKE_LOCK` | Keep the CPU running |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Foreground service of type `connectedDevice` |
| `POST_NOTIFICATIONS` | Foreground notification (API 33+) |
| `MANAGE_EXTERNAL_STORAGE` | Read videos by file path |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Offer the battery exemption dialog |

## Settings keys

`SharedPreferences("hostlocal")`: `folder`, `dlna_uuid`.

## Build configuration

| Item | Value |
|---|---|
| Application id / namespace | `com.hostlocal` |
| minSdk / targetSdk / compileSdk | 26 / 33 / 36 |
| Gradle / AGP / Kotlin | 8.10.2 / 8.7.3 / 2.0.21 |
| JVM target | 17 |
| Compose BOM | 2024.10.01 |
| Release signing | `keystore.properties` (gitignored) |

## Dependencies

| Library | Version | Used for |
|---|---|---|
| `org.apache.ftpserver:ftpserver-core` | 1.2.0 | FTP |
| `org.slf4j:slf4j-android` | 1.7.36 | MINA logging |
| `org.jmdns:jmdns` | 3.5.9 | mDNS |
| `org.nanohttpd:nanohttpd` | 2.3.1 | DLNA HTTP |
| Compose, Activity, Core KTX | via BOM / pinned | UI |
| `junit:junit` | 4.13.2 | tests |

## Key classes and constants

| Name | Where | Notes |
|---|---|---|
| `Settings.FTP_PORT`, `PASSIVE_PORTS`, `DLNA_PORT`, `DLNA_NAME` | `Settings.kt` | Ports and names |
| `MdnsHost.HOSTNAME` / `FQDN` | `MdnsHost.kt` | `hostlocal` / `hostlocal.local` |
| `Hotspot.pickAddress` | `Hotspot.kt` | Pure, tested |
| `DlnaContent.VIDEO_MIME` | `dlna/DlnaContent.kt` | Supported containers |
| `SsdpMessages` | `dlna/Ssdp.kt` | SSDP strings and subnet math |
| `SsdpStats` | `dlna/Ssdp.kt` | Debug counters |

## Supported video types (DLNA)

`mp4, m4v, mkv, avi, mov, webm, ts, m2ts, wmv, mpg, mpeg, 3gp, flv`. FTP serves any file.

## Glossary

| Term | Meaning |
|---|---|
| SSDP | Simple Service Discovery Protocol: UPnP's multicast discovery on UDP 1900 |
| DIDL-Lite | XML format for describing folders and items in UPnP Browse results |
| mDNS | Multicast DNS: `.local` names on UDP 5353 |
| SAF | Android Storage Access Framework (document picker) |
| IGMP snooping | Router feature that limits multicast delivery; can hide SSDP |
