# MapRouletteWeb

MapRouletteWeb is a JOSM plugin aimed at bringing MapRoulette's web-style
challenge workflow into JOSM. The repository starts from the existing
[JOSM MapRoulette plugin](https://github.com/JOSM/MapRoulette) and retains its
GPL license and original authorship.

The plugin implements the focused web-style workflow described below.

## Workflow

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

See [AUTHENTICATION.md](AUTHENTICATION.md) for the API-key format, automatic and
direct setup behavior, startup validation, and task-point attribution.

## Current Behavior

The plugin validates available remembered credentials at startup, remembers the
last successfully loaded challenge ID without automatically reserving it, and
provides a single-task reservation, editing, upload, and five-result completion
flow. The challenge input includes a Clear action for forgetting the saved ID.

## Build

Maven builds are standalone and download the current JOSM snapshot artifacts
from the JOSM repository. The Ant build remains compatible with the shared JOSM
plugins checkout used by JOSM CI.

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
