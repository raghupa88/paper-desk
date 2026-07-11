import { test, expect } from '@playwright/test';
import { signUpAndJoin, goToTab, pickWatchlistRowInGroup, pickWatchlistRowMatching, placeOrder } from './helpers';

test.describe('Forwards & swaps (teaching instruments)', () => {
  test('a forward requires no initial margin (contrast with futures) and fills', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Market');

    const symbol = await pickWatchlistRowInGroup(page, 'Forwards');
    await expect(page.locator('.panel-title', { hasText: 'Order ticket' })).toBeVisible();
    await expect(page.locator('text=Initial margin required')).not.toBeVisible();
    await placeOrder(page, { side: 'BUY', qty: 1 });

    await goToTab(page, 'Portfolio');
    const posRow = page.locator('table.tbl tbody tr', { hasText: symbol }).first();
    await expect(posRow).toContainText('FORWARD');
  });

  test('a swap ticket shows notional and fixed rate, and buying is "pay fixed"', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Market');

    const symbol = await pickWatchlistRowInGroup(page, 'Swaps');
    await expect(page.locator('text=Notional')).toBeVisible({ timeout: 8_000 });
    await expect(page.locator('text=Fixed rate')).toBeVisible();
    await expect(page.locator('text=pay fixed / receive floating')).toBeVisible();

    await placeOrder(page, { side: 'BUY', qty: 1 });

    await goToTab(page, 'Portfolio');
    const posRow = page.locator('table.tbl tbody tr', { hasText: symbol }).first();
    await expect(posRow).toContainText('SWAP');
  });

  test('stepping sim days on an open swap posts a SWAP_FIXING settlement', async ({ page }) => {
    test.setTimeout(60_000);
    await signUpAndJoin(page);
    await goToTab(page, 'Market');
    // IRS-6M fixes monthly (payFreqMonths=1, ~30 sim days) — the shortest of
    // the two seeded swaps, so it needs the fewest "+1 day" steps to observe
    // a fixing (IRS-1Y fixes quarterly, ~90 days).
    await pickWatchlistRowMatching(page, 'Swaps', 'IRS-6M');
    await placeOrder(page, { side: 'BUY', qty: 1 });

    for (let i = 0; i < 31; i++) {
      await page.click('button:has-text("+1 day")');
      await page.waitForTimeout(300);
    }

    await goToTab(page, 'Portfolio');
    const settlementsPanel = page.locator('.panel', { hasText: 'Settlements' });
    await expect(settlementsPanel.locator('table.tbl tbody')).toContainText(/SWAP_FIXING/, { timeout: 20_000 });
  });
});
