# HeySir

A Fabric mod for Minecraft Java 26.3. A man in a red shirt and old jeans rides around on a red bike with the seat removed, follows you, and keeps saying "Hey Sir" until you give him a McDonalds Giftcard.

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

## Playing it in the Minecraft Launcher

1. Run the [Fabric installer](https://fabricmc.net/use/installer/): pick **Client**, Minecraft **26.3**, Loader **0.19.5**.
2. In the Minecraft Launcher, open **Installations**, edit the new `fabric-loader-0.19.5-26.3` installation, and set **Game directory** to a new folder such as `~/Library/Application Support/minecraft-heysir`. Your normal `mods` folder holds NeoForge 1.21.1 mods, and they would crash a Fabric profile.
3. Put these in that folder's `mods` directory:
   - [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3 (0.162.0+26.3 or newer)
   - `build/libs/heysir-1.0.0.jar` from this project
4. Launch that installation.

## Development

Needs Java 25. `gradle.properties` points Gradle at Homebrew's `openjdk@25` (`brew install openjdk@25`); change `org.gradle.java.home` if your JDK 25 lives elsewhere.

- `./gradlew runClient` launches a dev copy of the game with the mod.
- `./gradlew build` produces the jar in `build/libs/`.
- `./gradlew runClientGameTest` runs the end-to-end test in [src/gametest](src/gametest). It covers following, the giftcard, the 3-day return, deaths doubling his health, the Nether, and relogging, and it saves screenshots to `build/run/clientGameTest/screenshots/`.

### Art and voices

- `python3 tools/gen_textures.py` regenerates the skin, bike, item icons and mod icon (needs Pillow). `heysir.png` is a standard 64x64 player skin, so you can drop in any classic-arm skin instead.
- `tools/gen_voices.sh` regenerates the placeholder voice lines with macOS text-to-speech. To use real recordings, convert them to **mono** OGG and keep the same names in `src/main/resources/assets/heysir/sounds/`:

  ```sh
  ffmpeg -i recording.m4a -ac 1 -ar 44100 -c:a libvorbis -q:a 5 src/main/resources/assets/heysir/sounds/hey_sir_1.ogg
  ```

  Variants are listed in `src/main/resources/assets/heysir/sounds.json`, so you can add or remove files there.
