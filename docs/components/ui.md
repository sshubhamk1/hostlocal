# UI and settings

Source: `MainActivity.kt`, `Settings.kt`, `FolderPaths.kt`

## MainActivity

A single Compose screen. It owns no server logic; it only starts and stops `HostService` and displays state.

- A `LaunchedEffect` loop runs every 2 seconds. It re-reads `Hotspot.currentAddress()` and the static status fields from `HostService` and `SsdpStats` into Compose state. A `tick` counter forces recomposition for non-state reads.
- **Setup checklist** appears while a permission is missing: all-files access, notifications (API 33+), battery-optimization exemption. Each item disappears once granted; `onResume` re-checks.
- **Start** is disabled until all-files access is granted.
- The folder field is editable only while stopped.
- The status block shows each server's bound address or error, plus SSDP counters.

## Folder picker

`ActivityResultContracts.OpenDocumentTree` returns a SAF tree URI. `treeUriToPath` converts it to a path (`primary:Movies` → `/storage/emulated/0/Movies`, other volumes → `/storage/<id>/...`). This only works because the app has all-files access; see [decisions](../decisions.md#storage-all-files-access). Non-primary volumes haven't been tested.

## Settings

`SharedPreferences` file `hostlocal`:

| Key | Type | Meaning |
|---|---|---|
| `folder` | String | Shared folder path (default `/storage/emulated/0/Movies`) |
| `dlna_uuid` | String | Generated once; DLNA device id |

Constants: `FTP_PORT = 2121`, `PASSIVE_PORTS = "50000-50010"`, `DLNA_PORT = 8200`, `DLNA_NAME = "HostLocal"`.

## Adding a status line

1. Expose the value as a `@Volatile` field (see [Service](service.md)).
2. Copy it into a `remember { mutableStateOf(...) }` inside the `LaunchedEffect` loop.
3. Render it in the `if (running)` block.
