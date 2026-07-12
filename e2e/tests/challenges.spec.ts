import { test, expect } from '@playwright/test';
import { signUp, joinFirstScenario, goToTab, pickWatchlistRowInGroup, placeOrder } from './helpers';

test.describe('Timed challenges', () => {
  test('an instructor starts a challenge, the student is auto-enrolled at par, and trading moves the sprint leaderboard', async ({ browser }) => {
    const instructorCtx = await browser.newContext();
    const instructorPage = await instructorCtx.newPage();
    await signUp(instructorPage, { role: 'INSTRUCTOR', prefix: 'sprintprof', displayName: 'Sprint Prof' });
    await joinFirstScenario(instructorPage);

    await instructorPage.locator('nav .tab', { hasText: 'Classroom' }).click();
    await expect(instructorPage.locator('.panel-title', { hasText: 'Create a cohort' })).toBeVisible({ timeout: 10_000 });

    const cohortName = `Challenge Cohort ${Date.now()}`;
    await instructorPage.fill('#cohortName', cohortName);
    await instructorPage.selectOption('#cohortScenario', { index: 1 });
    await instructorPage.click('button:has-text("Create cohort")');

    const cohortRow = instructorPage.locator('div.cursor-pointer', { hasText: cohortName });
    await expect(cohortRow).toBeVisible({ timeout: 10_000 });
    await cohortRow.click();
    const codeText = await instructorPage.locator('span', { hasText: /code [A-Z0-9]+/ }).innerText();
    const joinCode = codeText.replace(/.*code\s+/i, '').trim();

    await expect(instructorPage.locator('.panel-title', { hasText: 'Challenges' })).toBeVisible({ timeout: 10_000 });
    await expect(instructorPage.locator('text=No challenges yet')).toBeVisible({ timeout: 10_000 });

    // Student joins and switches into the cohort's account.
    const studentCtx = await browser.newContext();
    const studentPage = await studentCtx.newPage();
    await signUp(studentPage, { prefix: 'sprinter', displayName: 'Sprinter Student' });
    await joinFirstScenario(studentPage);
    await studentPage.locator('nav .tab', { hasText: 'Classroom' }).click();
    await studentPage.fill('#joinCode', joinCode);
    await studentPage.click('button:has-text("Join")');
    await expect(studentPage.locator('div.cursor-pointer', { hasText: cohortName })).toBeVisible({ timeout: 10_000 });

    const classAccountValue = await studentPage
      .locator('select[aria-label="Active trading account"] option', { hasText: '(class)' })
      .getAttribute('value');
    await studentPage.selectOption('select[aria-label="Active trading account"]', classAccountValue!);

    // Instructor starts a 5-day sprint.
    await instructorPage.fill('input[placeholder="e.g. Week 1 Sprint"]', 'Week 1 Sprint');
    await instructorPage.fill('input[type="number"][min="1"]', '5');
    await instructorPage.click('button:has-text("Start challenge")');

    const challengeBlock = instructorPage.locator('div', { hasText: 'Week 1 Sprint' }).filter({ hasText: 'Active' }).first();
    await expect(challengeBlock).toBeVisible({ timeout: 10_000 });
    await expect(challengeBlock).toContainText('Sprinter Student');
    await expect(challengeBlock).toContainText('5 sim days');

    // Student trades; the sprint's own leaderboard (not the lifetime one) should move.
    await goToTab(studentPage, 'Market');
    await pickWatchlistRowInGroup(studentPage, 'Equities');
    await placeOrder(studentPage, { side: 'BUY', qty: 5 });

    await instructorPage.waitForTimeout(5500); // the challenges panel polls every 5s
    const returnCell = challengeBlock.locator('table.tbl tbody tr', { hasText: 'Sprinter Student' }).locator('td').last();
    await expect(returnCell).not.toHaveText('+0.00%', { timeout: 10_000 });

    await instructorCtx.close();
    await studentCtx.close();
  });
});
