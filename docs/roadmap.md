# Roadmap and known limits

## Done

| Milestone | Summary |
|---|---|
| M0 | Project scaffold, foreground service skeleton |
| M1 | Anonymous read-only FTP on 2121 |
| M2 | Hotspot IP detection, folder picker, live status |
| M3 | mDNS `hostlocal.local` + `_ftp._tcp` (JmDNS) |
| M4 | Hardening: rebind on network change, locks, battery prompt, per-server retry |
| M5 | Setup checklist, icon, signed release config |
| M6 | DLNA/UPnP server with thumbnails; serves on router Wi-Fi when no hotspot |

## Known limitations

- **Discovery on router Wi-Fi** is unreliable: VLC on a tablet and a Sony Bravia (VLC 3.7.1) did not list the server, while the hotspot works. Root cause is multicast not reaching the phone on that router. See [Networking](networking.md).
- **`hostlocal.local` does not resolve on Android clients.**
- **Not verified on hardware**: playback of a real 4K file over Wi-Fi, non-primary storage volumes, behaviour after many hours with the screen off.
- **All-files access** blocks a Play Store release as-is ([decision](decisions.md#storage-all-files-access)).
- Hotspot interface names are matched from a list; an unknown OEM name shows "No network".
- Status is held in static fields, so there is one server set per process and the UI polls instead of observing.
- No UI tests, no CI, no license file.
- Only IPv4.

## Ideas

| Idea | Notes |
|---|---|
| **Cast to TV** | The phone scans the subnet by unicast `M-SEARCH`, finds DLNA renderers (a Bravia is one), and sends `SetAVTransportURI` + `Play` with a `/media/...` URL. Works where multicast is filtered. Plays in the TV's own player, not VLC. |
| Optional FTP login | Username/password for non-hotspot networks |
| Multiple folders | Several roots under one login |
| SAF-based file access | Remove `MANAGE_EXTERNAL_STORAGE`, enable Play distribution |
| Observable state | Replace static fields and polling with a bound service or `StateFlow` |
| Eventing / `SUBSCRIBE` | Only if a client needs it |
| CI | Run `testDebugUnitTest`, `assembleDebug` and `mkdocs build --strict` on PRs |
| Auto-start on hotspot | Start the service when tethering turns on |

## Rejected

| Idea | Why |
|---|---|
| Real domain + dynamic DNS | Needs a domain and internet; the product is offline-first |
| DNS server on the phone | Needs root; system owns port 53 on the hotspot |
| SMB server | Needs port 445 (root); no suitable maintained library |
| Spoofing DNS replies | Needs root, and it is unsafe |
