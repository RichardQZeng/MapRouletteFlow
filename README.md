# MapRouletteWeb

MapRouletteWeb is a JOSM plugin aimed at bringing MapRoulette's web-style
challenge workflow into JOSM. The repository starts from the existing
[JOSM MapRoulette plugin](https://github.com/JOSM/MapRoulette) and retains its
GPL license and original authorship.

The initial code is a clean baseline fork. The web-style workflow described
below is planned work, not yet implemented.

## Target Workflow

```text
Load challenge by ID or URL
-> Preview prioritized tasks
-> Explicitly start and lock a task
-> Download editable OSM data
-> Edit and choose a completion result
-> Confirm comment, tags, review, and next-task mode
-> Gate Fixed completion on successful OSM upload
-> Submit to MapRoulette and preview the next task
```

See [PLAN.md](PLAN.md) for the agreed product behavior and scope.

See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for engineering milestones,
acceptance criteria, and verification.

See [AUTHENTICATION.md](AUTHENTICATION.md) for the confirmed API-key format,
current baseline behavior, planned setup UI, and task-point attribution.

## Current Baseline

The inherited plugin can download MapRoulette tasks, show them in a custom
layer, lock tasks, display instructions, apply supported cooperative changes,
and submit completion data through its existing upload/stop-task flow.

The baseline does not yet automatically download editable OSM data when a task
starts, provide the five-button web completion flow, or safely wait for an
actual successful OSM upload before submitting a Fixed result.

## Build

The project follows the JOSM plugin repository layout and expects the shared
JOSM plugin build files in the parent directory.

For local builds, place this repository in a JOSM plugins checkout where the
parent `pom.xml`, `build-common.xml`, and `00_core_tools` directory are
available. A standalone clone without those files cannot resolve the
`plugin-root` Maven parent or Ant imports.

```text
mvn test
mvn spotless:check
mvn verify
```

## Package Compatibility

The Java package and entry class intentionally remain
`org.openstreetmap.josm.plugins.maproulette.MapRoulette` during the initial
fork. This keeps the first changes small while the external project and plugin
artifact are named `MapRouletteWeb`.

Do not enable the original MapRoulette plugin and MapRouletteWeb at the same
time during this compatibility phase; they register overlapping UI and upload
integrations and share preference keys.

## License

GPLv3 or any later version. See [LICENSE](LICENSE).
