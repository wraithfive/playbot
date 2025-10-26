import { test, expect } from '@playwright/test';

test.describe('Role Management', () => {
  test.beforeEach(async ({ page }) => {
    // Mock authenticated user and server list
    await page.route('**/api/servers', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'test-server',
            name: 'Test Server',
            iconUrl: null,
            botIsPresent: true,
          },
        ]),
      });
    });

    await page.route('**/api/servers/test-server/qotd/configs', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });
  });

  test('should display role list', async ({ page }) => {
    await page.route('**/api/servers/test-server/roles', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'role-1',
            name: 'gacha:legendary:Rainbow',
            rarity: 'LEGENDARY',
            colorHex: '#FF0000',
            position: 10,
          },
          {
            id: 'role-2',
            name: 'gacha:epic:Gold',
            rarity: 'EPIC',
            colorHex: '#FFD700',
            position: 9,
          },
        ]),
      });
    });

    await page.route('**/api/servers/test-server/roles/hierarchy-check', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          isCorrect: true,
          botRolePosition: 15,
          highestGachaRolePosition: 10,
        }),
      });
    });

    await page.goto('/servers/test-server');

    await expect(page.getByText('gacha:legendary:Rainbow')).toBeVisible();
    await expect(page.getByText('gacha:epic:Gold')).toBeVisible();
  });

  test('should show hierarchy warning when incorrect', async ({ page }) => {
    await page.route('**/api/servers/test-server/roles', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route('**/api/servers/test-server/roles/hierarchy-check', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          isCorrect: false,
          botRolePosition: 5,
          highestGachaRolePosition: 10,
        }),
      });
    });

    await page.goto('/servers/test-server');

    await expect(page.getByText(/role hierarchy issue/i)).toBeVisible();
  });

  test('should display create role form', async ({ page }) => {
    await page.route('**/api/servers/test-server/roles', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route('**/api/servers/test-server/roles/hierarchy-check', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ isCorrect: true }),
      });
    });

    await page.goto('/servers/test-server');

    await expect(page.getByText(/create new role/i)).toBeVisible();
  });

  test('should show back to servers button', async ({ page }) => {
    await page.route('**/api/servers/test-server/roles', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route('**/api/servers/test-server/roles/hierarchy-check', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ isCorrect: true }),
      });
    });

    await page.goto('/servers/test-server');

    const backButton = page.getByRole('button', { name: /back to servers/i });
    await expect(backButton).toBeVisible();
  });

  test('should navigate back to server list', async ({ page }) => {
    await page.route('**/api/servers/test-server/roles', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route('**/api/servers/test-server/roles/hierarchy-check', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ isCorrect: true }),
      });
    });

    await page.goto('/servers/test-server');

    await page.click('text=Back to Servers');

    await expect(page.url()).not.toContain('/servers/test-server');
    await expect(page.getByText('Your Servers')).toBeVisible();
  });

  test('should display QOTD navigation button', async ({ page }) => {
    await page.route('**/api/servers/test-server/roles', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route('**/api/servers/test-server/roles/hierarchy-check', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ isCorrect: true }),
      });
    });

    await page.goto('/servers/test-server');

    const qotdButton = page.getByRole('button', { name: /manage qotd/i });
    await expect(qotdButton).toBeVisible();
  });

  test('should show empty state when no roles', async ({ page }) => {
    await page.route('**/api/servers/test-server/roles', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route('**/api/servers/test-server/roles/hierarchy-check', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ isCorrect: true }),
      });
    });

    await page.goto('/servers/test-server');

    await expect(page.getByText(/no roles found/i)).toBeVisible();
  });
});
