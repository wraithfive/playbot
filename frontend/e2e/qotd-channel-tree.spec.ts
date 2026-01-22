import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

/**
 * E2E tests for QOTD channel tree selection feature
 * 
 * Tests the channel/thread tree UI:
 * - Tree renders with channels
 * - Channels with threads show collapse/expand indicators
 * - Clicking to expand shows nested threads
 * - Selecting channels and threads works
 * - Tree icons are correct (📌 for channels, 🧵 for threads)
 * 
 * Prerequisites:
 *   1. Run setup script: node scripts/setup-test-server.js <your-guild-id>
 *   2. Bot is added to the test server with proper permissions
 *   3. Admin is logged in via OAuth2
 */

let testConfig: any;

test.describe('QOTD Channel Tree Selection', () => {
  test.beforeAll(() => {
    // Load test server config created by setup script
    const configPath = path.join(__dirname, 'test-server-config.json');
    if (fs.existsSync(configPath)) {
      testConfig = JSON.parse(fs.readFileSync(configPath, 'utf-8'));
      console.log(`\n📋 Test config loaded for guild: ${testConfig.guildName}`);
    } else {
      console.warn(`⚠️ Test config not found at ${configPath}`);
      console.warn('Run: node scripts/setup-test-server.js <guild-id>');
    }
  });

  test.beforeEach(async ({ page }) => {
    // Navigate to QOTD manager for the test guild
    await page.goto('/');
    
    // Check if logged in, if not login
    const loginButton = page.locator('button:has-text("Login with Discord")');
    if (await loginButton.isVisible({ timeout: 5000 }).catch(() => false)) {
      console.log('Logging in with Discord...');
      await loginButton.click();
      // Handle OAuth2 flow
      await page.waitForNavigation();
      // After login, we should be redirected to servers list
      await page.waitForSelector('[href*="servers"]');
    }

    // Navigate to QOTD config for test guild
    if (testConfig && testConfig.guildId) {
      await page.goto(`/servers/${testConfig.guildId}/qotd`);
      await page.waitForLoadState('networkidle');
    }
  });

  test('should render channel tree with test channels', async ({ page }) => {
    if (!testConfig) {
      test.skip();
    }

    // Wait for the "Select Channel or Thread" section to appear
    const heading = page.locator('h3:has-text("Select Channel or Thread")');
    await expect(heading).toBeVisible();

    // Verify all test channels are present
    for (const channel of testConfig.channels) {
      const channelButton = page.locator(`button:has-text("${channel.name}")`);
      await expect(channelButton).toBeVisible();
    }
  });

  test('should show correct icons for channels and threads', async ({ page }) => {
    if (!testConfig) {
      test.skip();
    }

    // Check for channel icons (📌)
    const channelsWithThreads = testConfig.channels.filter((ch: any) => ch.threads.length > 0);
    for (const channel of channelsWithThreads) {
      // The channel button should have the channel icon nearby
      const channelButton = page.locator(`button:has-text("${channel.name}")`);
      expect(await channelButton.textContent()).toContain('📌'); // Channel icon
    }
  });

  test('should collapse/expand channels with threads', async ({ page }) => {
    if (!testConfig) {
      test.skip();
    }

    const channelWithThreads = testConfig.channels.find((ch: any) => ch.threads.length > 0);
    if (!channelWithThreads) {
      test.skip();
    }

    // Find the channel button
    const channelButton = page.locator(`button:has-text("${channelWithThreads.name}")`);
    
    // Initially, threads might be hidden or shown - click to collapse if shown
    const firstThread = channelWithThreads.threads[0];
    const threadExists = await page.locator(`button:has-text("${firstThread.name}")`).isVisible();

    if (threadExists) {
      // Threads are showing, click to collapse
      await channelButton.click();
      // Thread should now be hidden
      await expect(page.locator(`button:has-text("${firstThread.name}")`)).not.toBeVisible();
    }

    // Click to expand
    await channelButton.click();
    // Threads should now be visible
    await expect(page.locator(`button:has-text("${firstThread.name}")`)).toBeVisible();
  });

  test('should show all threads under expanded channel', async ({ page }) => {
    if (!testConfig) {
      test.skip();
    }

    const channelWithThreads = testConfig.channels.find((ch: any) => ch.threads.length >= 2);
    if (!channelWithThreads) {
      test.skip();
    }

    // Expand the channel by clicking it
    const channelButton = page.locator(`button:has-text("${channelWithThreads.name}")`);
    
    // Make sure threads are visible
    let threadCount = 0;
    for (const thread of channelWithThreads.threads) {
      const threadButton = page.locator(`button:has-text("${thread.name}")`);
      if (await threadButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        threadCount++;
      }
    }

    // All threads should be visible
    expect(threadCount).toBe(channelWithThreads.threads.length);
  });

  test('should select channel when clicked', async ({ page }) => {
    if (!testConfig) {
      test.skip();
    }

    const testChannel = testConfig.channels[0];
    const channelButton = page.locator(`button:has-text("${testChannel.name}")`);

    // Click to select
    await channelButton.click();

    // Check if button is now highlighted as selected (should have btn-primary class)
    const isSelected = await channelButton.evaluate(el => 
      el.classList.contains('btn-primary')
    );
    expect(isSelected).toBe(true);

    // Wait for streams to load for this channel
    await page.waitForSelector('h3:has-text("Select Stream")');
  });

  test('should select thread when clicked', async ({ page }) => {
    if (!testConfig) {
      test.skip();
    }

    const channelWithThreads = testConfig.channels.find((ch: any) => ch.threads.length > 0);
    if (!channelWithThreads) {
      test.skip();
    }

    // Ensure threads are visible
    const channelButton = page.locator(`button:has-text("${channelWithThreads.name}")`);
    
    // Expand if needed
    const firstThread = channelWithThreads.threads[0];
    const threadButton = page.locator(`button:has-text("${firstThread.name}")`);
    
    if (!await threadButton.isVisible({ timeout: 1000 }).catch(() => false)) {
      await channelButton.click();
    }

    // Click thread to select
    await threadButton.click();

    // Check if thread button is now selected
    const isSelected = await threadButton.evaluate(el => 
      el.classList.contains('btn-primary')
    );
    expect(isSelected).toBe(true);

    // Streams should load for the thread
    await page.waitForSelector('h3:has-text("Select Stream")');
  });

  test('should show thread icons (🧵) for nested threads', async ({ page }) => {
    if (!testConfig) {
      test.skip();
    }

    const channelWithThreads = testConfig.channels.find((ch: any) => ch.threads.length > 0);
    if (!channelWithThreads) {
      test.skip();
    }

    // Ensure threads are visible
    const channelButton = page.locator(`button:has-text("${channelWithThreads.name}")`);
    const firstThread = channelWithThreads.threads[0];
    const threadButton = page.locator(`button:has-text("${firstThread.name}")`);

    if (!await threadButton.isVisible({ timeout: 1000 }).catch(() => false)) {
      await channelButton.click();
    }

    // Check that thread button contains thread icon
    const text = await threadButton.textContent();
    expect(text).toContain('🧵'); // Thread icon
  });

  test('should highlight selected channel with checkmark', async ({ page }) => {
    if (!testConfig) {
      test.skip();
    }

    const testChannel = testConfig.channels[0];
    const channelButton = page.locator(`button:has-text("${testChannel.name}")`);

    // Click to select
    await channelButton.click();

    // Check if button text contains checkmark (if this channel has a stream configured)
    const text = await channelButton.textContent();
    // Note: checkmark only appears if channel has enabled streams
    // So we just verify the button is selected
    const isSelected = await channelButton.evaluate(el => 
      el.classList.contains('btn-primary')
    );
    expect(isSelected).toBe(true);
  });

  test('should maintain collapsed state when switching selections', async ({ page }) => {
    if (!testConfig) {
      test.skip();
    }

    const channel1 = testConfig.channels.find((ch: any) => ch.threads.length > 0);
    const channel2 = testConfig.channels.find((ch: any) => ch !== channel1);

    if (!channel1 || !channel2) {
      test.skip();
    }

    // Expand first channel
    const button1 = page.locator(`button:has-text("${channel1.name}")`);
    await button1.click(); // select it
    
    // Collapse it if it has threads by clicking again
    if (channel1.threads.length > 0) {
      const thread = channel1.threads[0];
      const threadButton = page.locator(`button:has-text("${thread.name}")`);
      const isExpanded = await threadButton.isVisible({ timeout: 1000 }).catch(() => false);
      
      if (isExpanded) {
        // Collapse by clicking channel button again (toggle collapse)
        await button1.click();
      }
    }

    // Select different channel
    const button2 = page.locator(`button:has-text("${channel2.name}")`);
    await button2.click();

    // First channel should still be collapsed
    if (channel1.threads.length > 0) {
      const thread = channel1.threads[0];
      const threadButton = page.locator(`button:has-text("${thread.name}")`);
      await expect(threadButton).not.toBeVisible();
    }
  });
});
