# Testing

Three levels: JVM unit tests (fast, run these always), emulator smoke tests, and real-device checks (the only way to verify discovery).

## Unit tests

```bash
./gradlew testDebugUnitTest
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`.

| File | Covers |
|---|---|
| `HotspotTest` | Interface choice: hotspot preferred, Wi-Fi fallback, mobile data ignored |
| `FtpHostTest` | Real MINA server on `127.0.0.1:2129`: anonymous download works, upload and delete rejected |
| `DlnaTest` | Real HTTP server on `127.0.0.1:8209`: descriptions, Browse and paging, Range 200/206/416, path traversal, hidden and non-video files, id round trip, SSDP matching, subnet sweep math |

!!! warning "Fixed ports"
    The tests bind real ports (2129 and 8209). Don't run two Gradle test processes at once.

### Writing tests

- Keep new logic **pure** where possible, as `Hotspot.pickAddress`, `SsdpMessages` and `DlnaContent.parseRange` are. Android classes (`Context`, `MediaMetadataRetriever`) can't run in JVM tests, which is why `DlnaServer` takes a nullable `ThumbProvider`.
- For server behaviour, start the server on `127.0.0.1` with a temp folder and talk to it over HTTP/FTP, as the existing tests do.

## Emulator smoke test

The emulator has a client network (`eth0`/`wlan0`, `10.0.2.x`) but no hotspot and no multicast to your machine, so it verifies startup, not discovery.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set com.hostlocal MANAGE_EXTERNAL_STORAGE allow
adb shell pm grant com.hostlocal android.permission.POST_NOTIFICATIONS
adb shell am start -n com.hostlocal/.MainActivity
```

The service isn't exported, so start it by tapping **Start** in the UI (or `adb shell input tap`). Then:

```bash
adb shell ss -ltn | grep -E ':(2121|8200)'     # both listening on 10.0.2.x
adb logcat -d | grep -c "FATAL EXCEPTION"      # expect 0
```

To reach the FTP server from your machine on the emulator, forward its ports (the server binds to the LAN IP, so test from inside with `nc`, or use a loopback-bound build). Unit tests already cover FTP behaviour.

## Real-device checklist

Do this before a release, on the phone hotspot and on a router.

1. Install, grant setup items, pick a folder with a few videos, **Start**.
2. **Laptop**: `ping hostlocal.local`; `curl ftp://<ip>:2121/` lists videos; an upload is refused.
3. **VLC on the phone**: Browse → Local Network shows HostLocal (UPnP, FTP).
4. **VLC on a TV/tablet**: Local Network shows HostLocal. If not, `ftp://<ip>:2121` added manually plays and seeks.
5. **Streaming**: play a large (ideally 4K) file and seek.
6. **Network change**: turn the hotspot off and on; the screen returns to "serving" with the new IP, and clients reconnect.
7. **Screen off for several minutes**: playback continues.

## Discovery checks from a laptop

See [How to test discovery yourself](networking.md#how-to-test-discovery-yourself) for the SSDP search script, and use `dns-sd -G v4 hostlocal.local` for mDNS.

## What is not covered

No UI tests. No test of real multicast across a router. No automated test of thumbnail generation on a real video. See [Roadmap](roadmap.md).
