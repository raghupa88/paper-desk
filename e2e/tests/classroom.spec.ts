import { test, expect } from '@playwright/test';
import { signUpAndJoin, uniqueEmail, signUp, joinFirstScenario } from './helpers';

test.describe('Classroom: cohorts and leaderboard', () => {
  test('an instructor creates a cohort, a student joins by code, and the student appears on the leaderboard', async ({ browser }) => {
    const instructorCtx = await browser.newContext();
    const instructorPage = await instructorCtx.newPage();
    await signUpAndJoin(instructorPage, { role: 'INSTRUCTOR', prefix: 'instructor' });

    await instructorPage.locator('nav .tab', { hasText: 'Classroom' }).click();
    await expect(instructorPage.locator('.panel-title', { hasText: 'Create a cohort' })).toBeVisible({ timeout: 10_000 });

    const cohortName = `E2E Cohort ${Date.now()}`;
    await instructorPage.fill('#cohortName', cohortName);
    await instructorPage.selectOption('#cohortScenario', { index: 1 });
    await instructorPage.click('button:has-text("Create cohort")');

    // The clickable cohort row in "My cohorts" is the div with cursor-pointer
    // (a plain `div` locator's .first() would instead match an ancestor
    // panel wrapper that also "contains" the text but has no click handler).
    const cohortRow = instructorPage.locator('div.cursor-pointer', { hasText: cohortName });
    await expect(cohortRow).toBeVisible({ timeout: 10_000 });
    await cohortRow.click();

    const codeText = await instructorPage.locator('span', { hasText: /code [A-Z0-9]+/ }).innerText();
    const joinCode = codeText.replace(/.*code\s+/i, '').trim();
    expect(joinCode.length).toBeGreaterThan(0);

    // The instructor themselves isn't a cohort participant, so the
    // leaderboard is correctly empty at this point — it only populates once
    // a student actually joins.
    await expect(instructorPage.locator('table.tbl tbody')).toContainText('Select a cohort', { timeout: 10_000 });

    const studentCtx = await browser.newContext();
    const studentPage = await studentCtx.newPage();
    await signUp(studentPage, { prefix: 'student', displayName: 'Student Tester' });
    // A student who hasn't picked a scenario yet still lands on the picker;
    // the classroom join flow itself creates the account, so cancel out to
    // the login screen's scenario picker isn't needed here directly — but
    // the app still requires an active scenario account to view most tabs,
    // so join any scenario first, then join the cohort separately.
    await joinFirstScenario(studentPage);
    await studentPage.locator('nav .tab', { hasText: 'Classroom' }).click();
    await studentPage.fill('#joinCode', joinCode);
    await studentPage.click('button:has-text("Join")');

    const studentCohortRow = studentPage.locator('div.cursor-pointer', { hasText: cohortName });
    await expect(studentCohortRow).toBeVisible({ timeout: 10_000 });
    await studentCohortRow.click();

    await expect(studentPage.locator('table.tbl tbody')).toContainText('Student Tester', { timeout: 10_000 });

    await instructorCtx.close();
    await studentCtx.close();
  });
});
