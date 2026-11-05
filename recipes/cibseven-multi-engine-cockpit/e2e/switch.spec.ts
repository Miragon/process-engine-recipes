import { expect, Page, test } from '@playwright/test';

const engines = [
  { name: 'Membership', process: 'newsletter-subscription' },
  { name: 'Leasing', process: 'bike-leasing' },
  { name: 'Logistics', process: 'welcome-package' },
];

const screenshotPath = '../images/engine-switch.png';

async function selectEngine(page: Page, engineName: string) {
  await openEngineMenu(page);
  await page.getByRole('menuitem', { name: engineName }).click();
}

async function openEngineMenu(page: Page) {
  await page.getByRole('button', { name: 'Engine selection' }).click();
  await expect(page.getByRole('menuitem').first()).toBeVisible();
}

async function login(page: Page) {
  await page.getByRole('group', { name: 'Username' }).getByRole('textbox').fill('demo');
  await page.locator('input[type="password"]').fill('demo');
  await page.getByRole('button', { name: 'Login' }).click();
  await expect(page).toHaveURL(/#\/seven\/auth\/start/);
}

async function openProcessList(page: Page) {
  await page.goto('#/seven/auth/processes/list');
  await expect(page.getByRole('table')).toBeVisible();
}

function processLink(page: Page, processKey: string) {
  return page.getByRole('link', { name: processKey, exact: true });
}

test('the cockpit offers the engine of every service', async ({ page }) => {
  await page.goto('');
  await login(page);
  await openProcessList(page);

  await openEngineMenu(page);
  await expect(page.getByRole('menuitem')).toHaveText(engines.map((engine) => engine.name));

  if (process.env.UPDATE_SCREENSHOT) {
    await page.screenshot({ path: screenshotPath });
  }
});

for (const engine of engines) {
  test(`${engine.name} shows only its own process`, async ({ page }) => {
    await page.goto('');
    await selectEngine(page, engine.name);
    await login(page);
    await openProcessList(page);

    await expect(processLink(page, engine.process)).toBeVisible();
    for (const otherEngine of engines.filter((candidate) => candidate !== engine)) {
      await expect(processLink(page, otherEngine.process)).toHaveCount(0);
    }
  });
}
