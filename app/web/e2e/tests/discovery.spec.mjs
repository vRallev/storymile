import { expect, test } from '@playwright/test';

test('loads real-mode charts after retry and searches topics in the browser', async ({ page }) => {
  const chartUrl = 'https://itunes.apple.com/us/rss/toppodcasts/limit=200/json';
  const unexpectedRequests = [];
  const searches = [];
  const lookups = [];
  const feeds = [];
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
    } else if (url.origin === 'https://itunes.apple.com' && url.pathname === '/lookup') {
      lookups.push(url.searchParams.get('id'));
      await route.fulfill({
        contentType: 'application/json',
        headers: { 'Access-Control-Allow-Origin': '*' },
        body: JSON.stringify({ results: [{
          collectionId: Number(url.searchParams.get('id')),
          collectionName: 'Chart Podcast',
          feedUrl: 'https://feeds.example.invalid/chart.xml',
        }] }),
      });
    } else if (url.hostname === 'feeds.example.invalid') {
      feeds.push(url.pathname);
      await route.fulfill({
        contentType: 'application/rss+xml',
        headers: { 'Access-Control-Allow-Origin': '*' },
        body: `<rss version="2.0"><channel><title>Feed Podcast</title><item>
          <guid>episode-1</guid><title>A real feed episode</title>
          <enclosure url="https://audio.example.invalid/episode.mp3" type="audio/mpeg" />
        </item></channel></rss>`,
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
          feedUrl: 'https://feeds.example.invalid/science.xml',
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

  const discoveryList = page.locator('[id="discovery-list"]');
  const chart = discoveryList.locator('[id="discovery-podcast-123"]');
  await expect(chart).toContainText('Chart Podcast');
  await expect(chart).toContainText('Chart Publisher');
  await expect(retry).not.toBeAttached();
  expect(chartRequests).toBe(2);
  await expect.poll(() => feeds).toEqual(['/chart.xml']);
  await expect(page.locator('[id="episode-0"]')).toContainText('A real feed episode');
  await chart.click({ force: true });
  await expect(page.locator('[id="show-title"]')).toContainText('Chart Podcast');
  const back = page.locator('[id="show-back"]');
  // Semantics bounds can lag the canvas until the outgoing screen finishes its transition.
  await expect(page.locator('[id="discovery-screen"]')).not.toBeAttached();
  await back.click({ force: true });
  await expect(page.locator('[id="show-screen"]')).not.toBeAttached();
  await expect(chart).toBeVisible();
  expect(lookups).toEqual(['123']);
  expect(feeds).toEqual(['/chart.xml']);
  const science = page.locator('[id="discovery-topic-SCIENCE"]');
  await expect(science).toBeVisible();
  await science.click({ force: true });
  await expect(discoveryList.locator('[id="discovery-podcast-456"]')).toContainText('Science Podcast');
  expect(searches).toEqual([{
    term: 'Science', media: 'podcast', entity: 'podcast', country: 'US', limit: '200',
  }]);
  await expect.poll(() => feeds).toEqual(['/chart.xml', '/science.xml']);
  expect(unexpectedRequests).toEqual([]);
});
