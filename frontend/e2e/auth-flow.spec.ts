import { test, expect } from '@playwright/test';

test.describe('Authentication Flow', () => {
  test('should display login page for unauthenticated users', async ({ page }) => {
    // Mock the API to return 401 for unauthenticated request
    await page.route('**/api/servers', (route) => {
      route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Unauthorized' }),
      });
    });

    await page.goto('/');

    // Should see login page
    await expect(page.getByText('Playbot')).toBeVisible();
    await expect(page.getByText('Admin Panel')).toBeVisible();
    await expect(page.getByRole('button', { name: /login with discord/i })).toBeVisible();
  });

  test('should show legal links on login page', async ({ page }) => {
    await page.route('**/api/servers', (route) => {
      route.fulfill({ status: 401 });
    });

    await page.goto('/');

    const privacyLink = page.getByRole('link', { name: /privacy policy/i });
    const termsLink = page.getByRole('link', { name: /terms of service/i });

    await expect(privacyLink).toBeVisible();
    await expect(termsLink).toBeVisible();
  });

  test('should navigate to privacy policy', async ({ page }) => {
    await page.route('**/api/servers', (route) => {
      route.fulfill({ status: 401 });
    });

    await page.goto('/');
    await page.click('text=Privacy Policy');

    await expect(page.url()).toContain('/privacy');
    await expect(page.getByText(/privacy policy/i)).toBeVisible();
  });

  test('should navigate to terms of service', async ({ page }) => {
    await page.route('**/api/servers', (route) => {
      route.fulfill({ status: 401 });
    });

    await page.goto('/');
    await page.click('text=Terms of Service');

    await expect(page.url()).toContain('/terms');
    await expect(page.getByText(/terms of service/i)).toBeVisible();
  });

  test('should show server list when authenticated', async ({ page }) => {
    // Mock authenticated API response
    await page.route('**/api/servers', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: '123',
            name: 'Test Server',
            iconUrl: null,
            botIsPresent: true,
          },
        ]),
      });
    });

    await page.goto('/');

    // Should see server list
    await expect(page.getByText('Your Servers')).toBeVisible();
    await expect(page.getByText('Test Server')).toBeVisible();
  });

  test('should show navbar when authenticated', async ({ page }) => {
    await page.route('**/api/servers', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto('/');

    await expect(page.getByText('Playbot Admin')).toBeVisible();
    await expect(page.getByRole('button', { name: /logout/i })).toBeVisible();
  });

  test('should not show navbar when not authenticated', async ({ page }) => {
    await page.route('**/api/servers', (route) => {
      route.fulfill({ status: 401 });
    });

    await page.goto('/');

    await expect(page.getByText('Playbot Admin')).not.toBeVisible();
    await expect(page.getByRole('button', { name: /logout/i })).not.toBeVisible();
  });
});
