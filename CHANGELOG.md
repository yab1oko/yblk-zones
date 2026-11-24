# Changelog

## 1.1.0 — Minecraft 1.20.1 Forge

Everything since 1.0.0.

### Editor
- Flags on the left, settings on the right. The flag list scrolls, and the scrollbar can be dragged
- The description stays on the right. A cursor tooltip shows after a short hover, for flags and the other groups
- Deleting a zone asks for confirmation
- Keys: N new zone, G edit, R settings, O owner, C add corner, X remove corner, Left Alt resize

### Wand
- Survival create, reshape, settings, and delete stay off until the config allows them. Operators and creative players can always use the wand
- Operators and creative players can draw past the survival size caps, up to a safety ceiling
- Editing an unowned zone or its command scripts stays with operators and creative players. Claim owners, managers, members, and trusted players keep their plot tools

### Outline
- The bottom edge and the hatch follow the ground. Each block is flat, and a height change is a step up or down

### Claims
- Guests can be set to break, place, use blocks, use entities, deal damage, or take damage. Break, place, and block use start denied
- Trusted players, the owner, and builders still pass
- A claim still blocks containers, buckets, frames, explosions, pistons, enderman grief, fire spread, and dispenser placement

### Adventure
- Break and place follow the block under the crosshair. Standing in a zone does not let you change blocks outside it

### Flags
- Off unless you turn them on: deny PvP, explosions, containers, entry, and teleport
- Operators are not exempt, so the flags can be tested. A claim's members are not pushed out of their own plot

### Commands
- `/zone list`, `/zone wand`, `/zone info`, and a zone name on flag, rename, remove, priority, event, and effect. A bad flag name prints the real list
- `/zone export` and `/zone import` read and write JSON in `config/yblk_zones/export/`. Import creates a new zone id

### Ambience
- The particle picker is a window of the real particle
- Loop sounds follow you in and out
- Radiation is gone

### Other
- Zone lookup uses a chunk index
- Players who are not operators no longer receive script text from the server
- Optional JEI page for the Zone Wand
- Optional KubeJS: `ZoneEnterEvent`, `ZoneExitEvent`, `ZoneRaidStartEvent`, `ZoneRaidEndEvent`

## 1.0.0 — 1.20.1 Forge

First public release (MIT).

- Create and edit world zones with the Zone Wand
- Large set of flags, ambience, HUD, effects, sale, and event settings
- Script system (triggers, actions, text editor, save/import library)
- Event raids: waves, rewards, local sky/weather, mob containment
