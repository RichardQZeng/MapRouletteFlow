# MapRoulette Flow Product Plan

## Objective

Provide a focused MapRoulette challenge workflow inside JOSM using one reserved
task at a time:

```text
Authenticate
-> Enter a challenge ID or URL
-> Reserve one Random or Nearby task
-> Preview it
-> Confirm Start & Download
-> Edit in JOSM
-> Complete the task safely
-> Reserve and preview the next task
```

## Release Scope

Included:

- Direct MapRoulette API-key authentication.
- Automatic API-key retrieval from OSM user preferences.
- Account validation and score display.
- One active challenge entered by ID or URL.
- One server-reserved task preview at a time.
- Random or Nearby next-task selection.
- Explicit confirmation before editable OSM data is downloaded.
- Automatic task-area OSM download into a JOSM edit layer.
- Five web-style completion results.
- Comments, MR tags, completion responses, review requests, and instructions.
- Fixed completion gated on actual successful OSM upload.
- Automatic reservation and preview of the next task.
- Existing ignored-task and ignored-challenge preferences.

Deferred:

- Searchable challenge discovery.
- Multi-task preview batches.
- Automatic OSM download before user confirmation.
- Full bundle and review/revision parity.
- Cooperative OSM object creation.

## Authentication

MapRoulette authentication uses this exact HTTP header:

```text
apiKey: <numeric-user-id>|<UUID>
```

The key is not a Bearer token and must never be placed in a URL or log message.

The plugin supports two authentication modes:

- `Direct API key`: a masked input for the key shown on the MapRoulette profile.
- `Automatic from OSM preferences`: fetch the named preference from the active
  JOSM OSM account, defaulting to `maproulette_apikey_v2`.

`Test Connection` calls:

```text
GET /api/v2/user/whoami
```

The plugin displays only the MapRoulette account, linked OSM identity, and
current score. The complete response is discarded because it contains private
account data and echoes the API key.

Direct keys remain in memory by default. `Remember key` is explicit and warns
that ordinary JOSM preference storage is not a secure credential vault.

At startup, the plugin validates an available automatic or remembered
credential with `/user/whoami`. A temporary connection failure leaves the
credential intact; HTTP 401 clears the rejected credential. Session-only direct
keys still require manual entry after restarting JOSM.

Task-changing controls remain disabled until authentication succeeds.

## Settings

### Server and Account

- MapRoulette API URL, default `https://maproulette.org/api/v2`.
- Authentication mode.
- Masked direct API-key field.
- OSM preference name for automatic mode.
- Remember key.
- Test Connection.
- Connected MapRoulette and OSM account details.
- Current MapRoulette score.

### Task Workflow

- Default next-task mode: Random or Nearby.
- Point-task OSM download radius.
- Geometry download padding.
- Automatic centering.

### Exclusions

The existing `Task List` settings tab becomes `Exclusions` and preserves:

- `maprouletteflow.ignore.tasks`
- `maprouletteflow.ignore.challenges`

The existing `Keep` behavior remains: checked keeps the item excluded;
unchecked followed by OK removes the exclusion.

## Main Challenge Panel

The challenge input belongs in the main MapRoulette panel, not Preferences:

```text
Challenge ID or URL:
[____________________________________] [Load Challenge] [Clear]

Challenge: <name>
Next task: (o) Random  ( ) Nearby

<reserved task preview>

[Start & Download] [Release]
```

The input accepts a numeric challenge ID or a supported MapRoulette challenge
URL. After a challenge loads successfully, its canonical numeric ID is
remembered for that MapRoulette server and prefilled on the next startup. It is
not loaded automatically because loading reserves a task. `Clear` forgets the
saved input without releasing or unloading current work.

There is no ten-task preview, `Load 10 More`, or multi-task fitting workflow.

## Reserved Preview

Loading a challenge requests one candidate.

Random:

```text
GET /api/v2/challenge/{challengeId}/tasks/prioritizedTasks?limit=1
```

Nearby after completing a task:

