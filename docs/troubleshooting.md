# Troubleshooting

| Symptom | Likely cause | What to do |
|---|---|---|
| Screen says "No network - turn on hotspot or join Wi-Fi" while hotspot is on | OEM hotspot interface name not in `Hotspot.AP_NAME` | `adb shell ip -4 -o addr` with hotspot on; add the name and a test ([Network selection](components/network-selection.md)) |
| **Start** is greyed out | All-files access not granted | Tap the setup button, enable it, return to the app |
| "Folder not found" on Start | Path doesn't exist or no permission | Fix the path or re-pick the folder |
| Laptop resolves `hostlocal.local`, Android VLC doesn't | Android doesn't do mDNS names | Use the IP or DLNA ([Networking](networking.md)) |
| VLC lists the phone's own entry but not on the TV | Multicast not delivered | Try the hotspot; see below |
| FTP connects but listing hangs | Passive ports 50000-50010 blocked | Allow them, or test on the hotspot |
| `FTP error: ... Address already in use` | Port 2121 held by an old process | Stop the app, wait a few seconds, Start again |
| `DLNA error: ...` | Port 8200 or UDP 1900 in use | Check what holds it; another UPnP app can hold 1900 |
| Server stops with the screen off | OEM kills the service | Disable battery optimization; on OxygenOS also allow background activity |
| No thumbnails | Extraction failed for that file | Expected for odd codecs; browsing still works |

## Reading the status block

- `FTP: serving on <ip>` / `mDNS: advertising hostlocal.local` / `DLNA: serving as HostLocal`: each server is up. `waiting for network` means no usable address.
- `SSDP searches seen: N, answered: M (last from IP)`: searches that reached the phone. **0** from other devices means multicast isn't arriving.
- `SSDP packets by sender`: every datagram on UDP 1900 by sender IP. Smart TVs normally announce constantly; if only the phone's own IP appears, the network or the phone's chip is dropping multicast.

## Useful commands

```bash
adb shell dumpsys wifi | grep -i -E "WifiLock|Multicaster"   # are the locks held?
adb shell ss -ltn | grep -E ':(2121|8200)'                   # are servers listening?
adb logcat | grep -E "hostlocal|FATAL"
curl ftp://<phone-ip>:2121/                                  # list the shared folder
curl -r 0-99 -i http://<phone-ip>:8200/media/<id>.mp4        # expect 206
```
