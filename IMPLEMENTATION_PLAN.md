# MapRoulette Flow Implementation Plan

This document implements the behavior defined in [PLAN.md](PLAN.md). Research
findings are retained separately and are not the authoritative product
specification.

## Delivery Strategy

- Build one vertical workflow slice at a time.
- Keep all network work off Swing's event dispatch thread.
- Centralize workflow state before adding more UI actions.
- Treat MapRoulette status submission as a commit point.
- Add tests in every milestone.
- Do not add compatibility branches without a concrete persisted-data or API
  requirement.

## Milestone 0: Reproducible Build Baseline

Goal: establish a build and test environment before changing behavior.

Work:

- Place the repository in a JOSM plugins checkout containing the parent
  `pom.xml`, `build-common.xml`, and `00_core_tools` directory.
- Confirm Maven and Ant produce the canonical `MapRouletteFlow.jar` artifact.
- Record inherited test failures separately from new regressions.
- Verify the external artifact identity is `MapRouletteFlow`, the entry class is
  `io.github.richardqzeng.josm.maprouletteflow.MapRouletteFlowPlugin`, and no
  classes remain in the official plugin package.
- Verify MapRoulette Flow and official MapRoulette can remain enabled together,
  while documenting that only one task workflow may be active at a time.

Exit criteria:

- `mvn test` and `mvn verify` can run from a documented local setup.
- The baseline plugin loads in a clean JOSM profile.

## Milestone 1: Authentication and Settings

Goal: validate the account that will receive task attribution before enabling
task-changing actions.

Primary files:

- `gui/preferences/MapRouletteServerPreference.java`
- `gui/preferences/MapRouletteTaskPreference.java`
- `gui/preferences/MapRouletteTaskListPreferences.java`
- `util/OsmPreferenceUtils.java`
- `util/HttpClientUtils.java`
- new minimal current-user API model/parser

Work:

- Add `Direct API key` and `Automatic from OSM preferences` modes.
- Replace the ambiguous API-key combo box with mode-specific controls.
- Add a masked direct-key field and session-only storage by default.
- Add an explicit `Remember key` option and warning.
- Preserve automatic lookup of `maproulette_apikey_v2` and custom preference
  names.
- Fix the missing-dot mismatch between custom preference-name read and write
  keys.
- Scope remembered/cached keys by MapRoulette base URL and active OSM user.
- Clear the active key on HTTP 401, account change, server change, or rotation.
- Add `GET /user/whoami` and parse only user ID, linked OSM identity, display
  name, and score.
- Add `Test Connection` and authenticated-account display.
- Ensure validation uses only the candidate `apiKey` header and no unrelated
  session credential.
- Prevent credential headers and full authentication responses from reaching
  logs or diagnostics.
- Rename `Task List` settings to `Exclusions` without changing existing stored
  keys.
- Populate `Task Preferences` with next mode, radius, padding, and centering.

Tests:

- Valid direct and OSM-preference keys.
- Malformed, missing, rotated, and unauthorized keys.
- Account/server switching and cache clearing.
- Preference migration and the custom-key-name bug.
- Credential redaction.

Exit criteria:

- Test Connection identifies the expected MapRoulette and OSM account.
- Task-changing actions remain disabled while unauthenticated.
- No API key appears in logs or test failure output.

## Milestone 2: Workflow State Controller

Goal: replace scattered static workflow state with one testable owner.

Primary files:

- `gui/ModifiedObjects.java`
- `gui/task/list/TaskListPanel.java`
- current-task actions and upload hooks that access `ModifiedObjects`

Introduce explicit states:

```text
DISCONNECTED
CHALLENGE_IDLE
RESERVED_PREVIEW
STARTING_DOWNLOAD
ACTIVE_EDITING
COMPLETION_DRAFT
WAITING_FOR_UPLOAD
SUBMITTING
RECOVERABLE_ERROR
```

Work:

- Create one controller/model for active challenge, reserved task, active task,
  completion draft, next mode, edit layer, and listener handles.
- Define legal transitions and transition-specific cleanup.
- Reject candidate requests while active edits or completion are pending.
- Marshal all UI-observable state changes onto the Swing event thread.
- Keep credential state outside persisted workflow drafts.
- Add shutdown cleanup for refresh timers and one-shot listeners.

Tests:

- Every legal transition.
- Forbidden challenge switching and candidate replacement.
- Cleanup after release, success, cancellation, errors, and shutdown.