```text
GET /api/v2/challenge/{challengeId}/tasks/prioritizedTasks
    ?limit=1
    &proximity={completedTaskId}
```

The backend releases the caller's existing task locks and locks the returned
task. The plugin therefore calls these endpoints only when there is no active
edit, pending completion, or unrelated reservation.

Random follows server priority. When proximity is supplied, the backend chooses
by proximity and intentionally bypasses priority.

The candidate is labeled `Reserved`, selected, and centered. Its lock is
refreshed every ten minutes. Editable OSM data is not downloaded until the user
confirms `Start & Download`.

`Release` cancels the preview and releases the reservation.

## Start and Download

`Start & Download` performs this sequence:

1. Call `/task/{id}/start` to refresh the reservation and retrieve the full task.
2. Compute padded bounds from task geometry.
3. Use the configured radius when usable geometry is absent.
4. Download OSM data into a suitable active editable layer.
5. Create an edit layer when necessary.
6. Select referenced OSM primitives that are present.
7. Center the task and display full instructions.

Network and download work runs outside Swing's event dispatch thread.

Download failure keeps the reservation and offers Retry or Release.

## Completion

The current task panel exposes five results:

| Label | API status | Default points |
|---|---:|---:|
| I fixed it! | 1 | 5 |
| Not an Issue | 2 | 3 |
| Skip | 3 | 0 |
| Already fixed | 5 | 3 |
| Can't Complete | 6 | 1 |

Points are backend-configured and are added only when a task changes to a
different status. The authenticated API-key account receives the attribution.

The reusable confirmation dialog contains:

- Selected result.
- Comment with a 5,000-character limit.
- Write and Markdown Preview modes.
- MR tags and challenge tag limits.
- Required completion-response fields.
- Review-request state.
- Random or Nearby next-task mode.
- Expandable task instructions.
- Cancel and Submit.

Cancel does not update status, release the task, or advance.

The status operation follows the current web contract:

```text
PUT /api/v2/task/{taskId}/{status}
    ?requestReview={value}
    &tags={tags}

Body: completion-response JSON
```

Comments are submitted separately through:

```text
POST /api/v2/task/{taskId}/comment?actionId={status}
Body: {"comment":"..."}
```

## Fixed and OSM Upload

When unuploaded edits exist:

```text
Confirm Fixed
-> Preserve the completion draft
-> Open the normal JOSM upload dialog
-> Merge MapRoulette changeset metadata
-> Wait for actual upload success
-> Submit Fixed and completion details
-> Associate the successful changeset
-> Reserve and preview the next task
```

Upload cancellation or failure keeps the task and draft and does not advance.

If no edits require upload, the plugin warns and allows Cancel or explicit Fixed
submission without a new changeset.

## Next Task

After successful completion the plugin:

1. Requests one task using the selected Random or Nearby mode.
2. Accepts the reservation created by the backend.
3. Selects and centers the candidate.
4. Shows it as a reserved preview.
5. Waits for `Start & Download`.

It does not download editable OSM data before confirmation.

If no task is available, the challenge remains visible and a clear no-more-tasks
message is shown.

## Exclusion Behavior

Loading an ignored challenge shows a warning and offers to remove the exclusion.

If the server reserves an ignored task, the plugin releases it immediately and
retries up to three times. If the server repeatedly returns excluded tasks, the
plugin stops and offers Try Again, Remove from Exclusions, or Leave Challenge.

## Safety Rules

- Never call a lock-mutating candidate endpoint while edits or completion are
  pending.
- Never discard a draft because of upload or API failure.
- Never submit Fixed before actual OSM upload success.
- Never log credentials or private authentication responses.
- Remove lock-refresh and upload listeners on every terminal path and shutdown.
- Check documented HTTP success codes before advancing local state.

## Non-Goals

- The plugin will not show or reserve ten tasks at once.
- Selecting or previewing a task will not download OSM data automatically.
- The plugin will not guarantee a different replacement when the server keeps
  returning the same excluded candidate.
- The first release will not support multiple simultaneous active tasks.
