import { test, expect } from '@playwright/test';
import { signUpAndJoin, goToTab, placeOrder } from './helpers';

test.describe('FX', () => {
  test('FX Trader: buying a pair at market fills and shows in the FX book', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'FX Trader');

    await expect(page.locator('.panel-title', { hasText: 'Pairs' })).toBeVisible({ timeout: 10_000 });
    const pairRow = page.locator('table.tbl tbody tr').first();
    const pairSymbol = (await pairRow.locator('td').first().innerText()).trim();
    await pairRow.click();

    await placeOrder(page, { side: 'BUY', qty: 10000 });

    await goToTab(page, 'FX Trader');
    const bookPanel = page.locator('.panel', { hasText: 'FX book' });
    await expect(bookPanel.locator('table.tbl tbody')).toContainText(pairSymbol, { timeout: 10_000 });
  });

  test('FX Trader: the spot risk ladder renders rows around the current spot', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'FX Trader');
    const pairRow = page.locator('table.tbl tbody tr').first();
    await pairRow.click();
    await placeOrder(page, { side: 'BUY', qty: 10000 });

    await goToTab(page, 'FX Trader');
    const ladderPanel = page.locator('.panel', { hasText: 'risk ladder' }).first();
    await expect(ladderPanel).toBeVisible({ timeout: 10_000 });
    const ladderRows = ladderPanel.locator('table.tbl tbody tr');
    await expect(ladderRows.first()).toBeVisible();
    expect(await ladderRows.count()).toBeGreaterThanOrEqual(5);
  });

  test('FX Trader: buying an FX option from the dealer-mid chain loads Greeks and fills tagged TRADER', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'FX Trader');

    const chainPanel = page.locator('.panel', { hasText: 'FX options chain' });
    await expect(chainPanel).toBeVisible({ timeout: 10_000 });
    const rows = chainPanel.locator('table.tbl tbody tr');
    await expect(rows.first()).toBeVisible({ timeout: 10_000 });

    // Click a call premium cell (5th column: Delta, Gamma, Theta, Vega, Premium).
    await rows.nth(2).locator('td').nth(4).click();

    await expect(page.locator('.panel-title', { hasText: 'Order ticket' })).toBeVisible();
    await expect(page.locator('text=(trader view)')).toBeVisible();
    await placeOrder(page, { side: 'BUY', qty: 1 });

    await goToTab(page, 'Blotter');
    const firstRow = page.locator('table.tbl tbody tr').first();
    await expect(firstRow).toContainText('FILLED');
    await expect(firstRow).toContainText('TRADER');
  });

  test('FX Sales: quoting and executing an RFQ books a deal with the sales all-in price', async ({ page }) => {
    await signUpAndJoin(page);
    await goToTab(page, 'FX Sales');

    await expect(page.locator('.panel-title', { hasText: 'Client RFQ' })).toBeVisible({ timeout: 10_000 });
    await page.click('button:has-text("Quote client")');

    await expect(page.locator('text=All-in client price')).toBeVisible({ timeout: 10_000 });
    await page.click('button:has-text("Execute deal at all-in")');

    await expect(page.locator('text=Last deal captured')).toBeVisible({ timeout: 10_000 });

    await goToTab(page, 'Blotter');
    const firstRow = page.locator('table.tbl tbody tr').first();
    await expect(firstRow).toContainText('SALES');
  });
});
