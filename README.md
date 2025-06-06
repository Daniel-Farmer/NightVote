# NightVote Spigot Plugin

NightVote allows players on your Minecraft server to vote to skip the night.

## Features
*   Automatic night votes
*   Manual vote start by admins
*   Simple Y/N chat voting
*   Highly configurable messages, timings, and worlds
*   Permissions support

## Installation
1.  Download the latest `NightVote-X.X.X.jar` from the [SpigotMC Resource Page](LINK_TO_YOUR_SPIGOT_PAGE_HERE).
2.  Place the JAR file into your server's `/plugins` directory.
3.  Restart or reload your server.
4.  Configure `plugins/NightVote/config.yml` as needed and use `/nv reload`.

## Commands
*   `/nv start [world_name]` - Starts a vote.
*   `/nv cancel` - Cancels a vote.
*   `/nv reload` - Reloads config.

## Permissions
*   `nightvote.vote` (default: true) - Allows voting.
*   `nightvote.admin` (default: op) - Access to all admin commands.
*   (List other admin perms)

## Configuration
See the `config.yml` generated by the plugin for all configurable options.

## License
This project is licensed under the MIT License.