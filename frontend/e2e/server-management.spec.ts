import { test, expect } from '@playwright/test';

test.describe('Server Management', () => {
  test.beforeEach(async ({ page }) => {
    // Mock authenticated user
    await page.route('**/api/servers', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'server-with-bot',
            name: 'Server With Bot',
            iconUrl: 'https://example.com/icon1.png',
            botIsPresent: true,
          },
          {
            id: 'server-without-bot',
            name: 'Server Without Bot',
            iconUrl: null,
            botIsPresent: false,
          },
        ]),
      });
    });
  });

  test('should display servers with bot in available section', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByText('Available Servers')).toBeVisible();
    await expect(page.getByText('Server With Bot')).toBeVisible();
  });

  test('should display servers without bot in invite section', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByText('Add Bot to These Servers')).toBeVisible();
    await expect(page.getByText('Server Without Bot')).toBeVisible();
    await expect(page.getByText('Bot not present')).toBeVisible();
  });

  test('should show invite button for servers without bot', async ({ page }) => {
    await page.goto('/');

    const inviteButton = page.getByRole('button', { name: /invite bot/i });
    await expect(inviteButton).toBeVisible();
  });

  test('should show remove bot button for servers with bot', async ({ page }) => {
    await page.goto('/');

    const removeButton = page.getByRole('button', { name: /remove bot/i });
    await expect(removeButton).toBeVisible();
  });

  test('should navigate to role manager when clicking on server', async ({ page }) => {
    // Mock roles API
    await page.route('**/api/servers/server-with-bot/roles', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route('**/api/servers/server-with-bot/roles/hierarchy-check', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          isCorrect: true,
          botRolePosition: 10,
          highestGachaRolePosition: 5,
        }),
      });
    });

    await page.route('**/api/servers/server-with-bot/qotd/configs', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto('/');
    await page.click('text=Server With Bot');

    await expect(page.url()).toContain('/servers/server-with-bot');
  });

  test('should show empty state when no servers available', async ({ page }) => {
    await page.route('**/api/servers', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto('/');

    await expect(page.getByText('No Servers Found')).toBeVisible();
    await expect(page.getByText(/you don't have administrator permissions/i)).toBeVisible();
  });

  test('should display server icon when available', async ({ page }) => {
    await page.goto('/');

    const serverCard = page.locator('text=Server With Bot').locator('..');
    const img = serverCard.locator('img');

    await expect(img).toHaveAttribute('src', 'https://example.com/icon1.png');
    await expect(img).toHaveAttribute('alt', 'Server With Bot');
  });

  test('should display server initial when no icon', async ({ page }) => {
    await page.goto('/');

    const serverCard = page.locator('text=Server Without Bot').locator('..');
    await expect(serverCard.locator('text=S')).toBeVisible(); // First letter of server name
  });

  test('should show role hierarchy warning', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByText(/drag the playbot role/i)).toBeVisible();
    await expect(page.getByText(/above all gacha roles/i)).toBeVisible();
  });
});
