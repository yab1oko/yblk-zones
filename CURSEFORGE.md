# CurseForge — YBLK Zones

**GitHub:** https://github.com/yab1oko/yblk-zones

Paste the summary into the project summary. Paste the description from the source block below. Upload images first, then replace each `<!-- replace with forgecdn URL -->` with the forgecdn URL. Do not publish the HTML while those comments are still the image sources.

## Categories (CurseForge)

| Priority | Category |
|----------|----------|
| **1 — Primary** | **Server Utility** |
| **2** | **Map and Information** |
| **3** | **Utility & QoL** |
| Optional | **Adventure and RPG** |

Recommended set: Server Utility (primary) · Map and Information · Utility & QoL

## Screenshot and GIF checklist

Replace the three superflat shots in `docs/screenshots/` with the same scenes in a real build, then add the GIFs. Upload all of them on CurseForge and paste the URLs into the description.

| File | Alt text |
|------|----------|
| `docs/screenshots/01_editor.png` | Zone editor with flags and settings |
| `docs/screenshots/02_wand_hud.png` | Zone Wand and the HUD over a zone |
| `docs/screenshots/03_polygon_edit.png` | Polygon points being edited in a build |
| `docs/screenshots/04_draw_polygon.gif` | Drawing a polygon and opening the editor |
| `docs/screenshots/05_enter_ambience.gif` | Walking in as the HUD, weather, and particles change |
| `docs/screenshots/06_raid_or_plot.gif` | Starting a raid or buying a plot |

## Summary

```
Draw box and polygon regions for adventure maps and servers — claims, buyable plots, ambience, raid waves, and enter/exit scripts, all from a wand editor.
```

## Description

Copy from **Source**, not Preview.

````markdown
<h1 align="center">YBLK Zones</h1>
<p align="center"><b>Draw box and polygon regions for adventure maps and servers — claims, buyable plots, ambience, raid waves, and enter/exit scripts, all from a wand editor.</b></p>

<p align="center">
<code>Minecraft 1.20.1</code> · <code>Forge</code> · <code>Client & Server</code> · <code><a href="https://github.com/yab1oko/yblk-zones">GitHub</a></code>
</p>

---

## Overview
YBLK Zones is an adventure-map and event region director. Draw a box or a polygon, then decide what that place does: a claim, a shop plot, a weather pocket, or a raid. It is not a chunk-claim replacement.

## Features
- Zone Wand for boxes and polygons, with a two-panel editor
- Flags for claim, adventure build and break, mob wall and spawn, damage, PvP, explosions, containers, deny entry, and deny teleport
- HUD, sky, weather, particles, and effects
- Item-priced plots and leases, with no extra economy mod
- Raid waves and rewards
- Enter, exit, and event scripts, plus a script library
- Templates: claim, safe, dungeon, loot cache
- `/zone` commands for list, info, flags, and events
- Export and import under `config/yblk_zones/export/`

<p align="center">
<img src="<!-- replace with forgecdn URL -->" alt="Zone editor with flags and settings" width="560">
</p>

<p align="center">
<img src="<!-- replace with forgecdn URL -->" alt="Zone Wand and the HUD over a zone" width="560">
</p>

<p align="center">
<img src="<!-- replace with forgecdn URL -->" alt="Polygon points being edited in a build" width="560">
</p>

## How to use
Take the Zone Wand, mark the shape, and open the editor. Flags are on the left. Settings, sale, and scripts are on the right. Operators can also use `/zone`.

<p align="center">
<a href="https://discord.gg/tXMBWwSuRp"><img src="https://media.forgecdn.net/attachments/description/1733109/description_21a9c9c7-989f-4444-ba7f-2e16083187b8.png" alt="Join Discord" width="535"></a>
</p>

## Dependencies
Required: Minecraft 1.20.1 and Forge 47.x. Optional: JEI for a Zone Wand info page, and KubeJS if you want to listen to the zone events. No required extra mods.

## License
MIT
````

## Dependencies (upload file)

| | |
|---|---|
| Game version | **1.20.1** |
| Mod loader | **Forge** |
| Required mods | none |
| Optional | JEI, KubeJS |

## Upload

1. **Name:** YBLK Zones · **Slug:** `yblk-zones`
2. **License:** MIT
3. **Icon:** `src/main/resources/logo.png`
4. Upload the screenshots and GIFs listed above, then paste their URLs into the description
5. **File:** `build/libs/yblk_zones-1.20.1-1.1.0.jar`
