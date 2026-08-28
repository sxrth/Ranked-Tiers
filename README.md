# HarbourPVP Ranked

A Paper 1.21.8 ranked PvP plugin project for HarbourPVP.

## Requirements
- Paper 1.21.8 or compatible 1.21.x server
- Java 21
- Gradle 8.x or newer

## Build
Windows:
1. Install Java 21 and Gradle.
2. Open a terminal in this folder.
3. Run `gradle build`.
4. The plugin jar will be in `build/libs/HarbourPVP-1.0.0.jar`.
5. Copy it to your server's `plugins` folder.

A `build.bat` file is included for convenience.

## First setup
1. Start the server once so `plugins/HarbourPVP/config.yml` is created.
2. Edit the `position1` and `position2` values for each kit. They must be in the form:
   `world,x,y,z,yaw,pitch`
3. Restart the server.

## Player commands
- `/play` - lists kits
- `/play <kit>` - join/leave-style matchmaking for that kit; a second player starts a match
- `/stats [player]`
- `/leaderboard [kit]`
- `/history`
- `/queue`

## Admin commands
Permission: `harbourpvp.admin`
- `/ht setrating <player> <kit> <rating>`
- `/ht settier <player> <kit> <tier>`
- `/ht reset <player> <kit>`
- `/ht forcematch <player1> <player2> <kit>`
- `/ht reload`

## Important
This project intentionally has no Discord dependency. Player ratings and match history are stored in `plugins/HarbourPVP/data.yml`.

The included match kits are a basic functional starting point. For a production PvPHQ-style server, customize each kit's exact inventory, armor, potion effects, arena reset, spectators, ELO/MMR formula, and anti-abuse rules.
