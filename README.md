# Hotbar Satchels

A Fabric mod for Minecraft 26.3 that adds equippable satchels providing extra hotbar storage.

![Minecraft 26.1.X](https://img.shields.io/badge/Minecraft-26.3-green)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow)

---

## Features

- **Three tiers of satchels** — Golden (3 slots), Diamond (6 slots), Netherite (9 slots).
- Satchel contents are displayed **above the hotbar** and can be swapped with the first hotbar slots using a keybind (default `V`).
- Works seamlessly inside **vanilla and modded container GUIs**.
- Integrates with **[Ohmega](https://modrinth.com/mod/ohmega)** (accessory slot).
- Satchels are **dyeable** — apply any dye in a crafting table.
- Configurable via an in-game config screen (requires [Mod Menu](https://modrinth.com/mod/modmenu)).

---

## Crafting

| Tier | Recipe |
|---|---|
| **Golden Satchel** | Strings + Leather + Gold Ingot (shaped) |
| **Diamond Satchel** | Golden Satchel + 4 Diamonds |
| **Netherite Satchel** | Diamond Satchel + Netherite Ingot (smithing table) |

---

## Optional Integrations

| Mod | Notes |
|---|---|
| [Ohmega](https://modrinth.com/mod/ohmega) | Equip satchels in the dedicated accessory slot |
| [Trinkets Updated](https://modrinth.com/mod/trinkets-updated) | Supported, but currently disabled pending an upstream bugfix|
| [Raised](https://modrinth.com/mod/raised) | Hotbar overlay adjusts correctly with the raised hotbar |
| [JEI](https://modrinth.com/mod/jei) | Recipes shown in JEI |
| [Flashback](https://modrinth.com/mod/flashback) | Satchel state renders correctly in replays |
| [Mod Menu](https://modrinth.com/mod/modmenu) | In-game config screen |

All integrations are optional — the mod works without any of them.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Drop the mod jar into your `mods` folder.
3. Launch the game.

---

## Building from Source

```bash
./gradlew build
```

The output jar will be in `build/libs/`.

To regenerate data files:

```bash
./gradlew runDatagen
```

---

## Credits

- **Winord** — Fabric port and further development
- **[Vercte](https://github.com/vercte/satchels)** — original NeoForge mod

---

## License

[MIT](LICENSE)
