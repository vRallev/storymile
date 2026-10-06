import { expect, test } from '@playwright/test';

// Host controls need a persistent app window; production Wasm is checked separately in CI.
const docsHostTest = test.extend({
  page: async ({ page }, use) => {
    await page.route('**/storymile/web/index.html', route => route.fulfill({
      contentType: 'text/html',
      body: '<!doctype html><html><head><title>App preview fixture</title></head><body></body></html>',
    }));
    await use(page);
  },
});

async function expectPreviewSize(iframe, width, height) {
  await expect.poll(() => iframe.evaluate(element => ({
    width: element.clientWidth,
    height: element.clientHeight,
  }))).toEqual({ width, height });
}

async function galleryAppearance(root) {
  return root.evaluate(element => {
    const selectedTab = document.querySelector('.tab[aria-selected="true"]');
    const close = document.querySelector('#viewer-close');
    if (!document.body || !selectedTab || !close) return null;
    const body = getComputedStyle(document.body);
    const activeTab = getComputedStyle(selectedTab);
    const control = getComputedStyle(close);
    return {
      theme: element.dataset.theme,
      colorScheme: getComputedStyle(element).colorScheme,
      background: body.backgroundColor,
      text: body.color,
      activeBackground: activeTab.backgroundColor,
      activeText: activeTab.color,
      controlText: control.color,
      controlBorder: control.borderTopColor,
    };
  });
}

async function expectGalleryIcon(gallery, theme) {
  const icon = gallery.locator('.brand img:visible');
  await expect(icon).toHaveAttribute('src', `../images/icon-${theme}.svg`);
  await expect.poll(() => icon.evaluate(image =>
    image.complete && image.naturalWidth > 0)).toBe(true);
}

test('loads the embedded production app under the project path', async ({ page }, testInfo) => {
  const errors = [];
  const localAssetPaths = [];
  page.on('pageerror', error => errors.push(error.stack ?? error.message));
  page.on('requestfailed', request => {
    if (new URL(request.url()).origin !== 'http://127.0.0.1:4174') return;
    errors.push(`${request.method()} ${request.url()}: ${request.failure()?.errorText}`);
  });
  page.on('response', response => {
    const url = new URL(response.url());
    // Material's optional GitHub release metadata can return 404 for an unreleased app.
    if (url.origin !== 'http://127.0.0.1:4174') return;
    if (response.status() >= 400) errors.push(`${response.status()} ${url}`);
    localAssetPaths.push(url.pathname);
  });

  try {
    await page.goto('./');
    const iframe = page.getByTitle('Storymile app', { exact: true });
    await expect(iframe).toHaveCount(1);
    await expect(iframe).toHaveAttribute('src', 'web/index.html');
    const app = page.frameLocator('iframe[title="Storymile app"]');
    for (const tag of ['library', 'tabs', 'playback']) {
      await expect(app.locator(`[id="${tag}"]`)).toBeAttached();
    }
    await expect.poll(() => localAssetPaths.some(path => /\.wasm$/.test(path))).toBe(true);
    expect(localAssetPaths.every(path => path.startsWith('/storymile/')),
      'All local resources must load below the GitHub Pages project path').toBe(true);
    await expect(page.getByRole('link', { name: /^Open app/ }))
      .toHaveAttribute('href', 'web/index.html');
    expect(errors, 'Browser errors while loading the documentation and app').toEqual([]);
  } finally {
    await testInfo.attach('browser-errors', {
      body: errors.join('\n') || 'No browser errors.',
      contentType: 'text/plain',
    });
  }
});

