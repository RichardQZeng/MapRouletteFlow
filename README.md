# MapRoulette Flow

MapRoulette Flow is a JOSM plugin for a focused, web-style MapRoulette challenge workflow.

MapRoulette Flow began as a fork of the original [JOSM MapRoulette plugin](https://github.com/JOSM/MapRoulette), based on upstream commit `d96c4634fc55ff47267e3844849a85dfa6301051`. The original plugin was authored by Taylor Smock, with additional upstream contributors. Since that starting point, MapRoulette Flow has changed direction toward a one-challenge, one-task workflow and is developed as a separate JOSM plugin maintained by Richard Zeng.

## Workflow

```text
Load challenge by ID or URL
-> Preview one prioritized task
-> Explicitly start and lock the task
-> Download editable OSM data
-> Edit and choose a completion result
-> Confirm comment, tags, review, and next-task mode
-> Gate Fixed completion on a successful OSM upload
-> Submit to MapRoulette and preview the next task
```

The plugin also provides active-task release and re-download, task-aware zoom and selection, account progress and achievements, restart recovery, and editable comments generated from the task's JOSM edits.

## Installation

Published releases use the canonical artifact name `MapRouletteFlow.jar`.

For manual installation, download the JAR from the [latest GitHub release](https://github.com/RichardQZeng/MapRouletteFlow/releases/latest), place it in the `plugins` directory under the JOSM user-data directory, and restart JOSM. Enable **MapRoulette Flow** in **Preferences > Plugins** if it is not already enabled.

After acceptance into JOSM's external plugin list, MapRoulette Flow can be installed and updated directly from **Preferences > Plugins**.

Requirements:

- Java 17 or later.
- JOSM revision 19528 or later. The release build verifies this minimum before publication.
- A MapRoulette API key, either read from the active OSM account preferences or entered directly.

## Coexistence

MapRoulette Flow has its own Java package, plugin identity, preferences, shortcuts, caches, resources, and recovery state. It can be installed and enabled alongside the official MapRoulette plugin.

Only one MapRoulette task workflow should be active at a time. Both plugins use the same MapRoulette account and JOSM upload lifecycle, so operating active tasks in both simultaneously is not supported in the initial release. MapRoulette Flow does not claim global `maproulette.org` Open Location URLs; paste a challenge ID or URL into its panel instead.

MapRoulette protocol values remain shared intentionally, including the default remote OSM preference `maproulette_apikey_v2` and standard `maproulette:*` changeset tags.

## Build

The standalone Maven build downloads JOSM snapshot artifacts from the JOSM repository. The Ant build remains compatible with the shared JOSM plugins checkout used by JOSM CI.

```text
mvn test
mvn spotless:check
mvn verify
```

The publishable Maven artifact is generated at `target/dist/MapRouletteFlow.jar`.

## Documentation

- [Product behavior](PLAN.md)
- [Implementation and release gates](IMPLEMENTATION_PLAN.md)
- [Authentication and credential handling](AUTHENTICATION.md)
- [Authors and upstream provenance](AUTHORS.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

## Support

Report bugs and feature requests at <https://github.com/RichardQZeng/MapRouletteFlow/issues>.

## License

MapRoulette Flow is distributed under GPL-3.0-or-later. See [LICENSE](LICENSE). Inherited and third-party attribution is documented in [AUTHORS.md](AUTHORS.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
