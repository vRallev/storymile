/* Limit image requests and decoding; the gallery decides which images to retain. */
(() => {
  'use strict';

  globalThis.StorymileImageLoader = class StorymileImageLoader {
    constructor({concurrency = 4,timeout = 10000,retryDelay = 300,maxAttempts = 3} = {}) {
      this.concurrency = Math.max(1,Math.floor(concurrency));
      this.timeout = Math.max(1,timeout);
      this.retryDelay = Math.max(0,retryDelay);
      this.maxAttempts = Math.max(1,Math.floor(maxAttempts));
      this.jobs = new Map();
      this.active = 0;
      this.sequence = 0;
      this.pumping = false;
      this.batchDepth = 0;
    }

    load(img,src,{priority = 0,onState = () => {}} = {}) {
      let job = this.jobs.get(img);
      if (job && job.src !== src) {
        this.release(img);
        job = null;
      }
      if (job) {
        job.priority = priority;
        job.onState = onState;
      } else {
        job = {img,src,priority,onState,state:'queued',order:this.sequence++,attempts:0,token:0,waiting:false};
        this.jobs.set(img,job);
      }
      this.notify(job);
      this.pump();
    }

    state(img) {
      return this.jobs.get(img)?.state || 'idle';
    }

    retry(img) {
      const job = this.jobs.get(img);
      if (!job || job.state !== 'error') return;
      job.attempts = 0;
      job.state = 'queued';
      this.notify(job);
      this.pump();
    }

    release(img) {
      const job = this.jobs.get(img);
      if (!job) return;
      this.jobs.delete(img);
      clearTimeout(job.retryTimer);
      this.cleanAttempt(job);
      img.removeAttribute('src');
      this.pump();
    }

    clear() {
      this.batch(() => {
        for (const img of this.jobs.keys()) this.release(img);
      });
    }

    batch(callback) {
      this.batchDepth++;
      try {
        return callback();
      } finally {
        this.batchDepth--;
        this.pump();
      }
    }

    notify(job) {
      job.onState(job.state);
    }

    owns(job,token) {
      return this.jobs.get(job.img) === job && job.token === token && job.state === 'loading';
    }

    cleanAttempt(job) {
      job.token++;
      if (!job.cleanup) return;
      job.cleanup();
      job.cleanup = null;
      this.active--;
    }

    pump() {
      if (this.pumping || this.batchDepth) return;
      this.pumping = true;
      try {
        while (this.active < this.concurrency) {
          const next = [...this.jobs.values()]
            .filter(job => job.state === 'queued' && !job.waiting)
            .sort((a,b) => b.priority - a.priority || a.order - b.order)[0];
          if (!next) break;
          this.start(next);
        }
      } finally {
        this.pumping = false;
      }
    }

    start(job) {
      const {img} = job;
      const token = ++job.token;
      job.state = 'loading';
      job.attempts++;
      this.active++;
      let decoding = false;
      const finish = success => {
        if (!this.owns(job,token)) return;
        this.cleanAttempt(job);
        if (success) {
          job.state = 'loaded';
        } else {
          img.removeAttribute('src');
          job.state = job.attempts < this.maxAttempts ? 'queued' : 'error';
          if (job.state === 'queued') {
            job.waiting = true;
            job.retryTimer = setTimeout(() => {
              if (this.jobs.get(img) !== job) return;
              job.waiting = false;
              this.pump();
            },this.retryDelay);
          }
        }
        this.notify(job);
        this.pump();
      };
      const loaded = () => {
        if (!this.owns(job,token) || decoding) return;
        if (!img.naturalWidth) { finish(false); return; }
        decoding = true;
        if (typeof img.decode !== 'function') { finish(true); return; }
        try {
          Promise.resolve(img.decode()).then(() => finish(true),() => finish(false));
        } catch {
          finish(false);
        }
      };
      const failed = () => finish(false);
      const watchdog = setTimeout(failed,this.timeout);
      job.cleanup = () => {
        clearTimeout(watchdog);
        img.removeEventListener('load',loaded);
        img.removeEventListener('error',failed);
      };
      img.addEventListener('load',loaded);
      img.addEventListener('error',failed);
      this.notify(job);
      if (!this.owns(job,token)) return;
      img.loading = 'eager';
      img.decoding = 'sync';
      img.src = job.src;
      // Cached images may already be complete without a new event.
      if (img.complete) Promise.resolve().then(loaded);
    }
  };
})();
