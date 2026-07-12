import { test, expect } from '@playwright/test';
import { signUp, signUpAndJoin, joinFirstScenario, goToTab, pickWatchlistRowInGroup, placeOrder } from './helpers';

test.describe('Instructor grading & trade feedback', () => {
  test('an instructor reviews a student, grades them, comments on a trade, and the student sees both', async ({ browser }) => {
    const instructorCtx = await browser.newContext();
    const instructorPage = await instructorCtx.newPage();
    await signUpAndJoin(instructorPage, { role: 'INSTRUCTOR', prefix: 'grader' });

    await instructorPage.locator('nav .tab', { hasText: 'Classroom' }).click();
    await expect(instructorPage.locator('.panel-title', { hasText: 'Create a cohort' })).toBeVisible({ timeout: 10_000 });

    const cohortName = `Grading Cohort ${Date.now()}`;
    await instructorPage.fill('#cohortName', cohortName);
    await instructorPage.selectOption('#cohortScenario', { index: 1 });
    await instructorPage.click('button:has-text("Create cohort")');

    const cohortRow = instructorPage.locator('div.cursor-pointer', { hasText: cohortName });
    await expect(cohortRow).toBeVisible({ timeout: 10_000 });
    await cohortRow.click();
    const codeText = await instructorPage.locator('span', { hasText: /code [A-Z0-9]+/ }).innerText();
    const joinCode = codeText.replace(/.*code\s+/i, '').trim();

    // Student: join a default scenario first (required to get past the
    // account picker), then join the cohort, then switch the active trading
    // account to the cohort's dedicated session so the trade below lands
    // where the instructor's review panel will look for it.
    const studentCtx = await browser.newContext();
    const studentPage = await studentCtx.newPage();
    await signUp(studentPage, { prefix: 'gradee', displayName: 'Gradee Student' });
    await joinFirstScenario(studentPage);
    await studentPage.locator('nav .tab', { hasText: 'Classroom' }).click();
    await studentPage.fill('#joinCode', joinCode);
    await studentPage.click('button:has-text("Join")');
    await expect(studentPage.locator('div.cursor-pointer', { hasText: cohortName })).toBeVisible({ timeout: 10_000 });

    const classAccountValue = await studentPage
      .locator('select[aria-label="Active trading account"] option', { hasText: '(class)' })
      .getAttribute('value');
    await studentPage.selectOption('select[aria-label="Active trading account"]', classAccountValue!);
    await goToTab(studentPage, 'Market');
    await pickWatchlistRowInGroup(studentPage, 'Equities');
    await placeOrder(studentPage, { side: 'BUY', qty: 5 });

    // Instructor: open the review panel for that student and grade them.
    await instructorPage.locator('div.cursor-pointer', { hasText: cohortName }).click();
    await expect(instructorPage.locator('table.tbl tbody')).toContainText('Gradee Student', { timeout: 10_000 });
    await instructorPage.locator('tr', { hasText: 'Gradee Student' }).locator('button:has-text("Review")').click();

    const panel = instructorPage.locator('[role="dialog"]');
    await expect(panel).toBeVisible({ timeout: 10_000 });
    await expect(panel).toContainText('Reviewing — Gradee Student');
    await expect(panel.locator('table.tbl tbody')).toContainText('BUY', { timeout: 10_000 });

    await panel.locator('label', { hasText: 'Overall' }).locator('select').selectOption('5');
    await panel.locator('#feedback').fill('Excellent discipline on entry sizing.');
    await panel.locator('button:has-text("Save grade")').click();
    await expect(panel.locator('button:has-text("Update grade")')).toBeVisible({ timeout: 10_000 });

    // Comment on the trade.
    await panel.locator('table.tbl tbody tr', { hasText: 'BUY' }).first().click();
    await panel.locator('input[placeholder="Add a comment on this trade…"]').fill('Watch position sizing next time.');
    await panel.locator('button:has-text("Post")').click();
    await expect(panel).toContainText('Watch position sizing next time.', { timeout: 10_000 });

    // Student: sees the grade on Progress and the comment on the Blotter.
    await goToTab(studentPage, 'Progress');
    await expect(studentPage.locator('.panel-title', { hasText: 'Instructor feedback' })).toBeVisible({ timeout: 10_000 });
    await expect(studentPage.locator('.panel', { hasText: 'Instructor feedback' })).toContainText('Excellent discipline on entry sizing.');

    await goToTab(studentPage, 'Blotter');
    await studentPage.locator('table.tbl tbody tr').first().locator('button[aria-label*="Instructor notes"]').click();
    await expect(studentPage.locator('table.tbl')).toContainText('Watch position sizing next time.', { timeout: 10_000 });

    await instructorCtx.close();
    await studentCtx.close();
  });
});
