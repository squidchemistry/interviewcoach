import { expect, test } from '@playwright/test';

/**
 * Full candidate journey against a real backend running AI_PROVIDER=mock:
 * register → onboarding → start a behavioural interview → answer every question → end → report.
 */

const ANSWER =
  'Situation: our release pipeline kept failing on Fridays. Task: I owned making deploys reliable. ' +
  'Action: I added canary deploys, automated rollbacks and a checklist, and paired with QA on flaky tests. ' +
  'Result: failed deploys dropped from five a month to one, and the team started shipping on Fridays again.';

test('a candidate completes a behavioural mock interview and sees the report', async ({ page }) => {
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;

  await page.goto('/register');
  await page.getByLabel('Name').fill('Asha E2E');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill('correct-horse-42');
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page).toHaveURL(/\/onboarding/);
  await page.getByRole('button', { name: 'Save and continue' }).click();
  await expect(page).not.toHaveURL(/\/onboarding/);

  await page.goto('/interviews/new');
  await expect(page.getByRole('heading', { name: 'Start a mock interview' })).toBeVisible();
  await page.getByText('Behavioural (STAR)').click();
  await page.getByLabel(/^Questions/).selectOption('3');
  await page.getByRole('button', { name: /Start interview/ }).click();

  await expect(page).toHaveURL(/\/interviews\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { name: 'Interview room' })).toBeVisible();
  await expect(page.getByRole('heading', { level: 2 }).first()).not.toHaveText(/Waiting for the first question/, { timeout: 60_000 });

  const report = /\/interviews\/[0-9a-f-]{36}\/report$/;
  const evaluating = page.getByRole('heading', { name: 'Evaluating…' });

  const send = page.getByRole('button', { name: 'Send answer' });
  const finished = async () => report.test(page.url()) || (await evaluating.isVisible());

  // Answer questions and follow-ups until the interviewer wraps up (bounded so a stuck session fails the test).
  for (let turn = 0; turn < 15; turn++) {
    // Wait until the interviewer has finished its turn (Send enabled once there is text) or the interview is over.
    const answer = page.getByLabel('Your answer');
    await expect
      .poll(
        async () => {
          if (await finished()) return 'done';
          if ((await answer.isVisible()) && (await answer.inputValue({ timeout: 1000 })) === '') await answer.fill(ANSWER);
          return (await send.isEnabled({ timeout: 1000 }).catch(() => false)) ? 'ready' : 'waiting';
        },
        { timeout: 90_000, intervals: [500] },
      )
      .not.toBe('waiting');
    if (await finished()) break;
    await send.click();
    await expect(answer).toHaveValue('');
  }

  // If the interviewer still expects more, end it explicitly.
  if (!report.test(page.url()) && !(await evaluating.isVisible())) {
    await page.getByRole('button', { name: 'End interview' }).click();
    await page.getByRole('button', { name: 'End and get feedback' }).click();
  }

  await expect(page).toHaveURL(report, { timeout: 150_000 });
  await expect(page.getByRole('heading', { name: 'Interview report' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Overall score' })).toBeVisible({ timeout: 150_000 });
  await expect(page.getByRole('heading', { name: 'Your next steps' })).toBeVisible();
});
