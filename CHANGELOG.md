# Changelog

## 1.1.0 — 1.20.1 Forge

- Editing unowned zones and command scripts is limited to operators and creative players. Claim owners, managers, members, and trusted players keep their plot tools.
- Operators and creative players can draw past the survival size caps, up to a safety ceiling.
- Claimed plots block containers, buckets, frames, explosions, pistons, enderman grief, fire spread, and dispenser placement. Trusted players can still build.
- Optional flags, off unless you turn them on: deny PvP, explosions, containers, entry, and teleport.
- `/zone list`, `/zone wand`, `/zone info`, and name arguments for flag, rename, remove, priority, event, and effect. A bad flag name prints the real list.
- Export and import zones as JSON in `config/yblk_zones/export/`. Import creates a new zone id.
- Zone lookup uses a chunk index, so many zones and mobs do not scan every zone every tick.
- Non-operators no longer receive script text in the zone sync.
- Radiation is gone. The particle picker shows a world preview. Loop sounds follow you in and out. New zones use the configured proximity radius.
- Optional JEI page for the Zone Wand. Optional KubeJS can listen to Forge events `ZoneEnterEvent`, `ZoneExitEvent`, `ZoneRaidStartEvent`, and `ZoneRaidEndEvent`.

## 1.0.0 — 1.20.1 Forge

First public release (MIT).

- Create and edit world zones with the Zone Wand
- Large set of flags, ambience, HUD, effects, sale, and event settings
- Script system (triggers, actions, text editor, save/import library)
- Event raids: waves, rewards, local sky/weather, mob containment
