# AsyncMOTD

Server list MOTD for Paper/Folia and Velocity. One jar, drop it on the backend, the proxy, or both.

## Features

- MOTD lines, hover text, version text and server icon, grouped into presets
- Switch and edit presets in game with `/motd`, no restart needed
- Lines are centered with a configurable character width table
- Hex colors and gradients: `&#00d3ff`, `<gradient:#00d3ff:#ffffff>text</gradient>`
- `{online}` and `{max}` placeholders in every field
- Behind Velocity the proxy answers the ping and the backend menu edits its presets
- Folia supported, all work runs on the player or async scheduler

## Requirements

- Paper or Folia 1.21.11 or newer, Java 21 or newer
- Velocity 4.2 or newer (optional)

## Install

**Single server:** put `AsyncMOTD.jar` into `plugins/` and restart. `/motd` opens the menu.

**Behind Velocity:** the jar goes on the proxy and on the backend you edit from.

1. In the backend's `plugins/AsyncMOTD/config.yml` set `proxy-sync.enabled: true`.
2. Set the same `proxy-sync.secret` there and in the proxy's `plugins/asyncmotd/config.yml`. Pick any long random text.
   Sync stays off while the secret is empty, so nobody else can send presets on the channel.
3. Install and configure the proxy first. If the backend sync is on while the proxy jar is missing or uses another
   channel, the backend message, including the secret, is passed on to the player's client.
4. Restart both. The proxy now owns the presets. Edits made in the backend menu are sent to it, and the proxy sends its
   presets to every backend a player joins. `/vmotd` reloads the proxy files after you edit them by hand.

## Commands

| Command | What it does |
|---|---|
| `/motd` | open the preset menu |
| `/motd list` | list all presets |
| `/motd create\|delete\|activate <name>` | manage presets |
| `/motd line1\|line2\|version <name> <text>` | edit a text field |
| `/motd icon <name> <file\|none>` | set or clear the server icon |
| `/motd hover <name> add <text>\|remove <number>\|clear` | edit the hover lines |
| `/motd reload` | reload all files |
| `/vmotd` | reload the presets on Velocity |

Permission for everything: `asyncmotd.admin` (op by default).

## Configuration

- `config.yml`: ping options, gradient, centering, icons, chat input and placeholders
- `presets.yml`: the presets and the active one, written by the menu and the commands
- `messages/motd.yml`: every message, each with optional actionbar, title and sound
- `gui/presets.yml`, `gui/preset-edit.yml`: the two menus
- Server icons: 64x64 PNG files in `plugins/AsyncMOTD/icons/` (on Velocity `plugins/asyncmotd/icons/`)

Hover and version text cannot show hex colors in the client, so they are converted to the closest named color.
`ping.legacy-overrides` maps a hex color to a fixed named color. The version text is only shown to players whose
client version does not match, unless `version.force-display` is on.

## Known limitations

- Sync uses plugin messages, which need a player online on the backend. Edits made while nobody is online reach the
  proxy when the next player joins.
- Presets sync as one file and the newest edit wins. Two admins editing different presets at the same moment on two
  backends can overwrite each other.
- Presets larger than about 32 KB are not synced in either direction, a warning is logged.
- Centering understands `&#rrggbb`, not the `&x&r&r&g&g&b&b` form.

## Build

```
mvn package
```

Needs JDK 25, because the Velocity API is compiled for Java 25. The plugin itself targets Java 21.

## License

MIT, see `LICENSE`.
