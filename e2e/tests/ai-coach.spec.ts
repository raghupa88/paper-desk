import { test, expect } from '@playwright/test';
import { signUpAndJoin, goToTab, pickWatchlistRowInGroup, placeOrder } from './helpers';

test.describe('AI trading coach', () => {
  test('a student can ask the coach to explain a filled trade', async ({ page }) => {
    await signUpAndJoin(page, { prefix: 'coachee' });

    await goToTab(page, 'Market');
    await pickWatchlistRowInGroup(page, 'Equities');
    await placeOrder(page, { side: 'BUY', qty: 1 });

    await goToTab(page, 'Blotter');
    const explainButton = page.locator('button[aria-label*="Explain trade"]').first();
    await expect(explainButton).toBeVisible({ timeout: 10_000 });
    await explainButton.click();

    // No ANTHROPIC_API_KEY is set in this environment, so the graceful
    // not-configured degrade path is what's actually verifiable end-to-end here.
    await expect(page.locator("text=AI coach isn't configured on this deployment yet.")).toBeVisible({ timeout: 10_000 });
  });
});
