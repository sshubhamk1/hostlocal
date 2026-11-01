# DLNA / UPnP

Source: `app/src/main/java/com/hostlocal/dlna/`, tests: `DlnaTest.kt`

A minimal UPnP **MediaServer:1** so VLC lists the phone under Local Network without typing a name or IP. It is hand-written (no Cling or jUPnP) because the client only needs three small pieces.

## Pieces

| Piece | File | Job |
|---|---|---|
| SSDP discovery | `Ssdp.kt` | Answer `M-SEARCH`, announce `NOTIFY alive/byebye` |
| Device and service XML | `DlnaDescriptions.kt` | `/desc.xml`, `/cd/scpd.xml`, `/cm/scpd.xml` |
| ContentDirectory + streaming | `DlnaServer.kt`, `DlnaContent.kt` | SOAP `Browse`, DIDL-Lite, Range streaming |
| Wiring | `DlnaHost.kt` | Starts HTTP then SSDP; stops both |

## HTTP routes (port 8200)

| Route | Purpose |
|---|---|
| `GET /desc.xml` | Device description (`urn:schemas-upnp-org:device:MediaServer:1`, friendly name "HostLocal") |
| `GET /cd/scpd.xml`, `/cm/scpd.xml` | Service descriptions |
| `POST /cd/control` | SOAP: `Browse`, `GetSystemUpdateID`, `GetSearchCapabilities`, `GetSortCapabilities` |
| `POST /cm/control` | SOAP: `GetProtocolInfo`, `GetCurrentConnectionIDs`, `GetCurrentConnectionInfo` |
| `GET /media/<id>.<ext>` | Video bytes with `Range` support |
| `GET /thumb/<id>.jpg` | JPEG thumbnail |

Eventing (`SUBSCRIBE`) is not implemented; VLC doesn't need it.

## Object ids

An id is the **URL-safe base64 of the path relative to the shared root** (`0` for the root). It is stateless and reversible: no table to keep in sync, and ids stay stable across restarts.

`DlnaContent.resolve(root, id)` is the security boundary. It returns a file only if:

- the id decodes, and no path segment starts with `.` (blocks `..` and hidden files),
- the canonical path is inside the canonical root (blocks symlink escapes),
- the file exists.

`media` and `thumb` also require a known video extension. Tests: `traversalRejected`, `nonVideoNotServed`.

## Browse

`DidlBuilder.browse(objectId, flag, start, count)` returns a DIDL-Lite document plus `NumberReturned` and `TotalMatches`.

- `BrowseDirectChildren` pages with `StartingIndex` / `RequestedCount` (`0` means all).
- `BrowseMetadata` describes one object.
- Folders sort first, then names case-insensitively. Hidden entries and non-video files are skipped.
- Video extensions and MIME types live in `DlnaContent.VIDEO_MIME`. Add a row to support a container.

## Streaming

`DlnaServer.media` serves straight from disk with a `FileInputStream`, positioned for `Range`:

- no `Range` → `200`, full length
- valid range → `206` with `Content-Range`
- unsatisfiable → `416`
- always `Accept-Ranges: bytes` and DLNA headers (`contentFeatures.dlna.org`, `transferMode.dlna.org: Streaming`)

No transcoding. The pure function `DlnaContent.parseRange` is unit-tested.

!!! info "Throughput"
    A local download measured about 400 MB/s, so the server is not a bottleneck. Real limits are Wi-Fi airtime and the phone's storage. HTTP and FTP move the same bytes.

## Thumbnails

`VideoThumbs` (Android) implements the `ThumbProvider` interface: `MediaMetadataRetriever` grabs a frame at about 1 s, scales to 320 px wide, writes a JPEG into `cacheDir/thumbs`, keyed by SHA-1 of path and mtime. A `Semaphore(2)` limits concurrent extraction. Failure returns `null` and the route 404s, so a missing thumbnail never breaks browsing. `DlnaServer` takes the provider as a nullable parameter so tests run on the JVM without Android.

## SSDP

`SsdpMessages` is pure (strings in, strings out) and unit-tested. `SsdpAdvertiser`:

1. Binds a `MulticastSocket` to UDP 1900 (reuse-address), joins `239.255.255.250` on the interface that owns the bound IP.
2. A listener thread answers matching `M-SEARCH` requests by unicast to the sender. It matches `ssdp:all`, `upnp:rootdevice`, the `uuid:`, the device type and both service types.
3. Every 60 seconds it announces `NOTIFY ssdp:alive` three ways: to the **multicast group**, to the **subnet broadcast address**, and by **unicast to every host in the subnet** (`SsdpMessages.hostsIn`, capped at about 1,000 hosts). The last two exist for networks that filter multicast; see [Networking](../networking.md).
4. On stop it sends `byebye`, then closes the socket.

The device UUID is generated once and stored (`Settings.dlnaUuid`) so clients don't see a new server on every launch.

## Diagnostics

`SsdpStats` (global object) counts searches received, replies sent, the last sender, and datagrams per sender IP. The main screen shows them. They answer the key question "do other devices' searches reach the phone at all?"

## Extending

- **New action**: add it to the `when` in `contentDirectory()` and to `contentDirectoryScpd` in `DlnaDescriptions.kt`.
- **Audio/images**: widen `children()` and `VIDEO_MIME`, and set the right `upnp:class` in `DidlBuilder.describe`.
- Always add a test in `DlnaTest` (the HTTP server runs on `127.0.0.1:8209` there).
