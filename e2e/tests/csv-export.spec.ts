import { test, expect } from '@playwright/test';
import { signUpAndJoin } from './helpers';

test.describe('Instructor CSV export', () => {
  test('exporting a cohort leaderboard downloads a CSV file', async ({ page }) => {
    await signUpAndJoin(page, { role: 'INSTRUCTOR', prefix: 'csvinstr' });
    await page.locator('nav .tab', { hasText: 'Classroom' }).click();

    await expect(page.locator('.panel-title', { hasText: 'Create a cohort' })).toBeVisible({ timeout: 10_000 });
    const cohortName = `CSV Cohort ${Date.now()}`;
    await page.fill('#cohortName', cohortName);
    await page.selectOption('#cohortScenario', { index: 1 });
    await page.click('button:has-text("Create cohort")');

    const cohortRow = page.locator('div', { hasText: cohortName }).first();
    await expect(cohortRow).toBeVisible({ timeout: 10_000 });
    await cohortRow.click();

    const exportBtn = page.locator('button:has-text("Export CSV")');
    await expect(exportBtn).toBeVisible({ timeout: 10_000 });

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      exportBtn.click(),
    ]);
    expect(download.suggestedFilename()).toMatch(/\.csv$/i);
  });
});
