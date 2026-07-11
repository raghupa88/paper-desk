import { test, expect } from '@playwright/test';
import { signUp, joinFirstScenario } from './helpers';

test.describe('Scenario picker', () => {
  test('lists multiple seeded scenarios to choose from', async ({ page }) => {
    await signUp(page);
    const cards = page.locator('.panel', { hasText: 'Join with 100,000' });
    await expect(cards.first()).toBeVisible({ timeout: 10_000 });
    expect(await cards.count()).toBeGreaterThanOrEqual(2);
  });

  test('joining shows starting cash of 100,000 on the dashboard', async ({ page }) => {
    await signUp(page);
    await joinFirstScenario(page);
    await expect(page.locator('text=/100,000/').first()).toBeVisible({ timeout: 10_000 });
  });

  test('"+ scenario" lets an already-active user add a second scenario account', async ({ page }) => {
    await signUp(page);
    await joinFirstScenario(page);
    await page.click('button:has-text("+ scenario")');
    await expect(page.locator('text=Pick a market scenario')).toBeVisible();
    await expect(page.locator('button:has-text("Back to desk")')).toBeVisible();
    await page.click('button:has-text("Back to desk")');
    await expect(page.locator('nav .tab', { hasText: 'Dashboard' })).toBeVisible();
  });
});
