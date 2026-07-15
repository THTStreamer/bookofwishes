# The Book of Wishes

A Minecraft 1.21.1 NeoForge mod where an ancient, powerful entity grants your wishes — for a price.

## What It Does

Write wishes in a magical book. An AI entity reads them, decides whether to grant them, and always demands payment. Every wish has a cost — no wish is ever free.

### Features

- **Custom Book of Wishes item** — Write and submit wishes through a custom GUI
- **Embedded AI** — Runs a local LLM inside the mod via llama.cpp. No external services required
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
- A GGUF model file (e.g., from Hugging Face)

## Setup

### 1. Install the Mod

Download `thebookofwishes-1.0.0.jar` from [Releases](https://github.com/THTStreamer/bookofwishes/releases) and place it in your server or client `mods/` folder.

### 2. Get a Model

Download a GGUF model file. Recommended options:

| Model | Size | RAM Required | Quality |
|-------|------|--------------|---------|
| Qwen3-1.7B-Q4_K_M | ~1.1 GB | ~2 GB | Good |
| Llama-3.2-3B-Q4_K_M | ~2 GB | ~3 GB | Better |
| Gemma-3-1B-Q4_K_M | ~0.7 GB | ~1.5 GB | Acceptable |

Place the `.gguf` file anywhere accessible (e.g., `config/models/`).

### 3. Configuration

On first launch, the mod generates `config/thebookofwishes-common.toml`. Key settings:

```toml
[ollama]
  # AI backend: "embedded" or "ollama"
  llm_provider = "embedded"

  # Ollama settings (only used when llm_provider = "ollama")
  endpoint = "http://localhost:11434"
  model = "llama3.1:8b"
  temperature = 0.4
  max_tokens = 700

[embedded]
  # Path to your GGUF model file
  model_path = "config/models/my-model.gguf"
  context_size = 2048
  gpu_layers = 0
  threads = 4

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

## AI Backend Options

### Embedded (Recommended)

Uses [java-llama.cpp](https://github.com/kherud/java-llama.cpp) to run a GGUF model directly inside the mod. No external services needed.

- Set `llm_provider = "embedded"` in config
- Set `model_path` to your GGUF file
- Model loads automatically on first wish (takes a few seconds)
- Runs entirely in-process — no network latency

### Ollama (Legacy)

Connects to an external [Ollama](https://ollama.com/) instance. Requires Ollama installed and running.

- Set `llm_provider = "ollama"` in config
- Set `endpoint` to your Ollama URL
- Set `model` to your Ollama model name

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
| `ollama.llm_provider` | `"ollama"` | AI backend (`embedded` or `ollama`) |
| `embedded.model_path` | `""` | Path to GGUF model file |
| `embedded.context_size` | `2048` | Context window size |
| `embedded.gpu_layers` | `0` | Layers to offload to GPU (0 = CPU only) |
| `embedded.threads` | `4` | CPU threads for inference |
| `ollama.endpoint` | `http://localhost:11434` | Ollama API URL (legacy) |
| `ollama.model` | `llama3.1:8b` | Model to use (legacy) |
| `gameplay.cooldown_seconds` | `30` | Cooldown between wishes |
| `gameplay.max_wishes_per_book` | `10` | Max wishes per book |
| `gameplay.enable_payment_system` | `true` | Enable payment extraction |
| `safety.max_destruction_radius` | `128` | Max destruction area radius |
| `safety.max_immortality_duration` | `600` | Max immortality in seconds |
| `structures.custom_structures` | _(17 vanilla structures)_ | Comma-separated structure list |
| `mod_compat.enable_registry_scanning` | `true` | Scan modded items for AI |

## License

MIT
