# Build Integration - Testing

## Overview

Frontend tests are now fully integrated into the build process. When you run `./build.sh` from the project root, both backend (Maven) and frontend (npm) tests will run automatically.

## Build Script Integration

### Default Behavior
```bash
./build.sh
```

This will:
1. ✅ Run backend tests (Maven)
2. ✅ Run frontend tests (npm)
3. ✅ Build backend JAR
4. ✅ Build frontend production bundle

### Skip Tests
```bash
./build.sh --skip-tests
```

This will:
1. ❌ Skip backend tests
2. ❌ Skip frontend tests
3. ✅ Build backend JAR
4. ✅ Build frontend production bundle

## Frontend Test Execution

When tests are enabled, the build script runs:
```bash
npm run test:unit
```

This executes:
- **13 test files**
- **81 tests** covering:
  - Unit tests
  - Integration tests
  - Accessibility tests

### What Gets Tested

#### Components (100% coverage):
- App.tsx
- Login.tsx
- Navbar.tsx
- Footer.tsx
- PrivacyPolicy.tsx
- TermsOfService.tsx

#### API & Hooks (96%+ coverage):
- client.ts (41% - focused on critical paths)
- useWebSocket.ts (96.77%)

#### Integration Tests:
- ServerList full workflow
- User interactions
- API mocking
- State management

#### Accessibility Tests:
- WCAG compliance via axe-core
- Keyboard navigation
- ARIA labels
- Screen reader support

## TypeScript Compilation

The TypeScript compiler (`tsc -b`) is configured to:
- ✅ Compile source files in `src/`
- ❌ Exclude test files (`**/__tests__`, `*.test.ts`, `*.test.tsx`)
- ❌ Exclude E2E tests (`e2e/`)

This means tests are checked by Vitest's runtime type checking, not by the build-time TypeScript compiler.

## Build Options

### Clean Build
```bash
./build.sh --clean
```
Removes all build artifacts before building.

### Production Build
```bash
./build.sh --production
```
Builds with production optimizations and shows deployment checklist.

### Combined Options
```bash
./build.sh --clean --production --skip-tests
```

## CI/CD Integration

### Example GitHub Actions Workflow

```yaml
name: Build and Test

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Set up Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'

      - name: Build and Test
        run: ./build.sh

      - name: Upload artifacts
        uses: actions/upload-artifact@v3
        with:
          name: playbot-artifacts
          path: |
            target/playbot-1.0.0.jar
            frontend/dist/
```

### Example GitLab CI

```yaml
stages:
  - build
  - test

build:
  stage: build
  image: eclipse-temurin:21-jdk
  before_script:
    - curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
    - apt-get install -y nodejs
  script:
    - ./build.sh
  artifacts:
    paths:
      - target/playbot-1.0.0.jar
      - frontend/dist/
```

## Manual Test Execution

You can also run tests independently:

### Backend Tests Only
```bash
mvn test
```

### Frontend Tests Only
```bash
cd frontend

# Run tests (watch mode)
npm test

# Run once
npm run test:unit

# With coverage
npm run test:coverage

# E2E tests
npm run test:e2e

# All tests
npm run test:all
```

## Test Failure Handling

If tests fail during build:
1. Build process **stops immediately**
2. Error message indicates which test suite failed
3. Exit code 1 returned (fails CI/CD pipelines)
4. No production build is created

### Debugging Test Failures

```bash
# Run tests in watch mode to debug
cd frontend
npm test

# Run specific test file
npm test Login.test.tsx

# Run with verbose output
npm test -- --reporter=verbose

# Generate coverage report
npm run test:coverage
```

## Performance

Typical build times:
- **Backend tests:** ~10-30 seconds
- **Frontend tests:** ~2-3 seconds
- **Backend build:** ~20-40 seconds
- **Frontend build:** ~1-2 seconds
- **Total:** ~1-2 minutes

Use `--skip-tests` for quick builds during development (not recommended for deployments).

## Best Practices

### During Development
1. Run tests locally before committing:
   ```bash
   cd frontend && npm test
   ```

2. Fix failures before pushing

3. Use watch mode for TDD:
   ```bash
   npm test
   ```

### Before Deployment
1. Always run full build with tests:
   ```bash
   ./build.sh
   ```

2. Review coverage report:
   ```bash
   cd frontend && npm run test:coverage
   ```

3. Run E2E tests:
   ```bash
   cd frontend && npm run test:e2e
   ```

### In CI/CD
1. **Never skip tests** in CI/CD pipelines
2. Upload test results and coverage reports
3. Fail the build on test failures
4. Generate test reports for review

## Troubleshooting

### Tests Pass Locally But Fail in CI
- Check Node.js version matches
- Verify all dependencies are installed
- Check for environment-specific issues
- Review CI logs for details

### TypeScript Errors During Build
- Test files are excluded from `tsc` compilation
- Check `tsconfig.app.json` excludes are correct
- Verify `@types/jest-axe` is installed

### Slow Test Execution
- Tests should complete in 2-3 seconds
- If slower, check for:
  - Network calls (should be mocked)
  - Large fixtures
  - Inefficient test setup

## Additional Resources

- [Main Testing Guide](./TESTING.md) - Detailed testing documentation
- [Vitest Docs](https://vitest.dev/)
- [Testing Library](https://testing-library.com/)
- [Playwright](https://playwright.dev/)
