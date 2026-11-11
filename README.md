# Village Heart

Village Heart is a Fabric mod for Minecraft. Craft a Village Heart, place it by a village bell, and that one village becomes yours. Hire villagers, mark their workplaces, share a stockpile, and grow a Hamlet into a Town. Think of a small settlement manager inside a vanilla village.

How to play is in the [player guide](docs/PLAYER_GUIDE.md): claiming, ranks, buildings, jobs, food and wages, tiers, the market, the builder, guards, gates, and lamps. Sieges, random raids, wall damage, the smith, and population growth are in v1, being finished.

## Requirements

To play:

- Minecraft **1.21.11**
- Fabric Loader **0.20.1** or newer
- Fabric API for 1.21.11 (this project is built against **0.141.6+1.21.11**)
- Java **21** or newer

## Install

1. Install Minecraft 1.21.11.
2. Install Fabric Loader for that version, and add Fabric API to your mods folder.
3. Put the Village Heart jar in your mods folder (`.minecraft/mods`, or the mods folder of the instance). Use the jar whose name does not end in `-sources`.
4. Launch the game with the Fabric profile. The Village Heart recipe appears in the crafting book once you have the ingredients. From there, follow the [player guide](docs/PLAYER_GUIDE.md).

If you built the mod yourself, the jar is `build/libs/village-heart-<version>.jar` (the version comes from `gradle.properties`; the release gate sets it to 1.0.0).

## Build from source

Gradle and Loom need **Java 25**. The mod still targets Java 21. `scripts/verify.sh` installs Mojang's Java 25 runtime to `~/.jdk25` if it is missing, then builds and runs the checks.

```bash
python3 scripts/install-java25.py
export JAVA_HOME=~/.jdk25
scripts/verify.sh
```

To compile and pack the jar without tests:

```bash
export JAVA_HOME=~/.jdk25
./gradlew --no-daemon build -x test
```

In cloud or CI sessions, keep `--no-daemon` on every Gradle call.

## License

[CC0 1.0](LICENSE).
