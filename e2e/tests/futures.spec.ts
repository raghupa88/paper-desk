import { test, expect } from '@playwright/test';
import { signUpAndJoin, goToTab, pickWatchlistRowInGroup, placeOrder } from './helpers';

test.describe('Futures: margin and daily mark-to-market', () => {
  test('opening a futures position holds initial margin, and stepping a sim day posts a FUTURES_MTM settlement', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Market');

    const symbol = await pickWatchlistRowInGroup(page, 'Futures');
    await expect(page.locator('text=Initial margin required')).toBeVisible({ timeout: 8_000 });
    await placeOrder(page, { side: 'BUY', qty: 1 });

    await goToTab(page, 'Portfolio');
    await expect(page.locator('text=/margin held [$]?[1-9]/')).toBeVisible({ timeout: 8_000 });
    const posRow = page.locator('table.tbl tbody tr', { hasText: symbol }).first();
    await expect(posRow).toBeVisible();

    // Advance the sim clock a couple of days to trigger daily MTM settlement.
    for (let i = 0; i < 2; i++) {
      await page.click('button:has-text("+1 day")');
      await page.waitForTimeout(1500);
    }

    await goToTab(page, 'Portfolio');
    const settlementsPanel = page.locator('.panel', { hasText: 'Settlements' });
    await expect(settlementsPanel).toBeVisible();
    await expect(settlementsPanel.locator('table.tbl tbody')).toContainText(/FUTURES_MTM/, { timeout: 15_000 });
  });
});
