import { test, expect } from '@playwright/test';
import { signUp, joinFirstScenario, goToTab, pickWatchlistRowInGroup, placeOrder } from './helpers';

test.describe('Guided curricula', () => {
  test('an instructor publishes a curriculum and a step unlocks once the student completes its mission', async ({ browser }) => {
    const instructorCtx = await browser.newContext();
    const instructorPage = await instructorCtx.newPage();
    await signUp(instructorPage, { role: 'INSTRUCTOR', prefix: 'syllabusprof', displayName: 'Syllabus Prof' });
    await joinFirstScenario(instructorPage);

    await instructorPage.locator('nav .tab', { hasText: 'Classroom' }).click();
    await expect(instructorPage.locator('.panel-title', { hasText: 'Create a cohort' })).toBeVisible({ timeout: 10_000 });

    const cohortName = `Curriculum Cohort ${Date.now()}`;
    await instructorPage.fill('#cohortName', cohortName);
    await instructorPage.selectOption('#cohortScenario', { index: 1 });
    await instructorPage.click('button:has-text("Create cohort")');

    const cohortRow = instructorPage.locator('div.cursor-pointer', { hasText: cohortName });
    await expect(cohortRow).toBeVisible({ timeout: 10_000 });
    await cohortRow.click();
    const codeText = await instructorPage.locator('span', { hasText: /code [A-Z0-9]+/ }).innerText();
    const joinCode = codeText.replace(/.*code\s+/i, '').trim();

    await expect(instructorPage.locator('.panel-title', { hasText: 'Curricula' })).toBeVisible({ timeout: 10_000 });
    await instructorPage.fill('input[placeholder="e.g. Options 101"]', 'Options 101');
    await instructorPage.click('button:has-text("First Steps")');
    await instructorPage.click('button:has-text("Covered Call Writer")');
    await instructorPage.click('button:has-text("Publish curriculum")');

    const curriculumBlock = instructorPage.locator('div', { hasText: 'Options 101' })
      .filter({ hasText: 'Covered Call Writer' }).first();
    await expect(curriculumBlock).toBeVisible({ timeout: 10_000 });
    await expect(curriculumBlock.locator('li', { hasText: 'First Steps' })).toContainText('🎯');
    await expect(curriculumBlock.locator('li', { hasText: 'Covered Call Writer' })).toContainText('🔒');

    // Student joins and switches into the cohort's account.
    const studentCtx = await browser.newContext();
    const studentPage = await studentCtx.newPage();
    await signUp(studentPage, { prefix: 'syllabusstu', displayName: 'Syllabus Student' });
    await joinFirstScenario(studentPage);
    await studentPage.locator('nav .tab', { hasText: 'Classroom' }).click();
    await studentPage.fill('#joinCode', joinCode);
    await studentPage.click('button:has-text("Join")');
    await expect(studentPage.locator('div.cursor-pointer', { hasText: cohortName })).toBeVisible({ timeout: 10_000 });

    const classAccountValue = await studentPage
      .locator('select[aria-label="Active trading account"] option', { hasText: '(class)' })
      .getAttribute('value');
    await studentPage.selectOption('select[aria-label="Active trading account"]', classAccountValue!);

    // A single filled buy satisfies both First Steps steps (a fill + an open position).
    await goToTab(studentPage, 'Market');
    await pickWatchlistRowInGroup(studentPage, 'Equities');
    await placeOrder(studentPage, { side: 'BUY', qty: 1 });

    // Curriculum progress is computed against the *current viewer's own*
    // accounts -- the instructor's own untouched progress never changes, so
    // check the student's own Classroom view, not the instructor's.
    await goToTab(studentPage, 'Classroom');
    const studentCurriculumBlock = studentPage.locator('div', { hasText: 'Options 101' })
      .filter({ hasText: 'Covered Call Writer' }).first();
    await expect(studentCurriculumBlock).toBeVisible({ timeout: 10_000 });
    await expect(studentCurriculumBlock.locator('li', { hasText: 'First Steps' }))
      .toContainText('✅', { timeout: 10_000 });
    await expect(studentCurriculumBlock.locator('li', { hasText: 'Covered Call Writer' }))
      .toContainText('🎯', { timeout: 10_000 });

    await instructorCtx.close();
    await studentCtx.close();
  });
});
