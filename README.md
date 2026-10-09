# GNV-Craft

A NeoForge mod for Minecraft Java 26.2 (NeoForge 26.2.0.89, the latest stable release). It adds HeySir: a man in a red shirt and old jeans rides around on a red bike with the seat removed, follows you, and keeps saying "Hey Sir" until you give him a McDonalds Giftcard.

![HeySir riding](docs/heysir-riding.png)

## How he behaves

- **First meeting:** about once per in-game day (on average) he appears 24-40 blocks away in the Overworld and rides toward you. Once he's close and can see you, he starts following.
- **Following:** he stays a few blocks behind you on his bike, sometimes getting off to walk it. If you outrun him he catches up, and he follows you into other dimensions and comes back after you relog.
- **Voice:** mostly "Hey Sir", sometimes "Hey Sir Excuse Me", very rarely "I have a wife and kids". Right-clicking him without a giftcard gets a "Hey Sir Excuse Me".
- **McDonalds Giftcard:** craft it from 8 gold ingots around a cooked beef. Right-click him with it and he rides off, then comes back to find you 3 in-game days later.
- **Killing him:** allowed. He comes back at the start of the next in-game day with double the health: 20, 40, 80, ... with no upper limit.
- Nobody else can ride his bike.

Each player gets their own HeySir. Days are counted on the Overworld clock, so sleeping and `/time add` count.

## Commands (operators)

- `/heysir status` shows his state, death count, health, and when he's coming back.
- `/heysir summon` makes him appear and follow you right away.
- `/heysir reset` makes him forget you.

## Install

GNV-Craft needs **Minecraft Java 26.2** and **NeoForge 26.2.0.89** or newer in the 26.2 line. It does not load on older Minecraft or NeoForge versions such as 1.21.1.

1. Download the NeoForge installer for Minecraft 26.2 from [neoforged.net](https://neoforged.net/) and run it. Choose **Client**.
2. In the Minecraft Launcher, open **Installations**, edit the new NeoForge 26.2 installation, and set **Game directory** to a new folder such as `~/Library/Application Support/minecraft-gnvcraft`. That keeps this profile away from the mods folder of older versions, which would crash 26.2.
3. Get the mod jar:
   - Download `gnvcraft-1.0.0.jar` from the [GitHub releases](https://github.com/rileycleavenger/gnv-craft/releases) page, or
   - Clone this repo and build it: `git clone https://github.com/rileycleavenger/gnv-craft.git && cd gnv-craft && ./gradlew build`. The jar is `build/libs/gnvcraft-1.0.0.jar`. Building needs Java 25 (`brew install openjdk@25` on macOS).
4. Put `gnvcraft-1.0.0.jar` in that game directory's `mods` folder (create it if missing). NeoForge does not need a separate API jar.
5. Launch the NeoForge 26.2 installation.

In game, craft a McDonalds Giftcard with 8 gold ingots around a cooked beef, or use `/heysir summon` if cheats are on.

## Development

Needs Java 25. `gradle.properties` points Gradle at Homebrew's `openjdk@25` (`brew install openjdk@25`); change `org.gradle.java.home` if your JDK 25 lives elsewhere.

- `./gradlew runClient` launches a dev copy of the game with the mod.
- `./gradlew build` produces the jar in `build/libs/`.
- `./gradlew runGameTestServer` runs the gameplay tests in [src/gametest](src/gametest).
- `./gradlew runScreenshotClient` opens a flat world, poses HeySir, and saves screenshots under `run/screenshots/`.

### Art and voices

- `python3 tools/gen_textures.py` regenerates the skin, bike, item icons and mod icon (needs Pillow). `heysir.png` is a standard 64x64 player skin, so you can drop in any classic-arm skin instead.
- `tools/gen_voices.sh` regenerates the placeholder voice lines with macOS text-to-speech. To use real recordings, convert them to **mono** OGG and keep the same names in `src/main/resources/assets/gnvcraft/sounds/`:

  ```sh
  ffmpeg -i recording.m4a -ac 1 -ar 44100 -c:a libvorbis -q:a 5 src/main/resources/assets/gnvcraft/sounds/hey_sir_1.ogg
  ```

  Variants are listed in `src/main/resources/assets/gnvcraft/sounds.json`, so you can add or remove files there.
