import { APIRequestContext, expect, FrameLocator, Page, test } from '@playwright/test';

const engineRest = 'http://localhost:8080/engine-rest';
const webClient = 'http://localhost:8080/webapp/';
const frontend = 'http://localhost:4173/';

type Variables = Record<string, { value: unknown }>;

async function startRepair(request: APIRequestContext): Promise<string> {
  const response = await request.post(`${engineRest}/process-definition/key/bikeRepair/start`, { data: {} });
  expect(response.ok()).toBeTruthy();
  return (await response.json()).id;
}

async function openTaskId(request: APIRequestContext, repairId: string, taskDefinitionKey: string): Promise<string> {
  let taskId = '';
  await expect(async () => {
    const response = await request.get(`${engineRest}/task`, {
      params: { processInstanceId: repairId, taskDefinitionKey },
    });
    const tasks = await response.json();
    expect(tasks).toHaveLength(1);
    taskId = tasks[0].id;
  }).toPass();
  return taskId;
}

async function completeInEngine(request: APIRequestContext, taskId: string, variables: Variables) {
  const response = await request.post(`${engineRest}/task/${taskId}/complete`, { data: { variables } });
  expect(response.ok()).toBeTruthy();
}

async function allTasksFilterId(request: APIRequestContext): Promise<string> {
  const filters = await (await request.get(`${engineRest}/filter`)).json();
  return filters[0].id;
}

async function login(page: Page) {
  await page.goto(webClient);
  await page.getByRole('group', { name: 'Username' }).getByRole('textbox').fill('demo');
  await page.locator('input[type="password"]').fill('demo');
  await page.getByRole('button', { name: 'Login' }).click();
  await expect(page).toHaveURL(/#\/seven\/auth\/start/);
}

async function openInTasklist(page: Page, filterId: string, taskId: string, taskName: string) {
  await page.goto(`${webClient}#/seven/auth/tasks/${filterId}/${taskId}`);
  await page.reload();
  await expect(page).toHaveTitle(new RegExp(taskName));
  const assignToMe = page.getByRole('button', { name: /Assign to me/ });
  await assignToMe.last().click();
  await expect(assignToMe).toHaveCount(0);
}

function taskForm(page: Page, taskName: string): FrameLocator {
  return page.frameLocator(`iframe[title="${taskName}"]`);
}

async function screenshot(page: Page, name: string) {
  if (process.env.UPDATE_SCREENSHOT) {
    await page.screenshot({ path: `../images/${name}.png` });
  }
}

async function openFrontendTask(page: Page, taskName: string) {
  await page.goto(frontend);
  const card = page.getByRole('region', { name: taskName });
  await expect(card).toBeVisible();
  return card;
}

test('every form type renders where it should and the repair completes', async ({ page, request }) => {
  const repairId = await startRepair(request);
  const filterId = await allTasksFilterId(request);
  await login(page);

  const checkInBike = await openTaskId(request, repairId, 'checkInBike');
  await openInTasklist(page, filterId, checkInBike, 'Check in bike');
  const generatedForm = taskForm(page, 'Check in bike');
  await generatedForm.locator('input[name="frameNumber"]').fill('WTU-2026-0815');
  await generatedForm.locator('input[name="issueDescription"]').fill('Rear brake rubs');
  await screenshot(page, '1-check-in-bike-generated');
  await completeInEngine(request, checkInBike, {
    frameNumber: { value: 'WTU-2026-0815' },
    issueDescription: { value: 'Rear brake rubs' },
  });

  const diagnoseBike = await openTaskId(request, repairId, 'diagnoseBike');
  await openInTasklist(page, filterId, diagnoseBike, 'Diagnose bike');
  const camundaForm = taskForm(page, 'Diagnose bike');
  await camundaForm.getByLabel('Findings').fill('Brake pads worn down to the metal');
  await camundaForm.getByLabel('Parts needed').fill('Rear brake pads');
  await camundaForm.getByLabel('Estimated cost (EUR)').fill('35');
  await screenshot(page, '2-diagnose-bike-camunda-form');
  await completeInEngine(request, diagnoseBike, { findings: { value: 'Brake pads worn down to the metal' } });

  const repairBike = await openTaskId(request, repairId, 'repairBike');
  await openInTasklist(page, filterId, repairBike, 'Repair bike');
  await expect(page.getByText('Empty task')).toBeVisible();
  await screenshot(page, 'empty-task-repair-bike');

  const repairCard = await openFrontendTask(page, 'Repair bike');
  await repairCard.getByLabel('Work done').fill('Replaced the rear brake pads and trued the wheel');
  await repairCard.getByLabel('Parts replaced').fill('Rear brake pads');
  await screenshot(page, '3-repair-bike-frontend');
  await repairCard.getByRole('button', { name: 'Repair done' }).click();
  await expect(repairCard).toBeHidden();

  const checkRepairQuality = await openTaskId(request, repairId, 'checkRepairQuality');
  await openInTasklist(page, filterId, checkRepairQuality, 'Check repair quality');
  const embeddedForm = taskForm(page, 'Check repair quality');
  await expect(embeddedForm.locator('#frameNumber')).toHaveValue('WTU-2026-0815');
  await embeddedForm.getByLabel('Brakes work').check();
  await embeddedForm.getByLabel('Gears shift cleanly').check();
  await embeddedForm.getByLabel('Test ride done').check();
  await screenshot(page, '4-check-repair-quality-embedded');
  await completeInEngine(request, checkRepairQuality, {
    brakesOk: { value: true },
    gearsOk: { value: true },
    testRideOk: { value: true },
  });

  const choosePickupSlot = await openTaskId(request, repairId, 'choosePickupSlot');
  await openInTasklist(page, filterId, choosePickupSlot, 'Choose pickup slot');
  await expect(page.getByText('Empty task')).toBeVisible();
  await screenshot(page, 'empty-task-choose-pickup-slot');

  const pickupCard = await openFrontendTask(page, 'Choose pickup slot');
  await pickupCard.getByLabel('Saturday, 09:00 – 12:00').check();
  await screenshot(page, '5-choose-pickup-slot-frontend');
  await pickupCard.getByRole('button', { name: 'Book pickup slot' }).click();
  await expect(pickupCard).toBeHidden();

  await expect(async () => {
    const repair = await (await request.get(`${engineRest}/history/process-instance/${repairId}`)).json();
    expect(repair.state).toBe('COMPLETED');
  }).toPass();
});