docsHostTest('resizes one app frame with presets, keyboard, and pointer input', async ({ page }) => {
  await page.setViewportSize({ width: 1800, height: 1100 });
  await page.goto('./');
  await expect(page.locator('.md-content__button')).toHaveCount(0);
  const iframe = page.getByTitle('Storymile app', { exact: true });
  const phone = page.getByRole('button', { name: 'Phone', exact: true });
  const tablet = page.getByRole('button', { name: 'Tablet', exact: true });
  const desktop = page.getByRole('button', { name: 'Desktop', exact: true });
  const handle = page.getByRole('separator', { name: 'Resize app width' });
  await expectPreviewSize(iframe, 480, 840);
  await expect(phone).toHaveAttribute('aria-pressed', 'true');

  // A value in the app window detects iframe replacement or navigation during resizing.
  await iframe.evaluate(element => {
    element.contentWindow.__docsPreviewState = 'preserve app state';
  });
  await tablet.click();
  await expectPreviewSize(iframe, 1100, 840);
  await expect(tablet).toHaveAttribute('aria-pressed', 'true');

  await handle.focus();
  await page.keyboard.press('ArrowLeft');
  await expectPreviewSize(iframe, 1076, 840);
  await expect(handle).toHaveAttribute('aria-valuenow', '1076');
  await page.keyboard.press('Shift+ArrowLeft');
  await expectPreviewSize(iframe, 980, 840);

  await handle.scrollIntoViewIfNeeded();
  const bounds = await handle.boundingBox();
  expect(bounds).not.toBeNull();
  const startX = bounds.x + bounds.width / 2;
  const startY = bounds.y + bounds.height / 2;
  await page.mouse.move(startX, startY);
  await page.mouse.down();
  await page.mouse.move(startX - 120, startY, { steps: 6 });
  await page.mouse.up();
  await expectPreviewSize(iframe, 860, 840);

  await desktop.click();
  await expectPreviewSize(iframe, 1440, 840);
  await expect(desktop).toHaveAttribute('aria-pressed', 'true');
  await expect(tablet).toHaveAttribute('aria-pressed', 'false');
  await expect.poll(() => iframe.evaluate(element => {
    const preview = element.closest('[data-app-preview]');
    const stage = element.closest('[data-preview-stage]');
    const article = element.closest('.md-content__inner');
    const frameBounds = element.getBoundingClientRect();
    const previewBounds = preview.getBoundingClientRect();
    const stageBounds = stage.getBoundingClientRect();
    const articleBounds = article.getBoundingClientRect();
    const previewStyle = getComputedStyle(preview);
    const stageStyle = getComputedStyle(stage);
    const paddingLeft = parseFloat(stageStyle.paddingLeft);
    const paddingRight = parseFloat(stageStyle.paddingRight);
    const borders = parseFloat(previewStyle.borderLeftWidth) + parseFloat(previewStyle.borderRightWidth);
    const viewportCenter = document.documentElement.clientWidth / 2;
    return Math.max(
      Math.abs(articleBounds.left + articleBounds.width / 2 - viewportCenter),
      Math.abs(previewBounds.left + previewBounds.width / 2 - viewportCenter),
      Math.abs(previewBounds.width - frameBounds.width - paddingLeft - paddingRight - borders),
      Math.abs(stageBounds.right - frameBounds.right - paddingRight),
    );
  }), 'The Home page and preview must be centered and fit the Desktop frame plus resize clearance')
    .toBeLessThanOrEqual(1);
  await phone.click();
  await expectPreviewSize(iframe, 480, 840);
  await expect(phone).toHaveAttribute('aria-pressed', 'true');
  await expect(desktop).toHaveAttribute('aria-pressed', 'false');
  await expect(page.locator('[data-preview-size]')).toContainText('480');
  await expect(page.locator('[data-preview-size]')).toContainText('840');
  expect(await iframe.evaluate(element => element.contentWindow.__docsPreviewState))
    .toBe('preserve app state');
  await expect(iframe).toHaveCount(1);
});

