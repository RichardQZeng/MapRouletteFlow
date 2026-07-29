# MapRoulette Flow

[![CI](https://github.com/RichardQZeng/MapRouletteFlow/actions/workflows/ci.yml/badge.svg)](https://github.com/RichardQZeng/MapRouletteFlow/actions/workflows/ci.yml)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)

MapRoulette Flow is a substantially redesigned, independently published fork of the official [JOSM MapRoulette plugin](https://github.com/JOSM/MapRoulette). It retains and credits the upstream foundation while introducing a focused one-challenge, one-task workflow with explicit state management, secure authentication, upload-safe completion, restart recovery, comprehensive tests, and standalone release automation.

The project is based on upstream commit [`d96c4634fc55ff47267e3844849a85dfa6301051`](https://github.com/JOSM/MapRoulette/commit/d96c4634fc55ff47267e3844849a85dfa6301051). The original plugin was authored and maintained by [Taylor Smock](https://github.com/tsmock), with contributions from Dirk Stocker, Kirill B., and other JOSM and MapRoulette contributors recorded in the upstream history. See [AUTHORS.md](AUTHORS.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for detailed attribution.

## Why MapRoulette Flow?

The official plugin provides broad MapRoulette integration inside JOSM. MapRoulette Flow explores a different product direction modeled on the focused workflow of the MapRoulette web application:

```text
Authenticate
-> Enter a challenge ID/URL or a specific task ID/task URL
-> Reserve one Random, Nearby, or explicitly selected task
-> Preview its geometry before downloading editable data
-> Confirm Start & Download
-> Edit current OSM data in JOSM
-> Choose and review a completion result
-> Gate Fixed completion on a successful matching OSM upload
-> Commit the MapRoulette result safely
-> Reserve and preview the next task
```

This is a complementary workflow rather than a replacement for the official plugin. It emphasizes one visible unit of work, explicit confirmation points, recoverable failures, and consistency between MapRoulette task state and JOSM upload state.

## Divergence from Upstream

MapRoulette Flow has its own Java package, plugin identity, entry point, preferences, shortcuts, caches, resources, workflow state, build, and release process. It can be installed alongside the official plugin.

### New or substantially redesigned

- A central finite-state workflow controller with guarded transitions and explicit cleanup ownership
- One-task challenge reservation with Random, Nearby, and direct task-ID or task-URL selection
- Reserved geometry preview before editable OSM data is downloaded
- Geometry-aware OSM download bounds, edit-layer selection, re-download, and task-feature selection
- Direct and OSM-preference authentication modes with account validation and credential scoping
- Upload-gated Fixed completion correlated with the expected JOSM changeset and edit layer
- Commit-aware retry behavior that never resubmits task status solely to retry a comment or changeset association
- Credential-free completion drafts and account-bound restart recovery
- Task-scoped edit tracking with editable completion-comment generation
- Reservation lock refresh, exclusion handling, cancellation, shutdown cleanup, and stale-callback guards
- Completion dialogs for comments, MapRoulette tags, review requests, instructions, and challenge-defined responses
- Standalone Maven packaging, JOSM-compatible Ant builds, CI checks, and tag-driven release automation

### Inherited and retained foundation

- JOSM plugin, layer, geometry, command, upload, and preference integration patterns
- MapRoulette API models, parsers, protocol values, geometry handling, and cooperative-task foundations
- Selected utilities, user-interface components, tests, and artwork with their original attribution and licenses

Mechanically relocated inherited files retain upstream attribution. Classes created for MapRoulette Flow or substantively changed for its separate workflow identify Richard Zeng in their class documentation. Raw line-change totals are not used as an authorship measure because package relocation, removed upstream functionality, and bundled resources would inflate those numbers.

## User-Facing Capabilities

- Authenticate directly or retrieve the MapRoulette key associated with the active OSM account
- Validate the connected MapRoulette and OSM identities before task-changing actions are enabled
- Load a challenge or exact task from a numeric ID or supported MapRoulette URL
- Reserve and preview exactly one task, with a ten-minute lock refresh
- Download current OSM data only after explicit confirmation
- Re-download into the retained edit layer without discarding local edits
- Select and center referenced OSM nodes, ways, and relations
- Apply supported cooperative tag changes through undoable JOSM commands
- Complete tasks as Fixed, Not an Issue, Skip, Already Fixed, or Can't Complete
- Review generated comments, Markdown instructions, tags, completion responses, and review requests
- Continue with a Random or Nearby task without downloading it prematurely
- Recover eligible completion work after restarting and authenticating the same accounts

## Reliability and Security

MapRoulette Flow treats remote task state, local edits, and OSM uploads as one coordinated workflow:

- **Explicit state ownership:** one controller owns the challenge, reservation, active task, edit layer, completion draft, and listener lifecycle.
- **Upload-safe completion:** Fixed is not submitted until the expected OSM upload succeeds and the captured edit layer no longer requires upload.
- **Commit-aware retries:** MapRoulette status submission is the commit point; failed auxiliary operations can be retried without duplicating the status update.
- **Lock hygiene:** reservations are refreshed, excluded or rejected candidates are released, and shutdown attempts to release owned locks.
- **Concurrency discipline:** network and download work stays off Swing's event dispatch thread, while UI-visible state changes return to it.
- **Credential handling:** API keys are never placed in URLs, remain session-only by default, are scoped to the server and account, and are excluded from recovery snapshots and logs.
- **Recovery:** cancellation, temporary failures, upload interruption, and restart preserve enough non-secret context for safe retry or release.

The current automated suite contains 169 tests across 43 test classes. It covers workflow transitions, reservation and release, authentication, API contracts, parsing, geometry and download bounds, Swing-thread ownership, upload correlation, completion commit semantics, retries, cooperative changes, and restart recovery. CI runs the Maven verification and formatting checks and audits the packaged plugin identity and resources.

## Project Status

MapRoulette Flow is currently a pre-release project. The build and tag-driven release workflows are implemented, but no public GitHub release has been published yet. Acceptance into JOSM's external plugin list is also future work.

Until the first release, build the plugin locally and install the generated JAR manually.

## Requirements

- Java 17 or later
- JOSM revision 19528 or later
- A MapRoulette API key entered directly or available through the active OSM account's preferences
- Maven 3.9 or later for the standalone build

## Build and Install

```text
mvn test
mvn spotless:check
mvn verify
```

The publishable artifact is generated at:

```text
target/dist/MapRouletteFlow.jar
```

Copy the JAR into the `plugins` directory under the JOSM user-data directory, restart JOSM, and enable **MapRoulette Flow** under **Preferences > Plugins**.

The Ant build remains compatible with the shared JOSM plugins checkout used by JOSM CI.

## Coexistence with the Official Plugin

MapRoulette Flow and the official MapRoulette plugin use separate package names, plugin identities, preferences, caches, and resources, so both can be installed and enabled in the same JOSM profile.

Only one active MapRoulette task workflow should be used at a time. Both plugins operate against the same MapRoulette account and JOSM upload lifecycle, so simultaneous active tasks are not supported. MapRoulette Flow does not claim global `maproulette.org` Open Location URLs; paste a challenge ID, task ID, or URL into its panel instead.

Shared protocol values remain compatible where required, including the default `maproulette_apikey_v2` OSM preference and standard `maproulette:*` changeset tags.

## Documentation

- [Product behavior and workflow](PLAN.md)
- [Implementation milestones and release gates](IMPLEMENTATION_PLAN.md)
- [Authentication and credential handling](AUTHENTICATION.md)
- [Authors and upstream provenance](AUTHORS.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

## Support

Report bugs and feature requests through [GitHub Issues](https://github.com/RichardQZeng/MapRouletteFlow/issues).

For issues with the official JOSM MapRoulette plugin, use the upstream project's support channels at [JOSM/MapRoulette](https://github.com/JOSM/MapRoulette).

## License

MapRoulette Flow is distributed under GPL-3.0-or-later, consistent with the inherited JOSM MapRoulette source. See [LICENSE](LICENSE). Third-party licenses and notices are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and packaged with applicable resources.
