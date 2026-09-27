import { expect, test } from '@playwright/test';

test('launches into the empty library', async ({ page }, testInfo) => {
  const errors = [];
  const consoleMessages = [];
  page.on('pageerror', error => errors.push(error.stack ?? error.message));
  page.on('console', message => {
    if (['error', 'warning'].includes(message.type())) {
      consoleMessages.push(`${message.type()}: ${message.text()}`);
    }
  });
  page.on('requestfailed', request => {
    errors.push(`${request.method()} ${request.url()}: ${request.failure()?.errorText}`);
  });
  page.on('response', response => {
    if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`);
  });

  try {
    await page.goto('/');
    await expect(page).toHaveTitle('Storymile');
    // Compose exposes text through its accessibility DOM; pixels are checked separately.
    await expect(page.getByText('Library', { exact: true })).toBeAttached();
    await expect(page.getByText('Your library is empty.', { exact: true })).toBeAttached();
    await expect(page).toHaveScreenshot('empty-library.png');
    expect(errors, 'Browser errors during startup').toEqual([]);
  } finally {
    await testInfo.attach('browser-errors', {
      body: errors.join('\n') || 'No browser errors.',
      contentType: 'text/plain',
    });
    await testInfo.attach('browser-console', {
      body: consoleMessages.join('\n') || 'No console warnings or errors.',
      contentType: 'text/plain',
    });
  }
});
