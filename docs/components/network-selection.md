# Network selection

Source: `Hotspot.kt`, test: `HotspotTest.kt`

The app must serve on whichever network the phone currently offers, and that changes (hotspot on/off, Wi-Fi joined). `Hotspot.currentAddress()` returns one IPv4 address, or `null`.

## Rules

1. Look at interfaces that are up and not loopback.
2. Keep only **site-local IPv4** addresses (`10.x`, `172.16-31.x`, `192.168.x`).
3. Prefer an **access-point interface**: names matching `ap*`, `swlan*`, `softap*`, `wlan1`+ or `rndis*`.
4. Otherwise fall back to the **client network**: `wlan0` or `ethN`.
5. Otherwise `null`. Mobile data (`rmnet_*`) is never used.

The hotspot wins over Wi-Fi, so if the phone has both, clients on the hotspot are served.

## Why a name list

Android gives no public API for "the hotspot's IP". Interface names vary by chipset and OEM, so the app matches known names. `pickAddress` is pure (takes `IfaceInfo(name, ipv4)`), which is what `HotspotTest` exercises.

!!! warning "Unknown OEM interface names"
    If the app says "No network" while the hotspot is on, the OEM probably uses a name that isn't in the list. Get it with `adb shell ip -4 -o addr` while the hotspot is on, then add it to `AP_NAME` and a test case.

## Hotspot vs router

| | Hotspot | Router Wi-Fi |
|---|---|---|
| Phone's address | usually fixed (e.g. `192.168.43.1`) | DHCP, can change; reserve it in the router |
| Discovery | works | often fails; see [Networking](../networking.md) |
| Needs internet | no | no |

## Changing the rules

Edit the two regexes and add a case to `HotspotTest`. Keep the preference order tested (`hotspotPreferredOverWifi`, `fallsBackToRouterWifi`).
