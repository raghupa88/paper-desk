import { test, expect } from '@playwright/test';
import { signUpAndJoin, pickWatchlistRowInGroup } from './helpers';

test.describe('Getting-started guide (first-run walkthrough)', () => {
  test('shows unchecked steps for a brand-new user, checks them off live, and auto-hides once the mission completes', async ({ page }) => {
    await signUpAndJoin(page);

    const guide = page.locator('[aria-label="Getting started guide"]');
    await expect(guide).toBeVisible({ timeout: 10_000 });
    await expect(guide).toContainText('Place a trade that fills');
    await expect(guide).toContainText('Hold at least one open position');
    // Neither step done yet -- both boxes unchecked.
    await expect(guide.locator('text=☑')).toHaveCount(0);

    await guide.locator('button:has-text("Go to Market")').click();
    await expect(page.locator('nav .tab', { hasText: 'Market' })).toHaveClass(/tab-active/);

    await pickWatchlistRowInGroup(page, 'Equities');
    await page.locator('input[aria-label="Quantity"]').fill('10');
    await page.locator('button', { hasText: /^BUY / }).click();
    await expect(page.locator('[role="status"]')).toBeVisible({ timeout: 8_000 });

    // Both FIRST_STEPS steps are now satisfied (a fill + an open position) --
    // the guide has done its job and should disappear on its own.
    await expect(guide).not.toBeVisible({ timeout: 10_000 });
  });

  test('dismissing the guide hides it and the dismissal persists across a reload', async ({ page }) => {
    await signUpAndJoin(page);
    const guide = page.locator('[aria-label="Getting started guide"]');
    await expect(guide).toBeVisible({ timeout: 10_000 });

    await guide.locator('button[aria-label="Dismiss getting started guide"]').click();
    await expect(guide).not.toBeVisible();

    await page.reload();
    await expect(page.locator('nav .tab', { hasText: 'Dashboard' })).toBeVisible({ timeout: 10_000 });
    await expect(guide).not.toBeVisible();
  });

  test('"Progress" button in the guide navigates to the Progress tab', async ({ page }) => {
    await signUpAndJoin(page);
    const guide = page.locator('[aria-label="Getting started guide"]');
    await expect(guide).toBeVisible({ timeout: 10_000 });

    await guide.locator('button:has-text("Progress")').click();
    await expect(page.locator('nav .tab', { hasText: 'Progress' })).toHaveClass(/tab-active/);
  });
});
