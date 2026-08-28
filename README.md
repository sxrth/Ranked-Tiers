# HarbourPVP Ranked — SimpleDuels-style GUI + Kit Editor

Paper 1.21.x plugin. The `/play` menu contains ranked kits and queues. Kit loadouts can be created/edited in-game without editing Java or YAML item lists.

## Kit Editor

1. Give yourself `harbourpvp.admin` (OP has it by default).
2. Put the exact MCPVP-style loadout you want into your own inventory, including armor and offhand.
3. Run:
   `/ht kit edit <kit>`
4. The editor opens. It can display an already-saved loadout.
5. To save the inventory currently shown in the editor:
   `/ht kit save <kit>`
6. To reset that kit to the built-in fallback:
   `/ht kit clear <kit>`

The saved loadout is stored in `plugins/HarbourPVP/config.yml` under `kits.<Kit>.items` and survives restarts.

## Ranked kits

Sword, Axe, Mace, Pot, NethPot, UHC, Crystal, SMP, Vanilla.

Crystal, UHC and SMP have block placement/breaking enabled by default. Other kits are configurable with `allow-block-place` and `allow-block-break`.

## Build

Use the included GitHub Actions workflow (`.github/workflows/build.yml`). The workflow builds the plugin and uploads the JAR as an artifact.
