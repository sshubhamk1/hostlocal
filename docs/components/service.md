# Service and lifecycle

Source: `HostService.kt`

`HostService` is a foreground service (type `connectedDevice`). It exists to keep the servers alive while the app is in the background, and to reconfigure them when the network changes.

## Start and stop

- `HostService.start(context)` / `stop(context)` are the only entry points. Stop sends `ACTION_STOP`; the service calls `stopSelf()`.
- `onStartCommand` runs `startForeground` first (required within seconds of `startForegroundService`), then validates the folder. A missing folder sets `lastError` and stops the service.
- It returns `START_STICKY`, so Android restarts it if the process is killed. After a restart the intent is `null`, which the code treats as a normal start.
- `onDestroy` cancels the watcher, releases the locks and stops the servers on a throwaway executor.

## Locks

| Lock | Why |
|---|---|
| `MulticastLock` | Without it, Android's Wi-Fi chip may drop incoming multicast (SSDP, mDNS). |
| `WifiLock` (`FULL_LOW_LATENCY` on API 29+) | Keeps Wi-Fi out of power-save so multicast and long streams aren't throttled. |
| `PARTIAL_WAKE_LOCK` | Keeps the CPU running with the screen off during playback. |

All three are non-reference-counted and released in `onDestroy`.

## The watcher

`startWatcher` schedules `syncToHotspot(folder)` every 3 seconds on a single-thread executor.

```text
ip = Hotspot.currentAddress()
if ip != boundIp:  stopServers(); boundIp = ip; clear errors
if ip == null:     return                    # "waiting for network"
if ftp  == null:   try start FtpHost         # each server retries alone
if mdns == null:   try start MdnsHost
if dlna == null:   try start DlnaHost
```

Because each block checks its own `null`, a persistent failure (for example the DLNA port already in use) is retried every 3 seconds without bouncing FTP or mDNS. The error stays visible in the UI until it clears.

## Shared status

Companion `@Volatile` fields expose state to the UI:

| Field | Meaning |
|---|---|
| `running` | Service started |
| `ftpAddress`, `mdnsAddress`, `dlnaAddress` | Address each server is bound to, or `null` |
| `ftpError`, `mdnsError`, `dlnaError` | Last start error per server |
| `lastError` | Fatal start error (e.g. folder not found) |

`SsdpStats` (in `dlna/Ssdp.kt`) holds discovery counters; see [DLNA](dlna.md#diagnostics).

## Adding a fourth server

1. Add a wrapper class with `start()` and `stop()`.
2. Add a `var` and a block in `syncToHotspot`, plus a line in `stopServers`.
3. Add `...Address` and `...Error` fields to the companion object.
4. Show it in `MainActivity`.
