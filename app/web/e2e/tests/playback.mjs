import { expect } from '@playwright/test';

export async function verifyFakePlayback(page, path) {
  // Observe the real media element without replacing browser playback.
  await page.addInitScript(() => {
    const NativeAudio = window.Audio;
    window.Audio = new Proxy(NativeAudio, {
      construct(target, args) {
        const audio = Reflect.construct(target, args);
        window.storymileTestAudio = audio;
        return audio;
      },
    });
  });
  await page.goto(path);
  const logo = page.locator('[id="storymile-logo"], [id="tab-brand-logo"]').first();
  await expect(logo).toBeAttached();
  // The hidden fake-mode switch requires a five-second hold on the rendered logo.
  await logo.click({ force: true, delay: 5_500 });
  await expect(page.locator('[id="playback"]')).toBeAttached();

  await expect.poll(() => page.evaluate(() => {
    const audio = window.storymileTestAudio;
    return audio && {
      ready: audio.readyState >= 1,
      duration: audio.duration > 0,
      error: audio.error?.message ?? null,
      path: new URL(audio.src).pathname,
    };
  })).toEqual({
    ready: true,
    duration: true,
    error: null,
    path: new URL('composeResources/software.ralf.storymile.player.impl.generated.resources/files/test-episodes/test-episode-1.mp3', page.url()).pathname,
  });

  // Media readiness can precede initialization of the Compose semantics node.
  const play = page.locator('[id="playback-bar-play-pause"][aria-label="Play"]');
  await expect(play).toBeVisible();
  await play.click({ force: true });
  await expect.poll(() => page.evaluate(() => window.storymileTestAudio.currentTime)).toBeGreaterThan(0);
  await expect.poll(() => page.evaluate(() => window.storymileTestAudio.paused)).toBe(false);
  // Audio starts before Compose replaces the Play semantics node with Pause.
  const pause = page.locator('[id="playback-bar-play-pause"][aria-label="Pause"]');
  await expect(pause).toBeVisible();
  await pause.click({ force: true });
  await expect.poll(() => page.evaluate(() => window.storymileTestAudio.paused)).toBe(true);
  expect(await page.evaluate(() => window.storymileTestAudio.error)).toBeNull();
}
