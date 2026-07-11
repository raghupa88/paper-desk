import { test, expect } from '@playwright/test';
import { signUpAndJoin, goToTab, pickWatchlistRowInGroup } from './helpers';

test.describe('esp DevTools (standalone window)', () => {
  test('the launcher opens a separate window that streams live app events', async ({ page, context }) => {
    await signUpAndJoin(page);

    const [devtoolsPage] = await Promise.all([
      context.waitForEvent('page'),
      page.click('button:has-text("esp")'),
    ]);
    await devtoolsPage.waitForLoadState();
    await expect(devtoolsPage).toHaveTitle(/DevTools/);

    // Trigger a real app event (selecting an instrument) and confirm it
    // shows up in the separate window's event stream.
    await goToTab(page, 'Market');
    await pickWatchlistRowInGroup(page, 'Equities');

    await expect(devtoolsPage.locator('text=instrumentChosen')).toBeVisible({ timeout: 10_000 });

    await devtoolsPage.close();
  });
});
