# QOTD Channel Tree Feature - Testing Guide

## Overview

This guide explains how to test the new QOTD channel tree selection feature, which allows users to select regular Discord channels or threads for QOTD posting.

## Test Structure

The channel tree feature includes:
- **Channels** shown with 📌 icon (collapsible if they contain threads)
- **Threads** shown with 🧵 icon, nested under their parent channel
- **Expand/Collapse** arrows (▶/▼) for channels with threads
- **Selection** with visual highlight and checkmark for configured channels

## Prerequisites

1. **Discord Bot Token** - Set in `.env` as `DISCORD_TOKEN`
2. **Test Discord Server** - A dedicated server for testing
3. **Bot Permissions** - Bot must have permissions to view channels/threads
4. **Admin Access** - You must be an admin in the test server
5. **OAuth2 Configured** - Discord OAuth2 set up with proper redirect URIs

## Step 1: Set Up Test Server

Run the automated setup script to create test channels and threads:

```bash
# Get your test server's guild ID from Discord
# Right-click server → Copy Server ID

node scripts/setup-test-server.js <YOUR_GUILD_ID>
```

### What Gets Created

The script creates:
- **test-no-threads** - Regular channel with no threads
- **test-with-2-threads** - Channel with 2 nested threads
  - thread-1-discussion
  - thread-2-questions
- **test-with-3-threads** - Channel with 3 nested threads
  - announcements-thread
  - bugs-thread
  - feature-requests-thread
- **test-empty-channel** - Empty channel for testing

A configuration file is saved to `e2e/test-server-config.json` with all channel/thread IDs for automated testing.

## Step 2: Start the Application

```bash
# Build both backend and frontend
./build.sh

# Start the application
./start.sh

# Application runs at http://localhost:8080
```

## Step 3: Manual Testing

### Via Web UI

1. Navigate to `http://localhost:8080`
2. Click "Login with Discord"
3. Authorize the application
4. Select your test server from the list
5. Click "💬 QOTD Config" tab

### Expected Tree Structure

You should see:

```
📌 test-empty-channel
📌 test-no-threads
📌 test-with-2-threads ▼
  🧵 thread-1-discussion
  🧵 thread-2-questions
📌 test-with-3-threads ▼
  🧵 announcements-thread
  🧵 bugs-thread
  🧵 feature-requests-thread
```

### Test Cases

#### 1. Tree Rendering
- [ ] All 4 channels appear in the list
- [ ] Channels with threads show expand/collapse arrow (▼)
- [ ] Empty/no-thread channels don't show arrow
- [ ] Channel icons (📌) are visible

#### 2. Expand/Collapse
- [ ] Click expand arrow → threads appear
- [ ] Click arrow again → threads collapse
- [ ] Threads are properly indented under parent
- [ ] Thread icons (🧵) are visible on threads

#### 3. Selection
- [ ] Click any channel → button turns blue (btn-primary)
- [ ] Click any thread → button turns blue
- [ ] "Select Stream" section appears after selection
- [ ] Only one channel/thread is selected at a time

#### 4. Stream Integration
- [ ] After selecting a channel, "Select Stream" section loads
- [ ] Can create new stream for selected channel/thread
- [ ] Stream is correctly associated with channel/thread ID
- [ ] Switching selection resets stream selection

#### 5. Accessibility
- [ ] Tab navigation works through channels/threads
- [ ] Keyboard arrow keys work for selection (optional enhancement)
- [ ] Screen reader announces channel and thread names
- [ ] Collapse/expand state is announced

## Step 4: Automated E2E Testing

### Run All QOTD Tree Tests

```bash
cd frontend

# Run only the channel tree tests
npm run test:e2e -- qotd-channel-tree.spec.ts

# Run with UI (debug mode)
npm run test:e2e -- --ui qotd-channel-tree.spec.ts

# Run with headed browser (watch interactions)
npm run test:e2e -- --headed qotd-channel-tree.spec.ts
```

### Test Coverage

The E2E test suite covers:

1. **Tree Rendering** - Verifies all channels appear
2. **Icons** - Checks for correct emoji icons
3. **Expand/Collapse** - Tests expand arrow functionality
4. **Thread Display** - Verifies threads appear under channels
5. **Channel Selection** - Tests selecting channels
6. **Thread Selection** - Tests selecting threads
7. **Selection State** - Verifies button highlighting
8. **Checkmarks** - Verifies enabled stream indicators
9. **State Persistence** - Tests collapsed state between selections

