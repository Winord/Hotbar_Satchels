# Hotbar Satchels

A Fabric mod for Minecraft that adds equippable satchels providing extra hotbar storage.

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-green)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow)

---

## Features

- **Three tiers of satchels** — Golden (3 slots), Diamond (6 slots), Netherite (9 slots).
- Satchel contents are displayed **above the hotbar** and can be swapped with the first hotbar slots using a keybind (default `V`).
- Works seamlessly inside **vanilla and modded container GUIs**.
- Integrates with **[Accessories](https://modrinth.com/mod/accessories)** (equip slot: Chest group).
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
| [Accessories](https://modrinth.com/mod/accessories) | Equip satchels in the dedicated slot |
| [Raised](https://modrinth.com/mod/raised) | Hotbar overlay adjusts correctly with the raised hotbar |
| [JEI](https://modrinth.com/mod/jei) | Recipes shown in JEI |
| [EMI](https://modrinth.com/mod/emi) | Recipes shown in EMI |
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
