# HarbourPVP 1.0.6

Paper 1.21.x ranked PvP plugin.

## Kits
Sword, Axe, Mace, Pot, NethPot, SMP, UHC, Vanilla, Spear.
Crystal is removed.

GUI icons:
- Sword: Diamond Sword
- Axe: Diamond Axe
- Mace: Mace
- Pot: Potion
- NethPot: Netherite Sword
- SMP: Shield
- UHC: Golden Apple
- Vanilla: End Crystal
- Spear: Trident fallback for APIs without a Netherite Spear material

## Ranked
Each kit has independent rating and placement progress. New players start **Unranked** on every kit. The first 5 ranked matches for each kit are placements; after 5, the kit receives its tier based on rating.

## Arena commands
/ht arena create <arena>
/ht arena set <arena> 1
/ht arena set <arena> 2
/ht arena delete <arena>
/ht arena list

## Build
GitHub Actions provisions Gradle 8.10.2 and Java 21 automatically. No local Gradle installation is required.
