import { test, expect } from '@playwright/test';
import { signUpAndJoin, goToTab, pickWatchlistRowInGroup } from './helpers';

test.describe('Gamification: XP, missions, badges', () => {
  test('placing a first trade completes the first-steps mission and earns a badge', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Market');
    await pickWatchlistRowInGroup(page, 'Equities');
    await page.locator('input[aria-label="Quantity"]').fill('10');
    await page.locator('button', { hasText: /^BUY /}).click();
    await expect(page.locator('[role="status"]')).toBeVisible({ timeout: 8_000 });

    await goToTab(page, 'Progress');
    await expect(page.locator('.panel-title', { hasText: 'Missions' })).toBeVisible({ timeout: 10_000 });
    // At least one mission should show the completed checkmark after a trade.
    await expect(page.locator('text=✅').first()).toBeVisible({ timeout: 10_000 });

    const earnedPanel = page.locator('.panel-title', { hasText: 'Earned' }).locator('xpath=..');
    await expect(earnedPanel).not.toContainText('Nothing yet');
  });

  test('the header shows a level badge and XP progress after activity', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Market');
    await pickWatchlistRowInGroup(page, 'Equities');
    await page.locator('input[aria-label="Quantity"]').fill('10');
    await page.locator('button', { hasText: /^BUY /}).click();
    await expect(page.locator('[role="status"]')).toBeVisible({ timeout: 8_000 });

    // The header shows the numeric level in a circle badge inside a
    // container div whose title attribute carries the "N XP" tooltip text
    // (not visible body text) plus an XP progress bar next to it.
    const levelBadge = page.locator('header div[title*="XP"]');
    await expect(levelBadge).toBeVisible({ timeout: 8_000 });
    await expect(levelBadge).toHaveAttribute('title', /\d+ XP/);
  });

  test('a toast notification appears for a fill', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Market');
    await pickWatchlistRowInGroup(page, 'Equities');
    await page.locator('input[aria-label="Quantity"]').fill('10');
    await page.locator('button', { hasText: /^BUY /}).click();

    await expect(page.locator('text=Order filled')).toBeVisible({ timeout: 8_000 });
  });
});
