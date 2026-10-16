import { expect, test } from '@playwright/test';

test('loads real-mode charts after retry and searches topics in the browser', async ({ page }) => {
  const chartUrl = 'https://itunes.apple.com/us/rss/toppodcasts/limit=200/json';
  const unexpectedRequests = [];
  const searches = [];
  let chartRequests = 0;
  // Exercise the production HTTP client without sending requests to external services.
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.origin === 'http://127.0.0.1:4173') {
      await route.continue();
    } else if (url.href === chartUrl) {
      chartRequests += 1;
      await route.fulfill({
        status: chartRequests === 1 ? 503 : 200,
        contentType: 'text/javascript; charset=UTF-8',
        headers: { 'Access-Control-Allow-Origin': '*' },
        body: JSON.stringify({ feed: { entry: [{
          id: { attributes: { 'im:id': '123' }, label: 'https://example.invalid/podcast' },
          'im:name': { label: 'Chart Podcast' },
          'im:artist': { label: 'Chart Publisher' },
        }] } }),
      });
    } else if (url.origin === 'https://itunes.apple.com' && url.pathname === '/search') {
      searches.push(Object.fromEntries(url.searchParams));
      await route.fulfill({
        contentType: 'text/javascript; charset=utf-8',
        headers: { 'Access-Control-Allow-Origin': '*' },
        body: JSON.stringify({ results: [{
          collectionId: 456,
          collectionName: 'Science Podcast',
          artistName: 'Science Publisher',
        }] }),
      });
    } else {
      unexpectedRequests.push(url.href);
      await route.abort();
    }
  });

  await page.goto('/index.html');
  const discover = page.locator('[id="home-discover"]');
  await expect(discover).toBeVisible();
  await discover.click({ force: true });
  const retry = page.locator('[id="discovery-retry"]');
  await expect(retry).toBeVisible();
  await retry.click({ force: true });

  const chart = page.locator('[id="discovery-podcast-123"]');
  await expect(chart).toContainText('Chart Podcast');
  await expect(chart).toContainText('Chart Publisher');
  await expect(retry).not.toBeAttached();
  expect(chartRequests).toBe(2);
  const science = page.locator('[id="discovery-topic-SCIENCE"]');
  await expect(science).toBeVisible();
  await science.click({ force: true });
  await expect(page.locator('[id="discovery-podcast-456"]')).toContainText('Science Podcast');
  expect(searches).toEqual([{
    term: 'Science', media: 'podcast', entity: 'podcast', country: 'US', limit: '200',
  }]);
  expect(unexpectedRequests).toEqual([]);
});
