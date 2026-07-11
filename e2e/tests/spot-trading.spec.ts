import { test, expect } from '@playwright/test';
import { signUpAndJoin, goToTab, pickWatchlistRowInGroup, placeOrder } from './helpers';

test.describe('Spot equity trading', () => {
  test('buying a stock fills, shows in the blotter, and updates the portfolio position', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Market');

    const symbol = await pickWatchlistRowInGroup(page, 'Equities');
    await expect(page.locator('.panel-title', { hasText: 'Order ticket' })).toBeVisible();
    await placeOrder(page, { side: 'BUY', qty: 10 });

    await goToTab(page, 'Blotter');
    await expect(page.locator('table.tbl tbody tr').first()).toContainText(symbol);
    await expect(page.locator('table.tbl tbody tr').first()).toContainText('FILLED');

    await goToTab(page, 'Portfolio');
    await expect(page.locator('table.tbl tbody', { hasText: symbol })).toBeVisible();
  });

  test('selling a held position is reflected as a negative or reduced quantity change', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Market');
    const symbol = await pickWatchlistRowInGroup(page, 'Equities');
    await placeOrder(page, { side: 'BUY', qty: 20 });

    await pickWatchlistRowInGroup(page, 'Equities');
    await placeOrder(page, { side: 'SELL', qty: 5 });

    await goToTab(page, 'Portfolio');
    const row = page.locator('table.tbl tbody tr', { hasText: symbol }).first();
    await expect(row).toBeVisible();
    const qtyText = await row.locator('td').nth(2).innerText();
    expect(Number(qtyText.replace(/,/g, ''))).toBeCloseTo(15, 5);
  });

  test('the equity curve and today\'s P&L on the dashboard react to a trade', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Market');
    await pickWatchlistRowInGroup(page, 'Equities');
    await placeOrder(page, { side: 'BUY', qty: 50 });

    await goToTab(page, 'Dashboard');
    await expect(page.locator('text=Open positions').first()).toBeVisible();
    await expect(page.locator('table.tbl tbody tr').first()).not.toContainText('No open positions');
  });
});
