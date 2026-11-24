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

Upload these, then paste the forgecdn URLs into the description. Do not publish while an image source is still a comment.

| Shot | What to capture |
|------|-----------------|
| Outline | A zone on uneven ground. The line and the hatch sit on the blocks and step at a height change. Wand in hand, HUD visible |
| Editor | Flags on the left, the description on the right. Claim selected so the guest rules are on screen |
| Polygon | Corners around a building, one face being moved |
| Enter | Walking in: the name appears, and weather, particles, or a loop sound changes |
| Optional | The particle picker, a raid wave, or the buy screen |

## Summary

```
Draw box and polygon regions for adventure maps and servers — claims, buyable plots, ambience, raid waves, and enter/exit scripts, all from a wand editor.
```

## Description

Copy from **Source**, not Preview.

````markdown
<h1 align="center">YBLK Zones</h1>
<p align="center"><b>Draw box and polygon regions for adventure maps and servers.</b></p>

<p align="center">
<code>Minecraft 1.20.1</code> · <code>Forge</code> · <code>Client &amp; Server</code> · <a href="https://github.com/yab1oko/yblk-zones"><code>GitHub</code></a>
</p>

---

## ✨ Overview
YBLK Zones is a wand editor for places on an adventure map or a server. Draw a box or a polygon, then decide what that place does: a claim, a shop plot, a weather pocket, or a raid. It is not a chunk-claim replacement.

## 🧩 Features

### Zone Wand
Mark a box or a polygon, then edit it from the wand. The outline and the hatch follow the ground and step where the height changes. N starts a zone, G edits a nearby one, R opens settings, and Left Alt moves a face. Survival use stays off until the config allows it. Operators and creative players can always use the wand.

### Claims and flags
A claim can allow or deny guests breaking, placing, using blocks, using entities, dealing damage, and taking damage. Other flags cover adventure break and build, mobs, damage, PvP, explosions, containers, entry, and teleport. Adventure break and place follow the block under the crosshair, not every block you can reach.

### Plots, raids, and scripts
Plots and leases are priced in items, with no extra economy mod. Raids have waves and rewards. Enter, exit, and event scripts live in the editor, with a script library. Templates cover a claim, a safe area, a dungeon, and a loot cache.

### Commands
`/zone` lists zones, describes the one you are standing in, and sets flags and events. Export and import use `config/yblk_zones/export/`.

## 🎮 How to use
Take the Zone Wand, mark the shape, and open the editor. Flags are on the left. Settings, sale, and scripts are on the right. Operators can also use `/zone`.

Optional JEI adds a Zone Wand page. Optional KubeJS can listen for `ZoneEnterEvent`, `ZoneExitEvent`, `ZoneRaidStartEvent`, and `ZoneRaidEndEvent`.

<p align="center">
<a href="https://discord.gg/tXMBWwSuRp"><img src="https://media.forgecdn.net/attachments/description/1733109/description_21a9c9c7-989f-4444-ba7f-2e16083187b8.png" alt="Join Discord" width="560"></a>
</p>

## More mods by yab1oko
- [Cold Weapons](https://www.curseforge.com/minecraft/mc-mods/cold-weapons)
- [Custom Loot System (CTS)](https://www.curseforge.com/minecraft/mc-mods/custom-loot-system)
- [YBLK Fashion](https://www.curseforge.com/minecraft/mc-mods/yblk-fashion)
- [YBLK Features](https://www.curseforge.com/minecraft/mc-mods/yblk-features)
- [Roleplay Materials](https://www.curseforge.com/minecraft/mc-mods/roleplay-materials)

## Feedback
Bugs, balance notes, and ideas: join the Discord — [yab1oko mods](https://discord.gg/tXMBWwSuRp).
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
