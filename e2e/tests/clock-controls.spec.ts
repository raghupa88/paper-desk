import { test, expect } from '@playwright/test';
import { signUpAndJoin } from './helpers';

test.describe('Sim clock controls', () => {
  test('pause and resume toggle the clock state and its label', async ({ page }) => {
    await signUpAndJoin(page);
    const clockBtn = page.locator('button', { hasText: /pause|resume/ });
    await expect(clockBtn).toBeVisible({ timeout: 10_000 });

    const initial = await clockBtn.innerText();
    await clockBtn.click();
    await expect(clockBtn).not.toHaveText(initial, { timeout: 8_000 });
  });

  test('+1 day advances the displayed sim time', async ({ page }) => {
    await signUpAndJoin(page);
    const header = page.locator('header');
    const before = await header.innerText();

    await page.click('button:has-text("+1 day")');
    await page.waitForTimeout(1500);

    const after = await header.innerText();
    expect(after).not.toBe(before);
  });

  test('changing the speed selector updates the acceleration shown in the header', async ({ page }) => {
    await signUpAndJoin(page);
    const speedSelect = page.locator('select[aria-label="Sim clock speed"]');
    await expect(speedSelect).toBeVisible({ timeout: 10_000 });

    await speedSelect.selectOption('60');
    await expect(page.locator('header')).toContainText('60×', { timeout: 8_000 });

    await speedSelect.selectOption('1800');
    await expect(page.locator('header')).toContainText('1800×', { timeout: 8_000 });
  });
});
