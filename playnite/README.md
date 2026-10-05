# Cloudlight Playnite Library

Playnite **library plugin** that detects GeForce NOW-compatible games in your existing Steam/Epic/GOG/Origin/Ubisoft/Battle.net/Bethesda/Xbox libraries and lets you launch them through [Cloudlight](https://github.com/miirys/OpenNOW), a GeForce NOW client built on OpenNOW.

This extension is modeled after [darklinkpower's NVIDIA GeForce NOW Library](https://github.com/darklinkpower/PlayniteExtensionsCollection) (formerly *GeForce NOW Enabler*), but launches via Cloudlight CLI direct launch instead of the official NVIDIA client.

## Features

- Downloads NVIDIA's live GeForce NOW catalog (GraphQL API) and caches it locally.
- Matches games from other Playnite libraries using store IDs or normalized titles (same strategy as darklinkpower's plugin).
- Adds an **OpenNOW** feature tag to compatible games for filtering (the tag keeps its pre-rename name so existing libraries stay tagged).
- Injects a **Launch via Cloudlight** play action when starting games from other libraries.
- Optional import of owned cloud-compatible titles as separate **Cloudlight** library entries.
- Optional marking of compatible games as installed.
- Database browser to inspect the cached GeForce NOW catalog.
- Launches Cloudlight with:

  ```text
  Cloudlight.exe --launch-app-id=<cmsId> --launch-title="<title>"
  ```

## Requirements

- Windows
- [Playnite](https://playnite.link/) 10+ (Playnite SDK 6.x)
- [.NET Framework 4.6.2 targeting pack](https://dotnet.microsoft.com/download/dotnet-framework/net462)
- [.NET SDK](https://dotnet.microsoft.com/download) (for building)
- [Cloudlight](https://github.com/miirys/OpenNOW/releases) (or a pre-rename OpenNOW build) installed and signed in at least once

## Build

```powershell
# Copy icon once
Copy-Item ..\..\logo.png .\OpenNow.Playnite\icon.png

# Build
cd playnite\OpenNow.Playnite
dotnet build -c Release
```

Output: `playnite/OpenNow.Playnite/bin/Release/`

## Install for development

1. Playnite → **Settings → For developers → External extensions**
2. Add the Release output folder above
3. Restart Playnite
4. Enable the **Cloudlight** library under **Add-ons → Libraries**

## Install packaged extension

Package with [Playnite Toolbox](https://github.com/JosefNemec/PlayniteToolbox):

```powershell
Playnite.Toolbox.exe pack extension "C:\path\to\OpenNOW\playnite\OpenNow.Playnite" "C:\path\to\output"
```

Install the generated `.pext` by opening it or dragging it onto Playnite.

## First-time setup

1. **Add-ons → Extension settings → Cloudlight**
   - Set the Cloudlight executable path if auto-detect fails.
   - Configure startup/library sync behavior.
   - Optionally enable **Import owned cloud-compatible games**.
2. **Main menu → Extensions → Cloudlight → Update Cloudlight-compatible games**
3. Start a game from Steam/Epic/etc. and choose **Launch via Cloudlight**.

## Settings overview

| Setting | Description |
| --- | --- |
| Cloudlight executable path | Override auto-detect (`%LocalAppData%\Cloudlight\bin\Cloudlight.exe`, pre-rename `OpenNOW` folders, etc.) |
| Import as library entries | Creates separate Cloudlight-library games for owned compatible titles |
| Update on startup / library update | Keeps features and cache in sync automatically |
| Show launch action | Adds Cloudlight as a launch option for matched games in other libraries |
| Only for not locally installed | Hides the Cloudlight action when the source library reports an install directory |
| Mark compatible as installed | Sets `IsInstalled = true` on matched games during sync |

## Matching notes

- **Steam / GOG / Ubisoft / Battle.net / Bethesda / Rockstar**: matched by store/game ID.
- **Epic / EA app / Xbox**: matched by normalized game title (store IDs often differ from Playnite).
- Name normalization removes punctuation/edition noise similar to the reference plugin.

If a game is on GeForce NOW but not detected, use **Open GeForce NOW database browser** to verify the catalog title/store ID, then adjust the Playnite game name if needed.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| Cloudlight not found | Set executable path in extension settings or install Cloudlight |
| No launch action shown | Run manual sync; confirm the game has the **OpenNOW** feature |
| Wrong game launches | Check title/store match in database browser; rename game or report a matching issue |
| Play time is approximate | Cloudlight single-instance behavior limits precise process tracking |

## Project layout

```text
playnite/OpenNow.Playnite/
  OpenNowLibraryPlugin.cs      # Library plugin + sync/import/play actions
  OpenNowLibraryClient.cs      # Library client (open Cloudlight)
  OpenNowPlayController.cs       # Launch + play-time tracking
  Services/GeforceNowService.cs  # NVIDIA GraphQL catalog fetch
  Services/GameDetectionService.cs
  Views/DatabaseBrowserView.*    # Catalog inspector
  Localization/en_US.xaml
```

## Credits

- Matching/sync design inspired by **[darklinkpower](https://github.com/darklinkpower)**'s [NVIDIA GeForce NOW Library](https://github.com/darklinkpower/PlayniteExtensionsCollection) for Playnite.
- Launch path uses Cloudlight direct launch (`--launch-title`, `--launch-app-id`).

## License

Same license as the OpenNOW repository.
