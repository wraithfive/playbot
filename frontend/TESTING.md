# Frontend Testing Guide

## Overview

The Playbot frontend has comprehensive test coverage across unit tests, integration tests, accessibility tests, and end-to-end tests.

## Test Statistics

### Current Coverage
- **Overall Coverage:** 24.4%
- **Test Files:** 13 (all passing)
- **Total Tests:** 81 (all passing)

### Component Coverage
- **App.tsx:** 100% coverage
- **Login.tsx:** 100% coverage
- **Navbar.tsx:** 100% coverage
- **Footer.tsx:** 100% coverage
- **PrivacyPolicy.tsx:** 100% coverage
- **TermsOfService.tsx:** 100% coverage
- **useWebSocket.ts:** 96.77% coverage
- **ServerList.tsx:** 61.22% coverage
- **client.ts:** 41.02% coverage

## Test Types

### 1. Unit Tests
Located in `src/**/__tests__/`

**Component Tests:**
- `Login.test.tsx` - 7 tests covering rendering, interactions, and OAuth flow
- `Navbar.test.tsx` - 4 tests covering logout functionality
- `Footer.test.tsx` - 2 tests for footer rendering
- `PrivacyPolicy.test.tsx` - 3 tests for legal content
- `TermsOfService.test.tsx` - 3 tests for legal content

**API Tests:**
- `client.test.ts` - 14 tests covering CSRF tokens, auth, and API methods

**Hook Tests:**
- `useWebSocket.test.ts` - 10 tests for WebSocket lifecycle and messaging

**App Tests:**
- `App.test.tsx` - 8 tests for routing and authentication state

### 2. Integration Tests
Located in `src/components/__tests__/*.integration.test.tsx`

**ServerList Integration Tests:** 10 tests covering:
- Server list loading and display
- Available vs unavailable servers
- Bot invite/remove functionality
- Error states and empty states
- Icon display and server navigation

### 3. Accessibility Tests
Located in `src/components/__tests__/accessibility.test.tsx`

**17 Accessibility Tests covering:**
- WCAG compliance using axe-core
- Proper ARIA labels and roles
- Heading hierarchy
- Keyboard navigation
- Screen reader compatibility
- Button and link accessibility

### 4. End-to-End Tests
Located in `e2e/`

**Authentication Flow (7 tests):**
- Login page display for unauthenticated users
- Legal links navigation
- Server list display when authenticated
- Navbar conditional rendering

**Server Management (10 tests):**
- Server list with/without bot
- Invite bot functionality
- Remove bot button
- Navigation to role manager
- Empty states
- Server icons

**Role Management (8 tests):**
- Role list display
- Hierarchy warnings
- Create role form
- Navigation
- QOTD button
- Empty states

## Running Tests

### Unit Tests
```bash
# Run all unit tests
npm test

# Run tests once (no watch mode)
npm run test:unit

# Run with coverage report
npm run test:coverage
```

### Integration Tests
Integration tests run automatically with unit tests as they use the same test runner.

### Accessibility Tests
```bash
# Included in unit test suite
npm test
```

### End-to-End Tests
```bash
# Run E2E tests headless
npm run test:e2e

# Run E2E tests with UI
npm run test:e2e:ui

# Run all tests (unit + E2E)
npm run test:all
```

## Test Best Practices

### Unit Tests
- Mock external dependencies (API calls, WebSocket)
- Test user interactions with `@testing-library/user-event`
- Focus on behavior, not implementation
- Use descriptive test names

### Integration Tests
- Test component interactions
- Mock at the boundary (API/network layer)
- Test real user workflows
- Verify state changes and side effects

### Accessibility Tests
- Run axe on all interactive components
- Test keyboard navigation
- Verify proper ARIA attributes
- Check heading hierarchy

### E2E Tests
- Mock API responses for consistent testing
- Test critical user journeys
- Keep tests focused and independent
- Use semantic queries (role, label, text)

## Test Configuration

### Vitest Config
Location: `vitest.config.ts`

Features:
- jsdom environment for browser APIs
- Coverage with v8
- Global test utilities
- Automatic test discovery

### Playwright Config
Location: `playwright.config.ts`

Features:
- Chromium browser
- Auto-start dev server
- Screenshot on failure
- Trace on retry

## CI/CD Integration

Add to your CI pipeline:

```yaml
# .github/workflows/test.yml
- name: Run Unit Tests
  run: npm run test:coverage

- name: Run E2E Tests
  run: npm run test:e2e
```

## Coverage Goals

### Current State
- ✅ Simple components: 100% coverage
- ✅ Hooks: 96%+ coverage
- ✅ API client: 40%+ coverage
- ⚠️ Complex components: 18-61% coverage

### Rationale
Complex interactive components (RoleManager, QotdManager) have lower coverage because:
1. They require extensive mocking (React Query, WebSocket, Router)
2. Heavy mocking makes tests brittle and tests implementation over behavior
3. E2E tests provide better coverage for these complex workflows

## Adding New Tests

### For New Components
1. Create `ComponentName.test.tsx` in same directory
2. Add basic rendering test
3. Add user interaction tests
4. Add accessibility test to `accessibility.test.tsx`

### For New Features
1. Add unit tests for business logic
2. Add integration test for feature workflow
3. Add E2E test for critical user path
4. Verify accessibility compliance

## Troubleshooting

### Tests Timing Out
- Increase timeout in test
- Check for unresolved promises
- Verify API mocks are set up correctly

### Accessibility Violations
- Check axe results for specific violations
- Verify ARIA attributes
- Test keyboard navigation manually
- Use Chrome DevTools Lighthouse

### E2E Tests Failing
- Check if dev server is running
- Verify API mocks in test
- Check for race conditions
- Look at screenshots in `playwright-report/`

## Resources

- [Vitest Documentation](https://vitest.dev/)
- [Testing Library](https://testing-library.com/)
- [Playwright](https://playwright.dev/)
- [jest-axe](https://github.com/nickcolley/jest-axe)
- [WCAG Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
