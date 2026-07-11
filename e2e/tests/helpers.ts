import { Page, BrowserContext, expect } from '@playwright/test';

let counter = 0;
/** Unique per test-run email so parallel specs never collide on the shared backend/H2 instance. */
export function uniqueEmail(prefix: string): string {
  counter += 1;
  return `${prefix}_${Date.now()}_${counter}@e2e.test`;
}

export interface SignedUpUser {
  email: string;
  displayName: string;
}

export async function signUp(
  page: Page,
  opts: { role?: 'STUDENT' | 'INSTRUCTOR'; displayName?: string; prefix?: string } = {}
): Promise<SignedUpUser> {
  const email = uniqueEmail(opts.prefix ?? 'user');
  const displayName = opts.displayName ?? 'E2E Tester';
  await page.goto('/');
  await page.click('text=Sign up');
  await page.fill('#displayName', displayName);
  if (opts.role === 'INSTRUCTOR') {
    await page.selectOption('#role', 'INSTRUCTOR');
  }
  await page.fill('#email', email);
  await page.fill('#password', 'e2e-test-pw-1');
  await page.click('button:has-text("Create account")');
  await expect(page.locator('text=Pick a market scenario')).toBeVisible({ timeout: 10_000 });
  return { email, displayName };
}

export async function logIn(page: Page, email: string, password = 'e2e-test-pw-1'): Promise<void> {
  await page.goto('/');
  // The "Log in" mode-toggle button and the form's submit button share the
  // same text when mode is already 'login' (the default) — disambiguate via
  // the submit button's distinct classes (w-full, no type="button").
  await page.fill('#email', email);
  await page.fill('#password', password);
  await page.locator('form button.w-full').click();
}

/** Joins the first scenario in the picker (defaults to whatever scenario is seeded first — "Calm market" in practice). */
export async function joinFirstScenario(page: Page): Promise<void> {
  const joinBtn = page.locator('button:has-text("Join with 100,000")').first();
  await joinBtn.click();
  await expect(page.locator('text=Paper Desk').first()).toBeVisible({ timeout: 10_000 });
}

/** Joins a scenario by its visible name (e.g. "High volatility", "Crash", "Bull market"). */
export async function joinScenarioNamed(page: Page, name: string): Promise<void> {
  const card = page.locator('.panel', { hasText: name });
  await card.locator('button:has-text("Join with 100,000")').click();
  await expect(page.locator('text=Paper Desk').first()).toBeVisible({ timeout: 10_000 });
}

export async function signUpAndJoin(
  page: Page,
  opts: { role?: 'STUDENT' | 'INSTRUCTOR'; displayName?: string; prefix?: string } = {}
): Promise<SignedUpUser> {
  const user = await signUp(page, opts);
  await joinFirstScenario(page);
  return user;
}

export async function goToTab(page: Page, label: string): Promise<void> {
  await page.locator('nav .tab', { hasText: label }).first().click();
}

/** Selects the first row of the Market watchlist matching a symbol group and loads it into the ticket. */
export async function pickFirstWatchlistRow(page: Page): Promise<string> {
  const row = page.locator('table.tbl tbody tr').first();
  const symbol = (await row.locator('td').first().innerText()).trim();
  await row.click();
  return symbol;
}

/**
 * Picks the first watchlist row under a specific asset-type group heading
 * (the Market tab groups the watchlist by "Equities" / "FX" / "Futures" /
 * "Forwards" / "Swaps" — see MarketView.tsx's GROUPS array).
 */
export async function pickWatchlistRowInGroup(page: Page, group: string): Promise<string> {
  const heading = page.locator('div', { hasText: group }).filter({ hasText: new RegExp(`^${group}$`, 'i') });
  const table = heading.locator('xpath=following-sibling::table[1]');
  const row = table.locator('tbody tr').first();
  await expect(row).toBeVisible({ timeout: 10_000 });
  const symbol = (await row.locator('td').first().innerText()).trim();
  await row.click();
  return symbol;
}

/** Picks a specific watchlist row by symbol substring within a group (e.g. the shorter-frequency swap). */
export async function pickWatchlistRowMatching(page: Page, group: string, symbolSubstring: string): Promise<string> {
  const heading = page.locator('div', { hasText: group }).filter({ hasText: new RegExp(`^${group}$`, 'i') });
  const table = heading.locator('xpath=following-sibling::table[1]');
  const row = table.locator('tbody tr', { hasText: symbolSubstring }).first();
  await expect(row).toBeVisible({ timeout: 10_000 });
  const symbol = (await row.locator('td').first().innerText()).trim();
  await row.click();
  return symbol;
}

export interface PlaceOrderOpts {
  side?: 'BUY' | 'SELL';
  qty?: number;
}

/** Assumes an instrument is already loaded into the order ticket (via a click elsewhere). */
export async function placeOrder(page: Page, opts: PlaceOrderOpts = {}): Promise<void> {
  const side = opts.side ?? 'BUY';
  if (side === 'SELL') {
    await page.locator('button:has-text("SELL")').click();
  }
  await page.locator('input[aria-label="Quantity"]').fill(String(opts.qty ?? 10));
  await page.locator('button', { hasText: new RegExp(`^${side} `) }).click();
  await expect(page.locator('[role="status"]')).toBeVisible({ timeout: 8_000 });
}

export async function waitForClockReady(page: Page): Promise<void> {
  await expect(page.locator('header')).toContainText('×', { timeout: 10_000 });
}
