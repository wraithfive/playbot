# QOTD: Multiple Streams per Channel (Design)

Status: Proposed
Owner: @wraithfive
Last updated: 2025-10-25
Related: #1

## Summary
Enable multiple independent QOTD "streams" under a single text channel. Each stream maintains:
- Its own question list and rotation (sequential or random)
- Its own schedule (cron or weekly builder)
- Its own banner (title) and embed color
- Its own mention target (e.g., @role, @everyone)

This lets servers run, for example, “QOTD” and “Fun Friday” in the same channel without interfering with each other, while preserving separate pacing and content.

## Goals
- Multiple schedules and question lists per channel
- Independent banner and mention per list/stream
- Backward compatible migration from current per-channel model
- Minimal friction in UI/UX for admins

## Non-Goals (initial phase)
- Cross-channel streams
- Per-stream moderation roles/ACLs (inherits channel perms)
- Complex conditional schedules (beyond cron/weekly)

## Data model
Introduce a new table `qotd_stream` and re-associate questions/config to a stream rather than directly to a channel.

- qotd_stream
  - id (PK)
  - guild_id (FK ref context; used for validation)
  - channel_id (FK to Discord channel id)
  - name (varchar 64) — admin-facing name like "QOTD", "Fun Friday"
  - schedule_cron (varchar 64 nullable)
  - timezone (varchar 64; default UTC)
  - randomize (boolean; default false)
  - auto_approve (boolean; default false)
  - next_index (int; default 0)
  - banner_text (varchar 160; default ❓❓ Question of the Day ❓❓)
  - embed_color (int nullable)
  - mention_target (varchar 64 nullable)
  - created_at, updated_at (timestamps)

- qotd_question (adjust)
  - Add stream_id (FK -> qotd_stream.id)
  - Replace channel_id linkage with stream_id
  - display_order (int)
  - text (varchar 2000)
  - author_user_id (varchar 32 nullable)
  - author_username (varchar 128 nullable)

- qotd_config (legacy)
  - Retain for now; used only to migrate/create a default stream
  - Mark as deprecated and no longer read for runtime after migration

- qotd_banner (legacy)
  - Fold into stream (banner_text, embed_color, mention_target)
  - Keep table during migration; later deprecate/clean

### Migration plan
1) Create qotd_stream
2) For each existing (guildId, channelId) in qotd_config or qotd_banner:
   - Create one default stream with settings from qotd_config, banner_text/embed_color/mention_target from qotd_banner
   - Re-map qotd_question rows for that (guildId, channelId) to the new stream_id preserving display_order
3) Update services to read/write through qotd_stream
4) Leave old tables populated for rollback window; add follow-up changeSet to drop after one release

## API surface
Add stream-scoped endpoints. Keep channel-scoped routes for BC by mapping to the default stream (id returned in DTOs).

Base: `/api/servers/{guildId}`

- Streams CRUD
  - GET `/channels/{channelId}/qotd/streams` -> StreamSummaryDto[]
  - POST `/channels/{channelId}/qotd/streams` { name, …config } -> StreamDto
  - GET `/channels/{channelId}/qotd/streams/{streamId}` -> StreamDto
  - PUT `/channels/{channelId}/qotd/streams/{streamId}` { …config } -> StreamDto
  - DELETE `/channels/{channelId}/qotd/streams/{streamId}` -> 204 (fails if questions exist unless `?force=true`)

- Stream questions
  - GET `/channels/{channelId}/qotd/streams/{streamId}/questions`
  - POST `/channels/{channelId}/qotd/streams/{streamId}/questions` { text, author? }
  - DELETE `/channels/{channelId}/qotd/streams/{streamId}/questions/{id}`
  - PUT `/channels/{channelId}/qotd/streams/{streamId}/questions/reorder` { orderedIds: number[] }
  - POST `/channels/{channelId}/qotd/streams/{streamId}/upload-csv` (multipart)

- Stream banner & mention
  - GET `/channels/{channelId}/qotd/streams/{streamId}/banner`
  - PUT `/channels/{channelId}/qotd/streams/{streamId}/banner` (text/plain)
  - GET `/channels/{channelId}/qotd/streams/{streamId}/banner/color`
  - PUT `/channels/{channelId}/qotd/streams/{streamId}/banner/color` (application/json)
  - GET `/channels/{channelId}/qotd/streams/{streamId}/banner/mention`
  - PUT `/channels/{channelId}/qotd/streams/{streamId}/banner/mention` (text/plain, optional body clears)