docsHostTest('fits the app preview to a narrow page and restores the selected size', async ({ page }) => {
  await page.setViewportSize({ width: 1800, height: 1100 });
  await page.goto('./');
  const iframe = page.getByTitle('Storymile app', { exact: true });
  await page.getByRole('button', { name: 'Desktop', exact: true }).click();
  await expectPreviewSize(iframe, 1440, 840);
  await page.setViewportSize({ width: 375, height: 812 });

  await expect.poll(() => page.locator('[data-preview-stage]').evaluate(stage => {
    const frame = stage.querySelector('iframe');
    return frame.clientWidth <= stage.clientWidth && frame.clientWidth < 375;
  })).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(375);
  await expect(iframe).toBeVisible();
  await page.setViewportSize({ width: 1800, height: 1100 });
  await expectPreviewSize(iframe, 1440, 840);
});

docsHostTest('serves the versioned preview script without stale conditional responses', async ({ page, request }) => {
  await page.goto('./');
  const script = page.locator('script[src*="javascripts/app-preview.js"]');
  await expect(script).toHaveAttribute('src', /app-preview\.js\?v=[a-f0-9]{12}$/);
  const scriptUrl = new URL(await script.getAttribute('src'), page.url()).href;
  const initialResponse = await request.get(scriptUrl);
  expect(initialResponse.status()).toBe(200);
  expect(initialResponse.headers()['cache-control']).toContain('no-store');
  const currentScript = await initialResponse.text();
  expect(currentScript.length).toBeGreaterThan(0);

  const conditionalResponse = await request.get(scriptUrl, {
    headers: { 'If-Modified-Since': 'Fri, 01 Jan 2100 00:00:00 GMT' },
  });
  expect(conditionalResponse.status(), 'Preview reloads must return the current script').toBe(200);
  expect(conditionalResponse.headers()['cache-control']).toContain('no-store');
  expect(await conditionalResponse.text()).toBe(currentScript);
});

