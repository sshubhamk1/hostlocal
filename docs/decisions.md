# Design decisions

Short records of why things are the way they are. Change them deliberately.

## Native Android, Kotlin

The app needs a foreground service, hotspot interface detection, multicast locks and raw sockets. Native Kotlin with Compose gives direct access; cross-platform frameworks would still need native modules. iOS is out of scope: it can't run a background FTP server reliably.

## FTP with Apache MINA

Chosen for a mature server with a read-only permission model. Requires real file paths (see below). Port 2121 because ports under 1024 need root. Anonymous and read-only so a TV needs no setup; the hotspot password is the gate.

## Storage: all-files access {#storage-all-files-access}

MINA serves a `java.io.File` root, and the DLNA server streams files with `FileInputStream`. Storage Access Framework URIs don't give that. The app therefore uses `MANAGE_EXTERNAL_STORAGE` ("All files access"), and the SAF picker is used only to *pick* a folder, converted to a path by `treeUriToPath`.

*Trade-off*: this permission is restricted on Google Play. It is fine for side-loading and personal use. A Play release would need a SAF-based `FileSystemView` and `DocumentFile` streaming, which is a rewrite of both servers' file access.

## mDNS with JmDNS, not NsdManager

`NsdManager` registers *services* but doesn't answer `A` queries for an arbitrary hostname. JmDNS runs a real responder, so `hostlocal.local` resolves. It must be bound to the chosen interface address.

## Hand-written UPnP, not Cling or jUPnP

A video-only MediaServer needs SSDP, one SOAP service and file streaming, around 500 lines. Cling is unmaintained and jUPnP pulls in Jetty. Writing it also keeps SSDP behaviour under our control, which mattered while debugging discovery.

## DLNA added beside FTP, not instead of it

Android clients can't resolve `hostlocal.local`, so DLNA gives zero-typing discovery. FTP stays because it works reliably when discovery doesn't (manual entry). An SMB server was considered and rejected: it needs port 445 (root) and there is no suitable maintained library.

## Stateless DLNA object ids

Base64 of the relative path avoids a database or in-memory table and survives restarts. The cost is that ids reveal paths to clients, which already see them.

## Polling watcher, not network callbacks

A 3-second poll of `NetworkInterface` is simple and robust across OEM hotspot implementations, where `ConnectivityManager` callbacks don't reliably report the tethering interface. Cost: up to 3 seconds to notice a change.

## Bind to the interface IP

FTP and DLNA never listen on `0.0.0.0`, so they aren't reachable over mobile data or other interfaces.

## R8 off

MINA and JmDNS use reflection. Shrinking saves little (the APK is about 7 MB) and risks runtime failures that only appear on a device.

## Unicast, broadcast and multicast announcements

Added to cope with routers that filter multicast. It didn't make VLC list the server on the test router, but it is cheap and kept. See [Networking](networking.md).