- Posting
  - POST `/channels/{channelId}/qotd/streams/{streamId}/post-now`

- Backward compatibility shims (temporary)
  - Existing channel-level endpoints route to that channel’s default stream (first stream or a specifically flagged one)

### DTO sketch
- StreamDto
  - id, channelId, name, enabled, timezone, scheduleCron, randomize, autoApprove, nextIndex, nextRuns[], bannerText, embedColor, mentionTarget
- StreamSummaryDto
  - id, name, enabled, nextRuns[]

## Scheduler and posting
- One scheduled task per stream (not per channel)
- Collision handling: if multiple streams share the same minute in the same channel:
  - Post sequentially in deterministic order (e.g., by streamId)
  - Ensure embeds/messages send reliably; small jitter (100–300ms) between sends
- Posting composition:
  - Title: stream.banner_text; Description: [mention + space] + question.text
  - Fallback to plain text retains mention line and banner

## Frontend changes
- QotdManager gets a Stream selector for the selected channel:
  - View/add/edit/delete streams
  - Per-stream tabs or panels for schedule, banner/color, mention, and question list
- CSV upload, reorder, randomize all operate on the selected stream
- Legacy: if no streams exist, auto-create “Default” stream from current config when selecting a channel

## Permissions and mentions
- Behavior identical to current implementation:
  - @everyone/@here require the bot’s “Mention @everyone, @here, and All Roles” permission
  - Role mentions require either role mentionable or the same permission
  - Channel mentions link but don’t ping

## Edge cases
- Deleting a stream with questions -> block unless `force=true`, then delete questions
- Streams with identical schedules -> sequential posts; document expected ordering
- Streams disabled -> skip scheduling and post-now should 409 or soft-warn

## Rollout plan
- Phase 1: Backend foundations
  - Liquibase migrations (qotd_stream, backfill)
  - Service layer reads from stream; default stream created on access
  - Backward-compatible channel endpoints
  - Unit tests for migration helpers and posting across streams
- Phase 2: Frontend
  - Stream selector + per-stream management
  - Move existing UI to operate on a stream context
- Phase 3: Cleanup
  - Migrate all channel-level endpoints in UI to stream endpoints
  - Mark legacy config/banner tables for removal in a later migration

## Interplay with per-channel timezone (#2)
- Source of truth:
  - Each stream should own its timezone (timezone column already included). If unset, it falls back to the channel’s timezone (current model) and ultimately to UTC.
- Migration behavior:
  - When creating the default stream from existing per-channel config, copy the channel’s timezone into the stream’s timezone.
  - If a server sets a channel-level timezone before multi-streams ships, the generated default stream will inherit it automatically.
- Scheduler and previews:
  - Cron triggers execute using the stream’s ZoneId. nextRuns are computed in the same ZoneId so preview matches runtime.
  - Streams in the same channel may have different timezones; if they align to the same minute, standard collision logic applies (deterministic order + small jitter).
- UI/UX:
  - Stream editor shows a timezone selector (IANA tz) with search. If the stream timezone is empty, display the effective fallback (channel/UTC) and allow overriding per stream.
  - Optional later enhancement: a per-guild default timezone used to prefill new channels/streams (out of scope here).
- Backward compatibility:
  - Until streams are introduced, per-channel timezone config (see issue #2) governs scheduling.
  - After streams, legacy channel config maps to a single default stream that contains the timezone value; channel-level timezone remains as a fallback for newly created streams if not explicitly set.

## Success criteria
- A channel can host multiple streams; each stream posts independently on its own schedule
- Streams have distinct question lists, banners, colors, and mention targets
- Existing channels transparently continue working after upgrade

## Estimates (rough)
- Backend + migrations + tests: 1.5–2.5 days
- Frontend UI/UX + tests: 1.5–2.5 days
- Total: ~3–5 days depending on polish

## Open questions
- Default stream naming convention (e.g., “Default” vs channel name) — propose “Default”
- Do we allow cross-stream question moves? (Nice to have)
- Should per-stream banner/mention inherit from channel defaults? (Out of scope for v1; streams own their values)