docsHostTest('renders the presenter diagram, source links, and both documentation themes', async ({ page }) => {
  // Observe geometry inside Material's closed root without changing its mode or return value.
  await page.addInitScript(() => {
    const roots = new WeakMap();
    const attachShadow = Element.prototype.attachShadow;
    Element.prototype.attachShadow = function (...args) {
      const root = Reflect.apply(attachShadow, this, args);
      roots.set(this, root);
      return root;
    };
    window.__docsShadowRoots = roots;
  });
  await page.goto('presenter-hierarchy/');
  await expect(page.locator('.md-content__button')).toHaveCount(0);
  await expect(page.getByRole('heading', { name: /^Presenter hierarchy/, level: 1 })).toBeVisible();
  for (const name of ['Home', 'Presenter hierarchy', 'Design research']) {
    await expect(page.locator('.md-tabs').getByRole('link', { name, exact: true })).toBeVisible();
  }
  await expect(page.locator('.md-tabs').getByRole('link', { name: 'App', exact: true })).toHaveCount(0);
  await expect(page.locator('.md-tabs').getByRole('link')).toHaveCount(3);
  const leftToc = page.locator('.md-sidebar--primary .md-nav--secondary');
  await expect(leftToc).toBeVisible();
  await expect(page.locator('.md-sidebar--secondary')).toHaveCount(0);
  await expect(page.getByText('Table of contents', { exact: true })).toBeHidden();
  for (const name of ['Presenter tree', 'Adaptive rendering']) {
    await expect(leftToc.getByRole('link', { name, exact: true })).toBeVisible();
  }
  const tocBounds = await leftToc.boundingBox();
  const articleBounds = await page.locator('.md-content__inner').boundingBox();
  expect(tocBounds.x + tocBounds.width).toBeLessThanOrEqual(articleBounds.x + 1);
  await leftToc.getByRole('link', { name: 'Adaptive rendering', exact: true }).click();
  await expect(page).toHaveURL(/#adaptive-rendering$/);
  await expect(page.getByRole('heading', { name: /^Adaptive rendering/, level: 2 })).toBeVisible();
  await leftToc.getByRole('link', { name: 'Presenter tree', exact: true }).click();
  // Material renders Mermaid inside a closed shadow root and replaces the source pre.
  const diagram = page.locator('div.mermaid');
  await expect(diagram).toBeVisible();
  await expect.poll(async () => (await diagram.boundingBox())?.height ?? 0).toBeGreaterThan(200);
  for (const viewport of [{ width: 1600, height: 1100 }, { width: 1040, height: 900 }]) {
    await page.setViewportSize(viewport);
    await expect.poll(() => diagram.evaluate(host => {
      const svg = window.__docsShadowRoots.get(host)?.querySelector('svg');
      if (!svg) return Number.POSITIVE_INFINITY;
      const bounds = host.getBoundingClientRect();
      const svgBounds = svg.getBoundingClientRect();
      return Math.abs(bounds.left + bounds.width / 2 - svgBounds.left - svgBounds.width / 2);
    }), `The diagram must be centered at ${viewport.width} pixels wide`).toBeLessThanOrEqual(1);
  }
  await page.setViewportSize({ width: 375, height: 812 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(375);
  await expect.poll(() => diagram.evaluate(host => {
    const svg = window.__docsShadowRoots.get(host)?.querySelector('svg');
    return Boolean(svg && svg.getBoundingClientRect().width <= host.clientWidth);
  })).toBe(true);
  await page.setViewportSize({ width: 1600, height: 1100 });
  const sourceLinks = page.locator('.md-content a[href*="github.com/vRallev/storymile/blob/main/"]');
  expect(await sourceLinks.count()).toBeGreaterThan(0);
  for (const link of await sourceLinks.all()) {
    await expect(link).toHaveAttribute('href', /\/blob\/main\/.+\.kt$/);
  }
  const lightBackground = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  await page.getByTitle('Switch to dark mode', { exact: true }).click();
  await expect(page.locator('body')).toHaveAttribute('data-md-color-scheme', 'slate');
  await expect.poll(() => page.evaluate(() => getComputedStyle(document.body).backgroundColor))
    .not.toBe(lightBackground);
  await expect(diagram).toBeVisible();
  await page.getByTitle('Switch to light mode', { exact: true }).click();
  await expect(page.locator('body')).toHaveAttribute('data-md-color-scheme', 'default');
  await expect.poll(() => page.evaluate(() => getComputedStyle(document.body).backgroundColor))
    .toBe(lightBackground);
  await expect(diagram).toBeVisible();

  await page.setViewportSize({ width: 375, height: 812 });
  // Reload to discard the delayed drawer reset from the earlier anchor navigation.
  await page.reload();
  await expect.poll(async () => (await diagram.boundingBox())?.height ?? 0).toBeGreaterThan(200);
  await page.locator('.md-header label[for="__drawer"]').click();
  await expect(page.locator('#__drawer')).toBeChecked();
  await page.locator('.md-sidebar--primary label.md-nav__link[for="__toc"]').click();
  await expect(page.locator('#__toc')).toBeChecked();
  await expect(leftToc.getByRole('link', { name: 'Presenter tree', exact: true })).toBeVisible();
  await expect(page.getByText('Table of contents', { exact: true })).toBeHidden();
  await leftToc.locator('.md-nav__title').click();
  await expect(page.locator('#__toc')).not.toBeChecked();
  await page.locator('.md-sidebar--primary').getByRole('link', { name: 'Home', exact: true }).click();
  await expect(page.getByRole('heading', { name: /^Storymile/, level: 1 })).toBeVisible();
});

docsHostTest('opens the design gallery, enlarges a screen, and switches views', async ({ page, request }, testInfo) => {
  const errors = [];
  const localPaths = [];
  page.on('pageerror', error => errors.push(error.stack ?? error.message));
  page.on('requestfailed', failedRequest => {
    const url = new URL(failedRequest.url());
    const failure = failedRequest.failure()?.errorText;
    // Switching gallery views releases images that are no longer needed.
    if (url.origin === 'http://127.0.0.1:4174' && failure !== 'net::ERR_ABORTED') {
      errors.push(`${failedRequest.method()} ${url}: ${failure}`);
    }
  });
  page.on('response', response => {
    const url = new URL(response.url());
    if (url.origin !== 'http://127.0.0.1:4174') return;
    localPaths.push(url.pathname);
    if (response.status() >= 400) errors.push(`${response.status()} ${url}`);
  });

  try {
    await page.goto('./');
    await page.locator('.md-tabs').getByRole('link', { name: 'Design research', exact: true }).click();
    await expect(page).toHaveURL(/\/storymile\/design-research\/$/);
    await expect(page.locator('.md-content__button')).toHaveCount(0);
    await expect(page.getByTitle('Storymile design research', { exact: true }))
      .toHaveAttribute('src', 'gallery/index.html');
    const gallery = page.frameLocator('iframe[title="Storymile design research"]');
    for (const name of ['Themes', 'Android / iOS', 'Flows']) {
      await expect(gallery.getByRole('tab', { name, exact: true })).toBeVisible();
    }
    await expect(gallery.getByRole('tab', { name: 'Themes', exact: true }))
      .toHaveAttribute('aria-selected', 'true');
    const galleryRoot = gallery.locator('html');
    const siteLightColors = await page.evaluate(() => ({
      background: getComputedStyle(document.body).backgroundColor,
      text: getComputedStyle(document.body).color,
    }));
    await expect.poll(() => galleryAppearance(galleryRoot)).toMatchObject({
      theme: 'light', colorScheme: 'light', ...siteLightColors,
    });
    await expectGalleryIcon(gallery, 'light');
    await gallery.locator('#device-filters').getByRole('checkbox', { name: 'Dark', exact: true }).uncheck();

    const previewButton = gallery.locator('button.image-button').first();
    await expect(previewButton).toHaveAttribute('data-load-state', 'loaded');
    await expect.poll(() => previewButton.locator('img').evaluate(image =>
      image.complete && image.naturalWidth > 0)).toBe(true);
    await previewButton.click();
    const dialog = gallery.getByRole('dialog');
    await expect(dialog).toBeVisible();
    const fullImage = dialog.locator('#image-stage img');
    await expect(fullImage).toHaveAttribute('data-load-state', 'loaded');
    await expect.poll(() => fullImage.evaluate(image =>
      image.complete && image.naturalWidth > 0)).toBe(true);
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();

    await gallery.getByRole('tab', { name: 'Flows', exact: true }).click();
    await expect(gallery.getByRole('tab', { name: 'Flows', exact: true }))
      .toHaveAttribute('aria-selected', 'true');
    await expect(gallery.getByRole('tab', { name: 'Themes', exact: true }))
      .toHaveAttribute('aria-selected', 'false');
    await expect(gallery.locator('#gallery')).toHaveAttribute('aria-labelledby', 'tab-flows');
    await expect(gallery.locator('button.image-button').first())
      .toHaveAttribute('data-load-state', 'loaded');
    await galleryRoot.evaluate(() => {
      window.__docsGalleryState = 'preserve gallery document';
      window.scrollTo(0, 180);
    });
    await gallery.locator('button.image-button').first().click();
    await expect(dialog).toBeVisible();
    await expect(fullImage).toHaveAttribute('data-load-state', 'loaded');
    await dialog.getByRole('button', { name: 'Actual size', exact: true }).click();
    await gallery.locator('#image-scroll').evaluate(element => { element.scrollTop = 100; });
    const readGalleryState = () => galleryRoot.evaluate(() => ({
      document: window.__docsGalleryState,
      view: document.querySelector('.tab[aria-selected="true"]').dataset.view,
      filters: [...document.querySelectorAll('#device-filters input')]
        .map(input => ({ value: input.value, checked: input.checked })),
      scroll: window.scrollY,
      images: [...document.querySelectorAll('button.image-button')].map(button => button.dataset.screenId),
      viewerOpen: document.querySelector('#image-dialog').open,
      viewerImage: document.querySelector('#image-stage img')?.getAttribute('src'),
      viewerTitle: document.querySelector('#viewer-title').textContent,
      viewerScroll: document.querySelector('#image-scroll').scrollTop,
      viewerSize: document.querySelector('#viewer-size').getAttribute('aria-pressed'),
    }));
    const preservedState = await readGalleryState();
    expect(preservedState.view).toBe('flows');
    expect(preservedState.filters.find(filter => filter.value === 'dark').checked).toBe(false);
    expect(preservedState.scroll).toBeGreaterThan(0);
    expect(preservedState.viewerScroll).toBe(100);
    expect(await gallery.locator('button.image-button img').evaluateAll(images =>
      images.every(image => image.alt.includes('light mode')))).toBe(true);
    const lightAppearance = await galleryAppearance(galleryRoot);
    await page.getByTitle('Switch to dark mode', { exact: true }).click();
    await expect(page.locator('body')).toHaveAttribute('data-md-color-scheme', 'slate');
    const siteDarkColors = await page.evaluate(() => ({
      background: getComputedStyle(document.body).backgroundColor,
      text: getComputedStyle(document.body).color,
    }));
    await expect.poll(() => galleryAppearance(galleryRoot)).toMatchObject({
      theme: 'dark', colorScheme: 'dark', ...siteDarkColors,
    });
    await expectGalleryIcon(gallery, 'dark');
    const darkAppearance = await galleryAppearance(galleryRoot);
    for (const property of ['background', 'text', 'activeBackground', 'activeText', 'controlText', 'controlBorder']) {
      expect(darkAppearance[property], `${property} must use the dark palette`).not.toBe(lightAppearance[property]);
    }
    expect(await readGalleryState()).toEqual(preservedState);
    await page.getByTitle('Switch to light mode', { exact: true }).click();
    await expect.poll(() => galleryAppearance(galleryRoot)).toEqual(lightAppearance);
    await expectGalleryIcon(gallery, 'light');
    expect(await readGalleryState()).toEqual(preservedState);
    await dialog.getByRole('button', { name: 'Close', exact: true }).focus();
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();

    // A saved site preference must take precedence over the browser's light preference on startup.
    await page.getByTitle('Switch to dark mode', { exact: true }).click();
    await expect(page.locator('body')).toHaveAttribute('data-md-color-scheme', 'slate');
    await page.reload();
    await expect(page.locator('body')).toHaveAttribute('data-md-color-scheme', 'slate');
    await expect.poll(() => galleryAppearance(galleryRoot)).toMatchObject({
      theme: 'dark', colorScheme: 'dark', ...siteDarkColors,
    });
    await expectGalleryIcon(gallery, 'dark');
    await page.getByTitle('Switch to light mode', { exact: true }).click();
    await expect.poll(() => galleryAppearance(galleryRoot)).toMatchObject({
      theme: 'light', colorScheme: 'light', ...siteLightColors,
    });
    await gallery.getByRole('tab', { name: 'Flows', exact: true }).click();
    await expect(gallery.getByRole('link', { name: 'Prompts', exact: true }))
      .toHaveAttribute('href', 'prompts.json');
    const promptsResponse = await request.get('design-research/gallery/prompts.json');
    expect(promptsResponse.ok(), 'The gallery prompts must be published').toBe(true);
    const prompts = await promptsResponse.json();
    expect(prompts.screens.length).toBeGreaterThan(0);
    expect(prompts.screens[0].prompt).toBeTruthy();

    const galleryFrame = page.getByTitle('Storymile design research', { exact: true });
    for (const viewport of [
      { width: 1600, height: 1100 },
      { width: 375, height: 812 },
      { width: 1600, height: 500 },
    ]) {
      await page.setViewportSize(viewport);
      await expect.poll(() => galleryFrame.evaluate(element => {
        const frame = element.getBoundingClientRect();
        const chromeBottom = Math.max(...[...document.querySelectorAll('.md-header, .md-tabs')]
          .filter(chrome => {
            const style = getComputedStyle(chrome);
            return style.display !== 'none' && style.visibility !== 'hidden' && chrome.getClientRects().length;
          })
          .map(chrome => chrome.getBoundingClientRect().bottom));
        return Math.max(Math.abs(frame.left), Math.abs(frame.right - innerWidth),
          Math.abs(frame.bottom - innerHeight), Math.abs(frame.top - chromeBottom));
      }), `The gallery must fill the viewport below site navigation at ${viewport.width} × ${viewport.height}`)
        .toBeLessThanOrEqual(1);
      await expect.poll(() => page.evaluate(() => {
        const scrolling = document.scrollingElement;
        return scrolling.scrollWidth <= innerWidth && scrolling.scrollHeight <= innerHeight;
      }), 'The documentation page must not add a second scrollbar').toBe(true);
      await expect(page.locator('.md-content h1')).toBeHidden();
      await expect(page.locator('.md-footer')).toBeHidden();
      await expect(page.locator('.md-content__button')).toBeHidden();
      await expect(gallery.getByRole('tab', { name: 'Flows', exact: true })).toBeVisible();
    }

    // Native navigation must remain usable, and the fullscreen layout must stay on this page.
    await page.setViewportSize({ width: 375, height: 812 });
    await page.locator('.md-header label[for="__drawer"]').click();
    await expect(page.locator('#__drawer')).toBeChecked();
    await page.locator('.md-sidebar--primary').getByRole('link', { name: 'Home', exact: true }).click();
    await expect(page.getByRole('heading', { name: /^Storymile/, level: 1 })).toBeVisible();
    await expect(page.locator('.storymile-intro + [data-app-preview]')).toBeAttached();
    await expect(page.locator('.storymile-links, .storymile-intro .md-button')).toHaveCount(0);
    await expect(page.getByRole('heading', { name: /^Try Storymile/ })).toHaveCount(0);
    await page.setViewportSize({ width: 1600, height: 1100 });
    await expectPreviewSize(page.getByTitle('Storymile app', { exact: true }), 480, 840);
    expect((await page.getByTitle('Storymile app', { exact: true }).boundingBox()).x).toBeGreaterThan(0);

    expect(localPaths.every(path => path.startsWith('/storymile/')),
      'All gallery resources must load below the GitHub Pages project path').toBe(true);
    expect(errors, 'Browser errors while using the published design gallery').toEqual([]);
  } finally {
    await testInfo.attach('gallery-browser-errors', {
      body: errors.join('\n') || 'No browser errors.',
      contentType: 'text/plain',
    });
  }
});

docsHostTest('follows system theme changes in the standalone design gallery', async ({ page }) => {
  await page.goto('design-research/gallery/index.html');
  const root = page.locator('html');
  await expect.poll(() => galleryAppearance(root)).toMatchObject({
    theme: 'light', colorScheme: 'light', background: 'rgb(247, 243, 235)', text: 'rgb(35, 59, 80)',
  });
  await expectGalleryIcon(page, 'light');
  await root.evaluate(() => { window.__docsGalleryState = 'same standalone gallery'; });
  await page.emulateMedia({ colorScheme: 'dark' });
  await expect.poll(() => galleryAppearance(root)).toMatchObject({
    theme: 'dark', colorScheme: 'dark', background: 'rgb(15, 32, 45)', text: 'rgb(244, 239, 229)',
  });
  await expectGalleryIcon(page, 'dark');
  await page.emulateMedia({ colorScheme: 'light' });
  await expect.poll(() => galleryAppearance(root)).toMatchObject({
    theme: 'light', colorScheme: 'light', background: 'rgb(247, 243, 235)', text: 'rgb(35, 59, 80)',
  });
  await expectGalleryIcon(page, 'light');
  expect(await root.evaluate(() => window.__docsGalleryState)).toBe('same standalone gallery');
  await expect(page.getByRole('tab', { name: 'Themes', exact: true })).toHaveAttribute('aria-selected', 'true');
  for (const name of ['Light', 'Dark']) {
    await expect(page.getByRole('checkbox', { name, exact: true })).toBeChecked();
  }
});