Exit criteria:

- UI and upload code no longer mutate independent static maps directly.
- Illegal transitions cannot silently discard a lock or draft.

## Milestone 3: Challenge Input and One-Task Reservation

Goal: enter a challenge and show exactly one server-reserved candidate.

Primary files:

- `gui/task/list/TaskListPanel.java`
- `api/ChallengeAPI.java`
- `api/TaskAPI.java`
- `workflow/TaskReservationService.java`
- `workflow/WorkflowController.java`
- `gui/layer/MapRouletteClusteredPointLayer.java`

Work:

- Add challenge ID/URL input and Load Challenge action to the main panel.
- Parse supported challenge URLs into a numeric challenge ID.
- Consolidate overlapping challenge URL handlers into this workflow.
- Remove batch-oriented challenge controls and assumptions.
- Add one-candidate API methods using `limit=1`.
- Use no proximity for Random and the completed task ID for Nearby.
- Guard candidate calls because the backend releases the caller's existing locks
  and locks the returned task.
- Render one reserved preview with challenge, status, priority, and geometry.
- Center full geometry with location fallback.
- Recalculate layer bounds when the preview changes.
- Add Release and ten-minute refresh behavior.
- Block ignored challenges before requesting a candidate.
- Release ignored candidates and retry at most three times.
- Handle empty challenges and repeated excluded candidates clearly.

Tests:

- Challenge ID and URL parsing.
- Random and Nearby request construction.
- Exactly one returned candidate.
- Reservation, refresh, release, and shutdown cleanup.
- Guard against replacing an active task or draft.
- Ignored challenge and bounded ignored-task retries.
- Geometry and location-only centering.

Exit criteria:

- Loading a challenge produces one labeled reserved preview.
- No OSM data is downloaded before confirmation.
- Release reliably clears local state and attempts server cleanup.

## Milestone 4: Start and Editable OSM Download

Goal: make `Start & Download` prepare JOSM for editing end to end.

Primary files:

- `workflow/TaskDownloadService.java`
- `data/TaskPrimitives.java`
- task geometry/bounds helpers

Work:

- Refresh/start the reserved task and retrieve its full representation.
- Run network and download operations outside Swing's event thread.
- Compute padded bounds from full geometry.
- Use the configured point radius when geometry is absent or unusable.
- Run `DownloadOsmTask` with `DownloadParams.withNewLayer(false)`.
- Merge into a suitable active editable layer or create one.
- Select referenced primitives after download.
- Fix the defect that discards `SimplePrimitiveId.fromString(id)` results.
- Center the task and show full instructions only after setup completes.
- Keep the reservation on recoverable download failure and offer Retry/Release.

Tests:

- Geometry, point-only, geometry-only, and empty task bounds.
- Active-layer merge and new-layer creation.
- Formatted node, way, and relation IDs.
- Missing referenced primitives.
- Download success, cancellation, and failure.
- Swing thread ownership.

Exit criteria:

- Start & Download creates a useful editable dataset without freezing the UI.
- Failure cannot leave an invisible or unmanageable lock.

## Milestone 5: Non-Fixed Completion Slice

Goal: prove the new completion and retry architecture without OSM upload
coordination.

Primary files:

- `gui/task/current/CurrentTaskPanel.java`
- `gui/task/current/TaskStatusAction.java`
- `api/TaskAPI.java`
- new completion dialog/controller and draft model

Work:

- Replace dropdown completion actions with five visible web labels.
- Add one reusable confirmation dialog.
- Implement comment limit, Write/Preview, tags, completion responses, review
  state, next mode, and expandable instructions.
- Validate required completion responses and challenge tag limits.
- URL-encode query parameters consistently.
- Send status with `requestReview`, `tags`, and JSON completion responses.
- Submit comments through the separate comment endpoint.
- Check actual success codes: status `204`, comment `201`, tags `200` when the
  explicit update endpoint is used.
- Treat status `204` as the completion commit point; do not resubmit status when
  retrying a failed auxiliary operation.
- Preserve a recoverable auxiliary retry record if comment/tag handling fails
  after status success.
- Account for the backend releasing the task during successful status update.
- Enable Already fixed, Not an Issue, Can't Complete, and Skip first.

Tests:

- Five label/status mappings.
- Cancel with zero mutation.
- Comment limits and Markdown preview.
- Tag limits and URL encoding.
- Review true, false, and omitted.
- Required completion responses.
- Status failure versus post-status auxiliary failure.
- Correct account attribution and expected score change.

