import { test, expect } from '@playwright/test';
import { signUpAndJoin, goToTab, placeOrder } from './helpers';

test.describe('Options chain trading', () => {
  test('buying a call from the chain loads Greeks into the ticket and fills', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Options Chain');

    await expect(page.locator('text=spot').first()).toBeVisible({ timeout: 10_000 });
    const rows = page.locator('table.tbl tbody tr');
    await expect(rows.first()).toBeVisible({ timeout: 10_000 });

    // Click a call premium cell (5th column: Delta, Gamma, Theta, Vega, Premium).
    const targetRow = rows.nth(2);
    await targetRow.locator('td').nth(4).click();

    await expect(page.locator('.panel-title', { hasText: 'Order ticket' })).toBeVisible();
    await expect(page.locator('text=/Δ [-\\d.]/')).toBeVisible();
    await expect(page.locator('text=Theta/day')).toBeVisible();

    await placeOrder(page, { side: 'BUY', qty: 1 });

    await goToTab(page, 'Blotter');
    await expect(page.locator('table.tbl tbody tr').first()).toContainText('FILLED');

    await goToTab(page, 'Portfolio');
    const posRow = page.locator('table.tbl tbody tr').first();
    await expect(posRow).toContainText('OPTION');
  });

  test('switching expiry changes the rendered chain', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'Options Chain');

    const expiryButtons = page.locator('button.text-xs.btn, button', { hasText: /^\d{4}-\d{2}-\d{2}$/ });
    await expect(expiryButtons.first()).toBeVisible({ timeout: 10_000 });
    const count = await expiryButtons.count();
    expect(count).toBeGreaterThanOrEqual(2);

    await expiryButtons.nth(1).click();
    await expect(expiryButtons.nth(1)).toHaveClass(/btn-accent/);
  });
});
