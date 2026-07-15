# The Book of Wishes

A Minecraft 1.21.1 NeoForge mod where an ancient, powerful entity grants your wishes — for a price.

## What It Does

Write wishes in a magical book. An AI entity reads them, decides whether to grant them, and always demands payment. Every wish has a cost — no wish is ever free.

### Features

- **Custom Book of Wishes item** — Write and submit wishes through a custom GUI
- **AI-powered wish processing** — Uses Ollama (local LLM) to understand and respond to wishes in-character
- **21 action types** — Items, effects, teleportation, entity spawning, world modification, PvP actions, structure discovery, and more
- **Payment system** — The AI demands payment scaled to the wish: items, XP, named items, or resources from nearby players
- **5 AI personalities** — Kind, Greedy, Trickster, Ancient, and Chaotic — each with unique response styles
- **Reputation system** — Trust level rises with granted wishes, falls with denials. Higher trust = better prices
- **Difficulty scaling** — Wishes get more expensive as you make more of them
- **OP/Creative bypass** — Players with operator permissions or in Creative mode always have wishes granted with no payment required
- **Modded item support** — Automatically scans the item registry so the AI knows about items from other mods
- **Configurable structures** — Add or remove structure types via config for modpack compatibility
- **Wish history logging** — All wishes are logged to disk for admin review
- **Visual feedback** — Enchantment particles while the AI thinks, totem particles on grant, smoke on denial, action bar payment notifications

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- Java 21
- [Ollama](https://ollama.com/) running locally (or remote endpoint configured)

## Setup

### 1. Install the Mod

Download `thebookofwishes-1.0.0.jar` from [Releases](https://github.com/THTStreamer/bookofwishes/releases) and place it in your server or client `mods/` folder.

### 2. Set Up Ollama

1. Install [Ollama](https://ollama.com/)
2. Pull a model:
   ```bash
   ollama pull llama3.1:8b
   ```
3. Start Ollama:
   ```bash
   ollama serve
   ```
4. The mod connects to `http://localhost:11434` by default. If your Ollama is on a different host/port, update the config (see below).

### 3. Configuration

On first launch, the mod generates `config/thebookofwishes-common.toml`. Key settings:

```toml
[ollama]
  endpoint = "http://localhost:11434"
  model = "llama3.1:8b"
  temperature = 0.4
  max_tokens = 700

[gameplay]
  cooldown_seconds = 30
  max_wishes_per_book = 10
  enable_payment_system = true

[safety]
  max_destruction_radius = 128
  max_immortality_duration = 600

[structures]
  # Comma-separated: name:tag_path (uses minecraft namespace)
  # Or: name:tag_namespace:tag_path (for modded structures)
  custom_structures = "village:village, stronghold:stronghold, ..."

[mod_compat]
  enable_registry_scanning = true
```

### 4. In-Game Usage

1. Craft the Book of Wishes:
   - Writable Book + Ender Eye + Nether Star
2. Right-click to open the book
3. Click **+** to add a new page
4. Type your wish
5. Click **Grant Wish** to submit
6. The AI processes your wish and writes its response back into the book

### Building from Source

```bash
git clone https://github.com/THTStreamer/bookofwishes.git
cd bookofwishes
./gradlew build
```

The built jar will be in `build/libs/`.

## Wish Types

The AI can grant wishes involving:

| Category | Examples |
|----------|----------|
| Items | "Give me 64 diamonds" |
| Effects | "Grant me immortality for 5 minutes" |
| Teleportation | "Teleport me to the nearest village" |
| Entities | "Spawn 10 withers" |
| World | "Set the weather to thunder" |
| PvP | "Destroy the nearest player's base" |
| Structures | "Find the nearest stronghold" |

## Configuration Reference

| Setting | Default | Description |
|---------|---------|-------------|
| `ollama.endpoint` | `http://localhost:11434` | Ollama API URL |
| `ollama.model` | `llama3.1:8b` | Model to use |
| `gameplay.cooldown_seconds` | `30` | Cooldown between wishes |
| `gameplay.max_wishes_per_book` | `10` | Max wishes per book |
| `gameplay.enable_payment_system` | `true` | Enable payment extraction |
| `safety.max_destruction_radius` | `128` | Max destruction area radius |
| `safety.max_immortality_duration` | `600` | Max immortality in seconds |
| `structures.custom_structures` | _(17 vanilla structures)_ | Comma-separated structure list |
| `mod_compat.enable_registry_scanning` | `true` | Scan modded items for AI |

## License

MIT