Exit criteria:

- All non-Fixed outcomes complete without an OSM upload.
- A successful status is never duplicated merely to retry a comment.

## Milestone 6: Upload-Gated Fixed Completion

Goal: submit Fixed only after the relevant JOSM upload actually succeeds.

Primary files:

- `io/upload/EarlyUploadHook.java`
- `io/upload/FixedUploadCoordinator.java`
- workflow and completion controllers

Work:

- Restrict upload hooks to task detection and changeset metadata preparation.
- Merge challenge comment/source with user values instead of overwriting them.
- Remove status and release operations from pre-upload timing.
- Capture the relevant edit layer, task, and expected changeset task tag.
- Register a one-shot post-upload observer before opening the upload dialog.
- Initially integrate through `ChangesetCacheListener` and verify the successful
  changeset contains the expected `maproulette:tasks` value.
- Verify the captured edit layer no longer requires upload for the task changes.
- Submit Fixed, comment, and changeset association only after both checks pass.
- Remove the observer on success, cancellation cleanup, task release, error, map
  frame close, and plugin shutdown.
- Preserve the draft and active state on cancellation or upload failure.
- Add explicit no-edit warning and confirmation.

Tests:

- Upload dialog cancellation.
- Upload transport/server failure.
- Successful correlated changeset.
- Unrelated changeset updates ignored.
- No-edit Cancel and explicit Fixed paths.
- Observer removal on every terminal path.
- Fixed submitted exactly once.

Exit criteria:

- No pre-upload hook can submit Fixed or advance the workflow.
- Upload failure cannot lose the draft or task context.

## Milestone 7: Automatic Next Reserved Preview

Goal: advance from successful completion to one reserved preview without
premature OSM download.

Work:

- Persist Random/Nearby as a per-challenge preference with a global fallback.
- Retain the completed task ID for Nearby proximity.
- Request one next candidate only after completion and auxiliary operations reach
  their required success state.
- Accept the backend reservation, select it, and center it.
- Return the workflow to `RESERVED_PREVIEW`.
- Start the refresh timer while waiting for confirmation.
- Do not invoke editable OSM download until Start & Download.
- Handle no-more-task and excluded-candidate exhaustion states.

Tests:

- Random without proximity.
- Nearby with completed-task proximity.
- Automatic selection and centering.
- Reservation refresh while waiting.
- No OSM download before confirmation.
- No-more-task and exclusion exhaustion.

Exit criteria:

- Every successful completion ends in a reserved preview or explicit terminal
  challenge state.

## Milestone 8: Hardening and Cooperative Fixes

Goal: close inherited defects that can corrupt task state or completion.

Work:

- Persist recoverable workflow drafts without credentials.
- Respect cooperative tag-change Keep checkboxes.
- Ensure failed or cancelled cooperative operations never queue Fixed.
- Correct `tasksNearby` handling of `excludeSelfLocked` where still used.
- Remove listeners during panel and plugin teardown.
- Define synchronization/thread confinement for layer and workflow data.
- Handle unsupported cooperative object creation explicitly.
- Verify cached authentication and workflow state across OSM account changes.

Tests:

- Draft recovery after restart.
- Cooperative Apply, Show, Cancel, undo, and redo.
- Keep selections, version mismatch, and missing primitives.
- Repeated map-frame open/close cycles.
- Account switching with active and inactive workflows.

Exit criteria:

- Restart and teardown cannot leak credentials, listeners, or invisible locks.

## Required Verification

Run for every milestone:

```text
mvn test
mvn spotless:check
mvn verify
```

Before release, perform a manual integration pass using a test OSM account and
test MapRoulette challenge covering:

- Direct and automatic authentication.
- Random and Nearby reservations.
- Preview release and ignored candidates.
- Geometry and point-only downloads.
- All five completion results.
- Successful, cancelled, and failed Fixed uploads.
- Correct account score changes.
- Automatic next preview without premature download.

## Release Gate

- Every included workflow has automated coverage or a documented manual reason.
- No network action blocks Swing's event dispatch thread.
- Candidate endpoints cannot replace active work.
- Status commit and auxiliary retry semantics are tested.
- Upload cancellation/failure cannot lose a draft or advance.
- API keys and private authentication responses are absent from logs.
- The manual integration matrix completes without stale listeners or locks.
