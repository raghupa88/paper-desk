import { test, expect } from '@playwright/test';
import { signUpAndJoin, goToTab } from './helpers';

const VIEWPORTS = [
  { name: 'phone', width: 375, height: 812 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'desktop', width: 1440, height: 900 },
];

const TABS = ['Dashboard', 'Market', 'Options Chain', 'FX Sales', 'FX Trader', 'Portfolio', 'Blotter', 'Progress', 'Classroom'];

for (const vp of VIEWPORTS) {
  test.describe(`Mobile/responsive @ ${vp.name} (${vp.width}px)`, () => {
    test.use({ viewport: { width: vp.width, height: vp.height } });

    test(`no page-level horizontal overflow on any tab`, async ({ page }) => {
      await signUpAndJoin(page);

      for (const tab of TABS) {
        await goToTab(page, tab);
        await page.waitForTimeout(300);
        const overflow = await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth);
        expect(overflow, `${tab} tab overflows horizontally at ${vp.width}px`).toBeLessThanOrEqual(2);
      }
    });
  });
}
