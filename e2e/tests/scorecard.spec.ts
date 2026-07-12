import { test, expect } from '@playwright/test';
import { signUpAndJoin, goToTab, pickWatchlistRowInGroup, placeOrder } from './helpers';

test.describe('Trader scorecard', () => {
  test('shows an empty state before any trade is closed', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Scorecard');
    await expect(page.locator('text=No closed trades yet')).toBeVisible({ timeout: 10_000 });
  });

  test('closing a position records a trade and updates win rate / avg loss / holding period', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Market');
    await pickWatchlistRowInGroup(page, 'Equities');
    await placeOrder(page, { side: 'BUY', qty: 10 });
    // Same instrument stays loaded in the ticket -- close it straight away.
    await placeOrder(page, { side: 'SELL', qty: 10 });

    await goToTab(page, 'Scorecard');
    await expect(page.locator('text=Closed trades')).toBeVisible({ timeout: 10_000 });
    const closedTradesCard = page.locator('.panel', { hasText: 'Closed trades' });
    await expect(closedTradesCard).toContainText('1');

    // The sim clock free-runs in the background between the two fills, so
    // which side of the spread/drift wins isn't deterministic here (unlike
    // the no-engine-tick backend unit test) -- assert the shape, not a sign.
    const winLossText = await page.locator('.panel', { hasText: 'Wins / losses' }).innerText();
    expect(winLossText).toMatch(/(1 \/ 0|0 \/ 1)/);
    const winRateText = await page.locator('.panel', { hasText: 'Win rate' }).innerText();
    expect(winRateText).toMatch(/(0\.0|100\.0)%/);
    const holdingText = await page.locator('.panel', { hasText: 'Avg holding period' }).innerText();
    expect(holdingText).toMatch(/\d+\.\d[hd]/);
  });
});
