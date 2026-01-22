# Channel Tree Feature - Quick Test Guide

## 🚀 Super Quick Start

```bash
# 1. Get your test server's ID (right-click server → Copy Server ID)
# Replace 123456789 with your actual guild ID

# 2. Setup test channels and threads
./test-server.sh 123456789

# 3. Start the application
./start.sh

# 4. Login and test
# Open http://localhost:8080
# Login with Discord → Select test server → Go to QOTD Config
```

## ✅ What You'll See

The QOTD Config page should show a tree like this:

```
Select Channel or Thread
┌─────────────────────────────────────────┐
│ 📌 test-empty-channel                   │
│ 📌 test-no-threads                      │
│ 📌 test-with-2-threads ▼                │
│   🧵 thread-1-discussion                │
│   🧵 thread-2-questions                 │
│ 📌 test-with-3-threads ▼                │
│   🧵 announcements-thread               │
│   🧵 bugs-thread                        │
│   🧵 feature-requests-thread            │
└─────────────────────────────────────────┘
```

## 🧪 Testing Checklist

- [ ] Channels show with 📌 icon
- [ ] Threads show with 🧵 icon
- [ ] Collapse/expand arrows work
- [ ] Can click to select channels
- [ ] Can click to select threads
- [ ] Selected item turns blue
- [ ] Stream section appears after selection

## 🤖 Run Automated Tests

```bash
cd frontend
npm run test:e2e -- qotd-channel-tree.spec.ts
```

## 🧹 Cleanup

When you're done testing:

```bash
./test-server.sh 123456789 cleanup
```

This removes all test channels from your Discord server.

## 📖 Full Documentation

For complete testing guide with advanced options, see: `docs/QOTD_TREE_TESTING.md`

## 🆘 Troubleshooting

**"Test config not found" error in E2E tests?**
```bash
node scripts/setup-test-server.js 123456789
```

**Threads not showing in tree?**
1. Check bot permissions in Discord
2. Refresh the page
3. Re-run setup script
4. Check browser console for errors

**Bot not appearing in server?**
1. Check DISCORD_TOKEN in .env is correct
2. Make sure bot is invited to test server
3. Restart the application

## 📝 Files Created

- `scripts/setup-test-server.js` - Automation script to create test channels
- `frontend/e2e/qotd-channel-tree.spec.ts` - Playwright E2E tests
- `test-server.sh` - Quick helper script
- `docs/QOTD_TREE_TESTING.md` - Complete testing guide
- `e2e/test-server-config.json` - Auto-generated test config (created by setup script)
