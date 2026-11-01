# How devices find the phone

Serving files is easy. Getting another device to **find** the phone is where this project spent most of its effort. Read this page before touching discovery code.

## Three ways in, three different mechanisms

| Method | Mechanism | Works on |
|---|---|---|
| Type the IP | none | everything, always |
| `ftp://hostlocal.local:2121` | mDNS multicast (UDP 5353) | laptops. **Not** Android apps |
| VLC → Local Network → HostLocal | SSDP multicast (UDP 1900) | depends on the network |

## Observed results

| Setup | Result |
|---|---|
| Laptop, `ping hostlocal.local` | works |
| VLC on the **phone itself** (loopback) | lists HostLocal as UPnP and FTP |
| Tablet and Sony Bravia (VLC 3.7.1) on the **phone's hotspot** | HostLocal listed |
| Tablet and Bravia on **router Wi-Fi** | not listed |
| Same, with `http://<phone-ip>:8200/desc.xml` opened in a browser | loads, so the network path exists |
| Same, manual `ftp://<phone-ip>:2121` in VLC | works |

## Why Android clients can't use `hostlocal.local`

Android apps resolve names through the system resolver, which doesn't query mDNS for `.local`. VLC on Android therefore never asks the network. There is no app-side fix for the client. Options considered and rejected: a real domain with dynamic DNS (needs a domain and internet), running DNS on the phone (needs root; the hotspot's own DNS owns port 53).

## Why discovery fails on some Wi-Fi networks

VLC finds UPnP servers by **multicasting a search** to `239.255.255.250:1900` and waiting for replies. On the failing router the phone's SSDP counters stayed at 0 for other devices' searches, even though the TV and tablet were announcing on the network. So searches never arrived. Likely causes:

- IGMP snooping or multicast filtering in the router or access point
- client isolation / AP isolation (ruled out here, because HTTP between devices worked)
- the phone's Wi-Fi chip dropping multicast (OxygenOS)

On the phone's **own hotspot** there is no router in the way and it works.

## What the app does about it

`SsdpAdvertiser` announces in three ways every 60 s: multicast, subnet broadcast, and unicast to every host. The goal was to reach clients even if multicast is filtered. On the test router this **did not make VLC list the server**: VLC only lists servers that answered its own search, and its search never reached the phone. The extra announcements are harmless and may help other clients.

The phone also holds a `MulticastLock` and a `WifiLock` (see [Service](components/service.md#locks)). Those fix the phone's own receive filtering where the OEM honours them.

## Recommendations to users

1. Prefer the **hotspot**: no router, discovery works.
2. On router Wi-Fi: add `ftp://<phone-ip>:2121` once in VLC (Browse → Add server). Reserve the phone's IP in the router (DHCP reservation) so it never changes.
3. If you control the router: disable IGMP snooping / multicast filtering for Wi-Fi.

## How to test discovery yourself

??? example "Does the phone answer a search? (run on a laptop on the same network)"
    ```python
    import socket
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.bind(("LAPTOP_IP", 0)); s.settimeout(4)
    s.sendto(b'M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n'
             b'MAN: "ssdp:discover"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n',
             ("239.255.255.250", 1900))
    try:
        while True: print(s.recvfrom(2048)[0].decode().splitlines()[:6])
    except Exception as e: print("done", e)
    ```
    Replies mentioning `HostLocal` or port 8200 mean the phone answers. Then check the app's **SSDP packets by sender** line to see that the laptop's search arrived.

Other useful checks: `dns-sd -G v4 hostlocal.local` (mDNS), and the app's own SSDP counters (see [DLNA diagnostics](components/dlna.md#diagnostics)).

## Ideas not built

See [Roadmap](roadmap.md): a "cast to TV" feature (phone finds the TV by unicast scan and tells it to play a URL).