### Debug Test Issues

If tests fail, check:

```bash
# Verify test config was created
ls e2e/test-server-config.json

# Check test server is still set up
node scripts/setup-test-server.js <GUILD_ID>  # Re-run if needed

# Check bot has proper permissions
# In Discord, verify bot can see all channels/threads

# Check OAuth2 login works
# Manually test login at http://localhost:8080
```

## Step 5: Clean Up

When testing is complete, remove test channels:

```bash
node scripts/setup-test-server.js <YOUR_GUILD_ID> --cleanup
```

This removes:
- All test channels
- All test threads
- The test configuration file

## Advanced Testing

### Test with Different Channel Configurations

Modify `scripts/setup-test-server.js`:

```javascript
const TEST_CHANNELS = [
  // Add more channels/threads with different names
  // Test with very long channel names
  // Test with special characters in names
];
```

Re-run setup script to create new config.

### Test API Directly

Get channels/threads tree from API:

```bash
# Replace <GUILD_ID> and run after OAuth2 login
curl -b cookies.txt http://localhost:8080/api/servers/<GUILD_ID>/channel-options
```

Should return JSON with tree structure:

```json
[
  {
    "id": "123456",
    "name": "test-with-threads",
    "type": "CHANNEL",
    "children": [
      {
        "id": "789012",
        "name": "thread-1",
        "type": "THREAD",
        "children": []
      }
    ]
  }
]
```

### Performance Testing

Test with large numbers of channels/threads:

1. Modify setup script to create 50+ channels
2. Add 10+ threads per channel
3. Run E2E tests and measure load time
4. Monitor browser DevTools for render performance

## Troubleshooting

### Tests Can't Find Test Config

```
⚠️ Test config not found at frontend/e2e/test-server-config.json
```

**Solution:**
```bash
node scripts/setup-test-server.js <YOUR_GUILD_ID>
```

### Tree Not Showing in UI

**Checks:**
1. Bot is added to test server
2. Bot can see channels (check permissions in Discord)
3. You're logged in as admin
4. API returns channels at `/api/servers/<GUILD_ID>/channel-options`

### Threads Not Appearing

**Checks:**
1. Run setup script again
2. Check Discord server - threads should exist
3. Refresh browser page
4. Check browser console for errors

### Selection Not Working

**Checks:**
1. Click event listeners attached (check DevTools console)
2. No JavaScript errors in console
3. Button has proper `onClick` handler
4. State is updating (use React DevTools)

## CI/CD Integration

To run tests in CI/CD pipeline:

```yaml
# Example GitHub Actions
- name: Setup test server
  env:
    DISCORD_TOKEN: ${{ secrets.DISCORD_TEST_BOT_TOKEN }}
  run: node scripts/setup-test-server.js ${{ secrets.DISCORD_TEST_GUILD_ID }}

- name: Run E2E tests
  run: cd frontend && npm run test:e2e -- qotd-channel-tree.spec.ts

- name: Cleanup test server
  if: always()
  env:
    DISCORD_TOKEN: ${{ secrets.DISCORD_TEST_BOT_TOKEN }}
  run: node scripts/setup-test-server.js ${{ secrets.DISCORD_TEST_GUILD_ID }} --cleanup
```

## Reporting Issues

When reporting a channel tree bug, include:
1. Screenshot of the tree
2. Browser console errors
3. Test server structure (channels/threads created)
4. Steps to reproduce
5. Expected vs. actual behavior

Example issue:
```
Title: Channel threads not expanding

Steps to reproduce:
1. Navigate to QOTD config for test server
2. Scroll to "Select Channel or Thread" section
3. Click expand arrow on "test-with-2-threads"

Expected: Threads appear below channel
Actual: Nothing happens

Screenshot: [attached]
Browser: Chrome 130
```

## Feature Checklist

- [ ] Channels display with 📌 icon
- [ ] Threads display with 🧵 icon under parent channel
- [ ] Channels with threads show expand arrow (▼)
- [ ] Channels without threads don't show arrow
- [ ] Click arrow toggles thread visibility
- [ ] Click channel/thread selects it (blue highlight)
- [ ] Selection appears to work for stream creation
- [ ] Only one channel/thread selected at time
- [ ] UI is responsive on mobile
- [ ] Keyboard navigation works
- [ ] No console errors
- [ ] Performance is acceptable (tree loads < 1s)
- [ ] Works with 50+ channels
- [ ] Works with 10+ threads per channel
