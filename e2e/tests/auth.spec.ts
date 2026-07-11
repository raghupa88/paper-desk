import { test, expect } from '@playwright/test';
import { uniqueEmail, signUp, logIn, joinFirstScenario } from './helpers';

test.describe('Authentication', () => {
  test('sign up, land on scenario picker, and reach the desk after joining', async ({ page }) => {
    await signUp(page);
    await joinFirstScenario(page);
    await expect(page.locator('nav .tab', { hasText: 'Dashboard' })).toBeVisible();
  });

  test('logging in with wrong credentials shows an error, not a crash', async ({ page }) => {
    await page.goto('/');
    await page.fill('#email', uniqueEmail('nouser'));
    await page.fill('#password', 'wrong-password');
    await page.locator('form button.w-full').click();
    await expect(page.locator('[role="alert"]')).toBeVisible({ timeout: 8_000 });
    await expect(page.locator('text=Pick a market scenario')).not.toBeVisible();
  });

  test('log out returns to the login screen, and logging back in resumes the joined scenario', async ({ page }) => {
    const user = await signUp(page);
    await joinFirstScenario(page);
    await page.click('button:has-text("Log out")');
    await expect(page.locator('text=Paper Desk')).toBeVisible();
    await expect(page.locator('#email')).toBeVisible();

    await logIn(page, user.email);
    // Already has an account in the scenario, so it should resume straight to the desk
    // or offer a "Resume" button on the picker rather than "Join with 100,000" again.
    const resumeOrDesk = page.locator('button:has-text("Resume"), nav .tab', ).first();
    await expect(resumeOrDesk).toBeVisible({ timeout: 10_000 });
  });

  test('a page reload preserves the session (JWT persisted, no re-login required)', async ({ page }) => {
    await signUp(page);
    await joinFirstScenario(page);
    await expect(page.locator('nav .tab', { hasText: 'Dashboard' })).toBeVisible();

    await page.reload();
    await expect(page.locator('nav .tab', { hasText: 'Dashboard' })).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('text=Pick a market scenario')).not.toBeVisible();
  });
});
