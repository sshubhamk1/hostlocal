# mDNS hostname

Source: `MdnsHost.kt`

Advertises `hostlocal.local` and a `_ftp._tcp` service using JmDNS, bound to the chosen address.

## What it does

- `JmDNS.create(address, "hostlocal")` makes JmDNS answer `A` queries for `hostlocal.local.` with the bound address. This is a **hostname** responder, which Android's own `NsdManager` can't do (it only registers services).
- Registers `_ftp._tcp.local.` on port 2121, named "HostLocal". VLC's local-network browser can list this.
- The hostname is fixed in `MdnsHost.HOSTNAME`.

## Who can resolve it

| Client | Resolves `hostlocal.local`? |
|---|---|
| macOS, iOS, Windows 10+, Linux with Avahi | yes |
| Android apps (VLC on phone, tablet, Android TV) | **no**: the Android resolver doesn't do mDNS names |

So the name helps laptops only. Android clients use [DLNA](dlna.md) or the IP.

## Verifying on a laptop

```bash
ping hostlocal.local
dns-sd -G v4 hostlocal.local      # macOS
```

!!! info "Verified"
    Running the same JmDNS calls on a Mac and resolving with `ping` and `dns-sd` worked. Resolution on Android clients was found not to work.

## Lifecycle

Created and closed by `HostService.syncToHotspot` / `stopServers`. `stop()` unregisters services before closing; it can block, so it runs off the main thread.

Requires the `MulticastLock` held by `HostService`.
