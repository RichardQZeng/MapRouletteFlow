# MapRoulette Authentication

## Confirmed API-Key Format

MapRoulette authenticates API clients with this HTTP header:

```text
apiKey: <numeric-user-id>|<UUID>
```

The header is named exactly `apiKey`. The value does not use `Bearer` and must
not be placed in a URL or log message.

The key shown on the MapRoulette profile page is already in the expected
format. Rotating that key immediately invalidates the previous value.

## Account Validation

The plugin will validate a configured key using:

```text
GET https://maproulette.org/api/v2/user/whoami
apiKey: <configured-key>
```

A valid key returns HTTP 200 and the authenticated MapRoulette user. An invalid
or malformed key returns HTTP 401. The response contains more private data than
the plugin needs, including an echoed API key, so the implementation must parse
only the account ID, linked OSM identity, display name, and score and then
discard the body.

## Existing Baseline Behavior

The inherited plugin does not currently provide a direct raw-key field. It:

1. Requires an active OSM account in JOSM.
2. Fetches that account's OSM user preferences.
3. Reads `maproulette_apikey_v2` by default.
4. Caches the resulting MapRoulette key for that OSM user and server.

The existing `MapRoulette OpenStreetMap Preference Key` setting expects the
*name of an OSM preference*, not the API-key value. Pasting a raw MapRoulette
key into that field will not authenticate. Its custom preference-name handling
also has a read/write key mismatch that is scheduled for correction.

## Planned Setup UI

`Preferences -> MapRoulette -> Server Settings` will provide:

- MapRoulette API URL, defaulting to `https://maproulette.org/api/v2`.
- Authentication mode: `Automatic from OSM preferences` or `Direct API key`.
- A masked direct API-key input.
- Optional session-only storage.
- `Test Connection`.
- Authenticated MapRoulette account, linked OSM name, and current score.

The plugin will not allow task-changing operations until the connection test
succeeds.

## Points and Attribution

Task completion does not send a user ID in the status body. The backend credits
the account identified by the API key. Default points are:

| Result | Points |
|---|---:|
| I fixed it! | 5 |
| Not an Issue | 3 |
| Already fixed | 3 |
| Can't Complete | 1 |
| Skip | 0 |

Points are only added when a task changes to a different status. A repeated
submission of the same status does not award the points again.

## Security Rules

- Never commit or paste an API key into source, issues, chat, or test fixtures.
- Never put the key in query parameters.
- Never log HTTP headers or the complete `whoami` response.
- Clear cached authentication after HTTP 401, account changes, server changes,
  or key rotation.
- Use HTTPS for MapRoulette API traffic.
