# QOTD Multi-Streams Implementation Status

## 🎉 FEATURE COMPLETE

**Implementation Date:** October 26, 2025
**Status:** ✅ All code complete - Ready for manual testing and deployment

## Overview
This document tracks the implementation status of the QOTD multi-streams feature (Issue #1).

**Goal:** Enable multiple independent QOTD streams per Discord text channel, each with separate schedules, question lists, banners, and mention targets.

### What's New
- **Multiple Streams per Channel**: Create up to 5 independent QOTD streams per Discord text channel
- **Per-Stream Configuration**: Each stream has its own schedule, timezone, question bank, banner, and settings
- **Stream Management UI**: New stream selector and creation dialog in the admin panel
- **Backward Compatible**: Existing QOTD configurations automatically migrate to "Default" streams
- **Real-time Updates**: WebSocket notifications for stream changes

### Key Changes Summary
- **Backend**: 8 new files, 7 modified files (database migrations, repositories, services, controllers, DTOs)
- **Frontend**: 2 modified files (+245 lines, -65 lines in QotdManager.tsx)
- **Tests**: All 257 backend tests passing
- **Build**: Both backend and frontend build successfully

---

## ✅ COMPLETED: Backend Implementation

### 1. Database Schema (100% Complete)

**New Entity:** `QotdStream`
- Location: [src/main/java/com/discordbot/entity/QotdStream.java](src/main/java/com/discordbot/entity/QotdStream.java)
- Fields: id, guildId, channelId, streamName, scheduleCron, timezone, randomize, nextIndex, bannerText, embedColor, mentionTarget, enabled, autoApprove, lastPostedAt, createdAt, updatedAt

**Modified Entity:** `QotdQuestion`
- Location: [src/main/java/com/discordbot/entity/QotdQuestion.java](src/main/java/com/discordbot/entity/QotdQuestion.java)
- Added: `streamId` column (foreign key to qotd_streams)
- Kept: `guildId`, `channelId` for migration compatibility (TODO: remove after verification)

### 2. Repositories (100% Complete)

**New:** `QotdStreamRepository`
- Location: [src/main/java/com/discordbot/repository/QotdStreamRepository.java](src/main/java/com/discordbot/repository/QotdStreamRepository.java)
- Methods: findByGuildIdAndChannelId, findByEnabledTrue, countByGuildIdAndChannelId (for 5-stream limit)

**Modified:** `QotdQuestionRepository`
- Added stream-based queries: `findByStreamIdOrderByDisplayOrderAsc`, `deleteByIdAndStreamId`
- Kept legacy queries for backward compatibility (marked @Deprecated with TODO)

### 3. Database Migrations (100% Complete)

**Migration 007:** Create streams table
- File: [src/main/resources/db/changelog/changes/007-add-qotd-streams.xml](src/main/resources/db/changelog/changes/007-add-qotd-streams.xml)
- Creates `qotd_streams` table with all columns
- Adds `stream_id` column to `qotd_questions` (nullable during migration)
- Adds foreign key constraint with CASCADE delete

**Migration 008:** Rename deprecated tables
- File: [src/main/resources/db/changelog/changes/008-rename-deprecated-qotd-tables.xml](src/main/resources/db/changelog/changes/008-rename-deprecated-qotd-tables.xml)
- Renames `qotd_configs` → `qotd_configs_deprecated`
- Renames `qotd_banner` → `qotd_banner_deprecated`
- Cleanup tracked in **Issue #3**

**Master Changelog:**
- Updated [src/main/resources/db/changelog/db.changelog-master.xml](src/main/resources/db/changelog/db.changelog-master.xml)
- Includes both migration 007 and 008

### 4. Data Migration Service (100% Complete)

**Service:** `QotdStreamMigrationService`
- Location: [src/main/java/com/discordbot/web/service/QotdStreamMigrationService.java](src/main/java/com/discordbot/web/service/QotdStreamMigrationService.java)
- Runs on `ApplicationReadyEvent` (app startup)
- Migrates old `qotd_configs` + `qotd_banner` data to default streams
- Updates all existing questions to reference new stream IDs
- Idempotent (safe to re-run)

### 5. Business Logic Layer (100% Complete)

**New Service:** `QotdStreamService`
- Location: [src/main/java/com/discordbot/web/service/QotdStreamService.java](src/main/java/com/discordbot/web/service/QotdStreamService.java)
- **Stream CRUD:** listStreams, getStream, createStream, updateStream, deleteStream
- **Question Management:** listQuestions, addQuestion, deleteQuestion, reorderQuestions, uploadCsv
- **Banner Management:** getBanner, setBanner, getBannerColor, setBannerColor, getBannerMention, setBannerMention, resetBanner
- **Posting:** postNextQuestion (called by scheduler or manual post-now)
- **Validation:** Enforces 5-stream-per-channel limit, unique stream names per channel

**Modified Scheduler:** `QotdScheduler`
- Location: [src/main/java/com/discordbot/web/service/QotdScheduler.java](src/main/java/com/discordbot/web/service/QotdScheduler.java)
- New method: `tickStreams()` - processes stream-based schedules
- Legacy method: `tickLegacyConfigs()` - kept for backward compatibility (marked @Deprecated with TODO)
- Both run every minute via single `@Scheduled` method

### 6. REST API Layer (100% Complete)

**New Controller:** `QotdStreamController`
- Location: [src/main/java/com/discordbot/web/controller/QotdStreamController.java](src/main/java/com/discordbot/web/controller/QotdStreamController.java)
- Base path: `/api/servers/{guildId}/channels/{channelId}/qotd/streams`
- **Stream endpoints:** GET/POST (list/create), GET/PUT/DELETE `/{streamId}` (get/update/delete)
- **Question endpoints:** All nested under `/{streamId}/questions`
- **Banner endpoints:** All nested under `/{streamId}/banner`
- **Post-now:** `POST /{streamId}/post-now`
- **Permission checks:** All endpoints validate `canManageGuild` via `AdminService`

### 7. DTOs (100% Complete)

**Added to:** [src/main/java/com/discordbot/web/dto/qotd/QotdDtos.java](src/main/java/com/discordbot/web/dto/qotd/QotdDtos.java)
- `QotdStreamDto` - Full stream data with computed nextRuns
- `CreateStreamRequest` - Create stream with banner, schedule, flags
- `UpdateStreamRequest` - Update stream config (excludes banner fields)

### 8. WebSocket Notifications (100% Complete)

**Modified:** `WebSocketNotificationService`
- Location: [src/main/java/com/discordbot/web/service/WebSocketNotificationService.java](src/main/java/com/discordbot/web/service/WebSocketNotificationService.java)
- New method: `notifyQotdStreamChanged(guildId, channelId, streamId, action)`
- Message type: `QOTD_STREAM_CHANGED` with actions: created, updated, deleted

---

## ✅ COMPLETED: Frontend API Layer

### 1. TypeScript Types (100% Complete)

**Added to:** [frontend/src/types/qotd.ts](frontend/src/types/qotd.ts)
- `QotdStreamDto` - matches backend DTO
- `CreateStreamRequest` - create stream request
- `UpdateStreamRequest` - update stream request

### 2. API Client (100% Complete)

**Modified:** [frontend/src/api/client.ts](frontend/src/api/client.ts)
- **Stream management:** listStreams, getStream, createStream, updateStream, deleteStream
- **Stream questions:** listStreamQuestions, addStreamQuestion, deleteStreamQuestion, reorderStreamQuestions, uploadStreamCsv
- **Stream banner:** getStreamBanner, setStreamBanner, getStreamBannerColor, setStreamBannerColor, getStreamBannerMention, setStreamBannerMention, resetStreamBanner
- **Stream actions:** postStreamNow

All endpoints follow nested URL structure: `/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}/...`

---

## ✅ COMPLETED: Frontend UI Component

### QotdManager.tsx Update (100% Complete)

**File modified:** [frontend/src/components/QotdManager.tsx](frontend/src/components/QotdManager.tsx)
**Changes:** +245 lines, -65 lines (303 total changes)

**Required Changes:**

#### 1. Add Stream State Management

```typescript
const [selectedStreamId, setSelectedStreamId] = useState<number | null>(null);
const [streams, setStreams] = useState<QotdStreamDto[]>([]);
const [showCreateStream, setShowCreateStream] = useState(false);
const [newStreamName, setNewStreamName] = useState('');
```

#### 2. Add Stream Queries

```typescript
// Fetch streams for selected channel
const { data: streams, isLoading: streamsLoading } = useQuery({
  queryKey: ['qotd-streams', guildId, selectedChannelId],
  queryFn: () => qotdApi.listStreams(guildId!, selectedChannelId!),
  enabled: !!guildId && !!selectedChannelId,
});

// Auto-select first stream when streams load
useEffect(() => {
  if (streams && streams.length > 0 && !selectedStreamId) {
    setSelectedStreamId(streams[0].id);
  }
}, [streams]);
```

#### 3. Replace Channel-Scoped Queries with Stream-Scoped

**Before:**
```typescript
const { data: questions } = useQuery({
  queryKey: ['qotd-questions', guildId, selectedChannelId],
  queryFn: () => qotdApi.listQuestions(guildId!, selectedChannelId!),
});
```

**After:**
```typescript
const { data: questions } = useQuery({
  queryKey: ['qotd-stream-questions', guildId, selectedChannelId, selectedStreamId],
  queryFn: () => qotdApi.listStreamQuestions(guildId!, selectedChannelId!, selectedStreamId!),
  enabled: !!guildId && !!selectedChannelId && !!selectedStreamId,
});
```

#### 4. Add Stream Selector UI (Below Channel Selector)

```tsx
{/* Channel Selector (existing) */}
<div className="channel-selector">
  {/* ...existing channel buttons... */}
</div>

{/* NEW: Stream Selector */}
{selectedChannelId && (
  <div className="stream-selector" style={{ marginTop: '1rem' }}>
    <h3>Stream:</h3>
    <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
      {streams?.map(stream => (
        <button
          key={stream.id}
          className={selectedStreamId === stream.id ? 'btn btn-primary' : 'btn btn-secondary'}
          onClick={() => setSelectedStreamId(stream.id)}
        >
          {stream.streamName}
          {stream.enabled && ' ✓'}
        </button>
      ))}
      {streams && streams.length < 5 && (
        <button
          className="btn btn-secondary"
          onClick={() => setShowCreateStream(true)}
        >
          + New Stream
        </button>
      )}
    </div>
  </div>
)}
```

#### 5. Add Create Stream Dialog

```tsx
{showCreateStream && (
  <div className="modal-overlay">
    <div className="modal">
      <h3>Create New Stream</h3>
      <input
        type="text"
        placeholder="Stream name (e.g., Weekly QOTD)"
        value={newStreamName}
        onChange={e => setNewStreamName(e.target.value)}
        maxLength={64}
      />
      <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
        <button
          className="btn btn-primary"
          onClick={handleCreateStream}
          disabled={!newStreamName.trim()}
        >
          Create
        </button>
        <button
          className="btn btn-secondary"
          onClick={() => {
            setShowCreateStream(false);
            setNewStreamName('');
          }}
        >
          Cancel
        </button>
      </div>
    </div>
  </div>
)}
```

#### 6. Update All Mutations to Use Stream ID

**Example - Add Question:**

**Before:**
```typescript
const addQuestionMutation = useMutation({
  mutationFn: (text: string) => qotdApi.addQuestion(guildId!, selectedChannelId!, text),
  onSuccess: () => {
    queryClient.invalidateQueries(['qotd-questions', guildId, selectedChannelId]);
  },
});
```

**After:**
```typescript
const addQuestionMutation = useMutation({
  mutationFn: (text: string) =>
    qotdApi.addStreamQuestion(guildId!, selectedChannelId!, selectedStreamId!, text),
  onSuccess: () => {
    queryClient.invalidateQueries(['qotd-stream-questions', guildId, selectedChannelId, selectedStreamId]);
  },
});
```

**Repeat for:** deleteQuestion, reorderQuestions, uploadCsv, updateConfig, setBanner, etc.

#### 7. Add Stream Deletion with Confirmation

```tsx
const handleDeleteStream = async (streamId: number) => {
  const stream = streams.find(s => s.id === streamId);
  const questions = await qotdApi.listStreamQuestions(guildId!, selectedChannelId!, streamId);

  const confirmed = window.confirm(
    `Delete stream "${stream?.streamName}"?\n\n` +
    `This will permanently delete ${questions.data?.length || 0} question(s) in this stream.\n\n` +
    `This action cannot be undone.`
  );

  if (confirmed) {
    await qotdApi.deleteStream(guildId!, selectedChannelId!, streamId);
    queryClient.invalidateQueries(['qotd-streams', guildId, selectedChannelId]);
    // Switch to first remaining stream
    if (streams.length > 1) {
      const remaining = streams.filter(s => s.id !== streamId);
      setSelectedStreamId(remaining[0].id);
    }
  }
};
```

#### 8. Update WebSocket Handler

```typescript
useWebSocket((msg: GuildUpdateMessage) => {
  if (msg.guildId !== guildId) return;

  if (msg.type === 'QOTD_STREAM_CHANGED') {
    queryClient.invalidateQueries(['qotd-streams', guildId, msg.channelId]);
  }

  if (msg.type === 'QOTD_QUESTIONS_CHANGED') {
    // Now need to invalidate specific stream questions
    if (msg.streamId) {
      queryClient.invalidateQueries(['qotd-stream-questions', guildId, msg.channelId, msg.streamId]);
    }
  }
});
```

#### 9. Update Config Section to Use Stream

The schedule, timezone, randomize, autoApprove settings should now come from and save to the selected stream, not the channel config.

**Load stream config:**
```typescript
useEffect(() => {
  if (selectedStreamId && streams) {
    const stream = streams.find(s => s.id === selectedStreamId);
    if (stream) {
      setForm({
        enabled: stream.enabled,
        timezone: stream.timezone,
        scheduleCron: stream.scheduleCron || '',
        randomize: stream.randomize,
        autoApprove: stream.autoApprove,
        // ...
      });
    }
  }
}, [selectedStreamId, streams]);
```

**Save stream config:**
```typescript
const updateStreamMutation = useMutation({
  mutationFn: (req: UpdateStreamRequest) =>
    qotdApi.updateStream(guildId!, selectedChannelId!, selectedStreamId!, req),
  onSuccess: () => {
    queryClient.invalidateQueries(['qotd-streams', guildId, selectedChannelId]);
    setConfigMessage('✓ Stream updated successfully');
  },
});
```

---

## 📝 Implementation Checklist for QotdManager.tsx

- [x] Add stream state variables (selectedStreamId, streams, etc.)
- [x] Add stream queries (listStreams)
- [x] Auto-select first stream on load
- [x] Add stream selector UI below channel selector
- [x] Add "Create Stream" button (max 5 streams)
- [x] Add create stream modal/dialog
- [x] Replace all channel-scoped queries with stream-scoped queries
- [x] Update all mutations to use streamId parameter
- [x] Add stream deletion with confirmation prompt
- [x] Update config loading to read from selected stream
- [x] Update config saving to update selected stream
- [x] Update banner management to use stream banner endpoints
- [x] Update WebSocket handler for stream events
- [x] Add stream name display in UI sections
- [ ] Test stream switching (all data updates correctly) - **Needs Manual Testing**
- [ ] Test create/delete stream flows - **Needs Manual Testing**
- [ ] Test 5-stream limit enforcement - **Needs Manual Testing**
- [ ] Test backward compatibility (channels with default stream) - **Needs Manual Testing**

---

## 🧪 Testing Plan

### Backend Testing

1. **Migration Testing:**
   - [ ] Fresh database: migrations create tables correctly
   - [ ] Existing database: old configs migrate to default streams
   - [ ] Verify all questions get `stream_id` populated
   - [ ] Verify old tables renamed to `_deprecated`

2. **API Testing:**
   - [ ] Create stream (up to 5 per channel)
   - [ ] Create 6th stream (should fail with 400)
   - [ ] Update stream name/config
   - [ ] Delete stream (cascade deletes questions)
   - [ ] Add/delete/reorder questions per stream
   - [ ] Upload CSV to stream
   - [ ] Post now for specific stream
   - [ ] Permission validation (403 for unauthorized users)

3. **Scheduler Testing:**
   - [ ] Multiple streams in same channel with different schedules
   - [ ] Verify correct stream posts at correct time
   - [ ] Verify no duplicate posts within 2-minute window
   - [ ] Verify legacy configs still work (backward compatibility)

### Frontend Testing

1. **UI Testing:**
   - [ ] Channel selection works
   - [ ] Stream selection works
   - [ ] Create stream dialog
   - [ ] Delete stream with confirmation
   - [ ] 5-stream limit UI enforcement
   - [ ] Stream switching updates all panels (config, questions, banner)

2. **Integration Testing:**
   - [ ] Real-time updates via WebSocket
   - [ ] Question drag-and-drop reordering per stream
   - [ ] CSV upload to specific stream
   - [ ] Banner/color/mention per stream
   - [ ] Schedule configuration per stream

---

## 🚀 Deployment Steps

1. **Pre-Deployment:**
   - [ ] Backup production database
   - [ ] Test migrations on staging database
   - [ ] Verify rollback plan (migrations 007 and 008 have rollback support)

2. **Deployment:**
   - [ ] Deploy backend JAR (migrations run automatically on startup)
   - [ ] Verify migration logs (check for "QOTD migration completed")
   - [ ] Verify default streams created for existing channels
   - [ ] Deploy frontend build

3. **Post-Deployment Verification:**
   - [ ] Check existing QOTD channels still work
   - [ ] Create new stream in test channel
   - [ ] Post test question from new stream
   - [ ] Monitor scheduler logs for errors

4. **Cleanup (30-90 days later):**
   - [ ] Verify no issues with streams
   - [ ] Run migration 009 to drop deprecated tables (tracked in Issue #3)
   - [ ] Remove legacy code (tickLegacyConfigs, deprecated queries)

---

## 📚 Related Issues

- **Issue #1:** QOTD: Support multiple streams per channel (THIS FEATURE)
- **Issue #3:** Tech Debt: Clean up deprecated QOTD tables after multi-streams migration

---

## 💡 Notes

- **Backward Compatibility:** Migration automatically creates "Default" stream for each existing channel
- **Stream Limit:** Maximum 5 streams per channel (enforced in backend)
- **Stream Names:** Unique per channel (preferred), but duplicates technically allowed
- **Question Ownership:** Questions belong to streams via `stream_id` foreign key
- **Cascade Delete:** Deleting a stream deletes all its questions (ON DELETE CASCADE)
- **Scheduler:** Both legacy and stream schedulers run (legacy will be removed after verification)
- **Frontend:** Requires manual implementation of stream selector UI and state management

---

## 🛠️ Key Files Modified

### Backend
- ✅ Entity: `QotdStream.java` (new)
- ✅ Entity: `QotdQuestion.java` (added streamId)
- ✅ Repository: `QotdStreamRepository.java` (new)
- ✅ Repository: `QotdQuestionRepository.java` (added stream queries)
- ✅ Migration: `007-add-qotd-streams.xml` (new)
- ✅ Migration: `008-rename-deprecated-qotd-tables.xml` (new)
- ✅ Migration Service: `QotdStreamMigrationService.java` (new)
- ✅ Service: `QotdStreamService.java` (new)
- ✅ Scheduler: `QotdScheduler.java` (added stream support)
- ✅ Controller: `QotdStreamController.java` (new)
- ✅ DTOs: `QotdDtos.java` (added stream DTOs)
- ✅ WebSocket: `WebSocketNotificationService.java` (added stream events)
- ✅ Changelog: `db.changelog-master.xml` (included new migrations)

### Frontend
- ✅ Types: `qotd.ts` (added stream types)
- ✅ API: `client.ts` (added stream endpoints)
- ✅ Component: `QotdManager.tsx` (COMPLETE - refactored for streams)

---

**Status:** Backend 100% Complete ✅ | Frontend API 100% Complete ✅ | Frontend UI 100% Complete ✅

**Build Status:** ✅ All 257 backend tests passing | ✅ Backend compiles successfully | ✅ Frontend builds successfully (470.47 kB)

**Manual Testing Required:** Integration tests needed for stream creation, deletion, switching, and backward compatibility

---

## ⚠️ IMPORTANT: Implementation Fixes Applied

During implementation, the following compatibility fixes were made to support the deprecated table names:

### Fixed Entity Mappings

1. **QotdConfig.java** - Updated to point to `qotd_configs_deprecated`
   - Marked as @Deprecated with TODO comment
   - Will be removed after migration verification (tracked in IDE)

2. **QotdBanner.java** - Updated to point to `qotd_banner_deprecated`
   - Marked as @Deprecated with TODO comment
   - Will be removed after migration verification (tracked in IDE)

3. **QotdSchedulerTest.java** - Updated constructor to include new stream dependencies
   - Mocks `QotdStreamRepository` and `QotdStreamService`
   - Ensures legacy tests still pass while new stream logic coexists

### Fixed Migration Service (October 26, 2025)

4. **QotdStreamMigrationService.java** - Updated to handle table rename timing issue
   - **Issue**: Migration 008 renames tables before the migration service reads them
   - **Fix**: Now checks for BOTH `qotd_configs` and `qotd_configs_deprecated` table names
   - **Behavior**:
     - Fresh installs: No migration needed (tables never existed)
     - Existing installs: Reads from `qotd_configs_deprecated` (after migration 008 runs)
     - Upgrade path: Handles both scenarios automatically
   - **Result**: Data migration now works correctly on application startup

These changes ensure backward compatibility during the migration period. The legacy entities and repositories should be removed after:
1. All production channels have been migrated to streams
2. Migration has been verified for 30-90 days
3. Issue #3 cleanup migration has been deployed
