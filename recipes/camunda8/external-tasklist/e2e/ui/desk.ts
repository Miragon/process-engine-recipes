import { expect, type Page } from '@playwright/test';

export const USERS = { alice: 'Alice Adams', bob: 'Bob Baker', carol: 'Carol Clark' } as const;

/** Where `UPDATE_SCREENSHOT=1 ./e2e.sh` puts the picture of the README. */
export const SCREENSHOT = '../images/tasklist.png';

/** Opens the tasklist and picks a demo user in the top bar, like a person would; the recipe has no sign-in. */
export async function openAs(page: Page, user: keyof typeof USERS, path = '/'): Promise<void> {
  await page.goto(path);
  const switcher = page.getByRole('button', { name: /^Switch demo user/ });
  await switcher.click();
  await page.getByRole('menuitemradio', { name: new RegExp(USERS[user]) }).click();
  await expect(switcher).toHaveAccessibleName(`Switch demo user, now ${USERS[user]}`);
}

/** The detail pane with the open task; the list next to it is the region "Open tasks". */
export function taskDetail(page: Page) {
  return page.getByRole('region', { name: 'Task', exact: true });
}

export function startFormDetail(page: Page) {
  return page.getByRole('region', { name: 'Start form', exact: true });
}

export function taskList(page: Page) {
  return page.getByRole('listbox', { name: 'Tasks' });
}

export function filters(page: Page) {
  return page.getByRole('navigation', { name: 'Filters' });
}

/** A row of the list, found by the business ID in its subject. */
export function row(page: Page, businessId: string) {
  return taskList(page).getByRole('option', { name: new RegExp(businessId) });
}
