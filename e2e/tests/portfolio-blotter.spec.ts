import { test, expect } from '@playwright/test';
import { signUpAndJoin, goToTab, pickWatchlistRowInGroup } from './helpers';

test.describe('Portfolio & blotter', () => {
  test('a limit order far from market stays NEW and can be cancelled from the blotter', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Market');
    await pickWatchlistRowInGroup(page, 'Equities');

    await page.locator('select[aria-label="Order type"]').selectOption('LIMIT');
    await page.locator('input[aria-label="Limit price"]').fill('0.01');
    await page.locator('input[aria-label="Quantity"]').fill('5');
    await page.locator('button', { hasText: /^BUY /}).click();
    await expect(page.locator('[role="status"]')).toBeVisible({ timeout: 8_000 });

    await goToTab(page, 'Blotter');
    const row = page.locator('table.tbl tbody tr').first();
    await expect(row).toContainText('NEW');

    await row.locator('button:has-text("cancel")').click();
    await expect(page.locator('table.tbl tbody tr').first()).toContainText('CANCELLED', { timeout: 8_000 });
  });

  test('the blotter lists both order and fill details for a filled market order', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Market');
    const symbol = await pickWatchlistRowInGroup(page, 'Equities');
    await page.locator('input[aria-label="Quantity"]').fill('7');
    await page.locator('button', { hasText: /^BUY /}).click();
    await expect(page.locator('[role="status"]')).toBeVisible({ timeout: 8_000 });

    await goToTab(page, 'Blotter');
    const row = page.locator('table.tbl tbody tr').first();
    await expect(row).toContainText(symbol);
    await expect(row).toContainText('BUY');
    await expect(row).toContainText('MARKET');
    await expect(row).toContainText('FILLED');
  });

  test('the portfolio positions table shows unrealized P&L that updates as the market moves', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Market');
    await pickWatchlistRowInGroup(page, 'Equities');
    await page.locator('input[aria-label="Quantity"]').fill('20');
    await page.locator('button', { hasText: /^BUY /}).click();
    await expect(page.locator('[role="status"]')).toBeVisible({ timeout: 8_000 });

    await goToTab(page, 'Portfolio');
    const posRow = page.locator('table.tbl tbody tr').first();
    await expect(posRow).toBeVisible();
    // Columns: Symbol, Type, Qty, Avg, Mark, Value, Unrlzd P&L, Rlzd P&L, ...
    // The Unrealized P&L cell should render a signed glyph via the shared
    // <Pnl> component (▲/▼/–), not be blank.
    await expect(posRow.locator('td').nth(6)).toContainText(/[▲▼–]/);
  });
});
