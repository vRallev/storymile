const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname,'image-loader.js'),'utf8');
const flush = async () => { for (let i = 0; i < 4; i++) await Promise.resolve(); };
const deferred = () => {
  let resolve,reject;
  const promise = new Promise((yes,no) => { resolve = yes; reject = no; });
  return {promise,resolve,reject};
};

function environment(options = {}) {
  let now = 0,id = 0;
  const timers = new Map();
  const context = vm.createContext({
    setTimeout(callback,delay) { timers.set(++id,{at:now + delay,callback}); return id; },
    clearTimeout(key) { timers.delete(key); }
  });
  vm.runInContext(source,context);
  const loader = new context.StorymileImageLoader({timeout:100,retryDelay:10,...options});
  return {
    loader,timers,
    async advance(duration) {
      const end = now + duration;
      await flush();
      for (;;) {
        const next = [...timers.entries()].filter(([,task]) => task.at <= end).sort((a,b) => a[1].at - b[1].at)[0];
        if (!next) break;
        timers.delete(next[0]); now = next[1].at; next[1].callback();
        await flush();
      }
      now = end;
      await flush();
    }
  };
}

class FakeImage {
  constructor(onSource) {
    this.events = new Map();
    this.requests = [];
    this.complete = false;
    this.naturalWidth = 0;
    this.onSource = onSource;
    this.decode = () => Promise.resolve();
  }
  addEventListener(type,listener) {
    if (!this.events.has(type)) this.events.set(type,new Set());
    this.events.get(type).add(listener);
  }
  removeEventListener(type,listener) { this.events.get(type)?.delete(listener); }
  emit(type) { for (const listener of [...this.events.get(type) || []]) listener(); }
  set src(value) {
    assert.equal(this.loading,'eager');
    assert.equal(this.decoding,'sync');
    this.url = value; this.complete = false; this.naturalWidth = 0;
    this.requests.push(value); this.onSource?.(this);
  }
  get src() { return this.url || ''; }
  removeAttribute(name) {
    assert.equal(name,'src');
    this.url = ''; this.complete = false; this.naturalWidth = 0;
  }
  succeed() { this.complete = true; this.naturalWidth = 800; this.emit('load'); }
  listenerCount() { return [...this.events.values()].reduce((sum,listeners) => sum + listeners.size,0); }
}

test('limits requests and decoding together; identical URLs have independent consumers',async () => {
  const {loader,timers} = environment({concurrency:2});
  const images = Array.from({length:5},() => new FakeImage());
  const decode = deferred();
  images[0].decode = () => decode.promise;
  images.forEach(img => loader.load(img,'same.webp'));
  assert.deepEqual(images.map(img => img.requests.length),[1,1,0,0,0]);
  images[0].succeed(); await flush();
  assert.equal(loader.state(images[2]),'queued');
  images[1].succeed(); await flush();
  assert.equal(loader.state(images[2]),'loading');
  decode.resolve(); await flush();
  assert.equal(loader.state(images[3]),'loading');
  images[2].succeed(); images[3].succeed(); await flush();
  images[4].succeed(); await flush();
  assert.ok(images.every(img => loader.state(img) === 'loaded'));
  assert.equal(timers.size,0);
  assert.ok(images.every(img => img.listenerCount() === 0));
});

test('retries errors finitely, reports failure, and permits manual recovery',async () => {
  const {loader,advance,timers} = environment();
  const img = new FakeImage();
  const states = [];
  loader.load(img,'preview.webp',{onState:state => states.push(state)});
  for (let attempt = 1; attempt <= 3; attempt++) {
    img.emit('error');
    assert.equal(loader.state(img),attempt < 3 ? 'queued' : 'error');
    await advance(10);
  }
  assert.equal(img.requests.length,3);
  assert.equal(timers.size,0);
  loader.load(img,'preview.webp');
  assert.equal(img.requests.length,3,'routine refresh must not restart exhausted work');
  loader.retry(img); img.succeed(); await flush();
  assert.equal(loader.state(img),'loaded');
  assert.equal(img.requests.length,4);
  assert.deepEqual(states,['queued','loading','queued','loading','queued','loading','error']);
});

test('network and decode stalls free slots and ignore late completion',async () => {
  const {loader,advance} = environment({concurrency:1,maxAttempts:1});
  const network = new FakeImage();
  const decoding = new FakeImage();
  const next = new FakeImage();
  const decode = deferred(); decoding.decode = () => decode.promise;
  loader.load(network,'network.webp');
  const oldLoad = [...network.events.get('load')][0];
  loader.load(decoding,'decode.webp'); loader.load(next,'next.webp');
  await advance(100);
  assert.equal(loader.state(network),'error');
  decoding.succeed(); await flush();
  await advance(100);
  assert.equal(loader.state(decoding),'error');
  assert.equal(loader.state(next),'loading');
  oldLoad(); decode.resolve(); await flush();
  assert.equal(loader.state(network),'error');
  assert.equal(loader.state(decoding),'error');
  assert.equal(loader.state(next),'loading');
  next.succeed(); await flush();
  assert.equal(loader.state(next),'loaded');
});

test('rejected decode and invalid loaded dimensions follow the bounded retry path',async () => {
  const {loader,advance} = environment({maxAttempts:2});
  const img = new FakeImage();
  img.decode = () => Promise.reject(new Error('Decode failed'));
  loader.load(img,'preview.webp'); img.succeed(); await flush();
  assert.equal(loader.state(img),'queued');
  await advance(10);
  img.emit('load');
  assert.equal(loader.state(img),'error');
  assert.equal(img.requests.length,2);
});

test('release and clear cancel timers, listeners, decoded memory, and pending starts',async () => {
  const {loader,advance,timers} = environment({concurrency:1});
  const first = new FakeImage(),second = new FakeImage(),third = new FakeImage();
  loader.load(first,'first.webp'); loader.load(second,'second.webp'); loader.load(third,'third.webp');
  const oldError = [...first.events.get('error')][0];
  loader.release(first);
  assert.equal(first.src,''); assert.equal(first.listenerCount(),0);
  assert.equal(loader.state(first),'idle'); assert.equal(loader.state(second),'loading');
  oldError();
  second.emit('error');
  assert.equal(loader.state(third),'loading');
  loader.clear();
  await advance(1000);
  assert.equal(timers.size,0);
  assert.equal(third.requests.length,1);
  for (const img of [first,second,third]) {
    assert.equal(loader.state(img),'idle'); assert.equal(img.src,'');
    assert.equal(img.listenerCount(),0);
  }
});

test('reprioritizes queued requests without starting duplicate work',async () => {
  const {loader} = environment({concurrency:1});
  const first = new FakeImage(),second = new FakeImage(),third = new FakeImage();
  loader.load(first,'first.webp'); loader.load(second,'second.webp'); loader.load(third,'third.webp');
  loader.load(third,'third.webp',{priority:10});
  first.succeed(); await flush();
  assert.equal(loader.state(third),'loading'); assert.equal(loader.state(second),'queued');
  third.succeed(); await flush();
  second.succeed(); await flush();
  assert.deepEqual([first,second,third].map(img => img.requests.length),[1,1,1]);
});

test('same-source registration updates callbacks and preserves loaded images',async () => {
  const {loader} = environment();
  const img = new FakeImage();
  const oldStates = [],newStates = [],reparentedStates = [];
  loader.load(img,'preview.webp',{onState:state => oldStates.push(state)});
  loader.load(img,'preview.webp',{onState:state => newStates.push(state)});
  img.succeed(); await flush();
  loader.load(img,'preview.webp',{onState:state => reparentedStates.push(state)});
  assert.deepEqual(oldStates,['queued','loading']);
  assert.deepEqual(newStates,['loading','loaded']);
  assert.deepEqual(reparentedStates,['loaded']);
  assert.equal(img.requests.length,1);
});

test('cached and synchronous load events cannot leak concurrency slots',async () => {
  const {loader,timers} = environment({concurrency:1});
  const cached = new FakeImage(img => { img.complete = true; img.naturalWidth = 800; });
  const synchronous = new FakeImage(img => img.succeed());
  const immediateError = new FakeImage(img => img.emit('error'));
  cached.decode = undefined; synchronous.decode = undefined;
  loader.load(cached,'cached.webp'); loader.load(synchronous,'sync.webp');
  await flush();
  assert.equal(loader.state(cached),'loaded'); assert.equal(loader.state(synchronous),'loaded');
  loader.load(immediateError,'error.webp');
  loader.clear();
  assert.equal(timers.size,0);
});

test('source replacement and release protect against stale decode promises',async () => {
  const {loader,timers} = environment({maxAttempts:1});
  const img = new FakeImage();
  const old = deferred(); img.decode = () => old.promise;
  loader.load(img,'old.webp'); img.succeed();
  loader.load(img,'new.webp');
  old.reject(new Error('Late old failure')); await flush();
  assert.equal(loader.state(img),'loading'); assert.equal(img.src,'new.webp');
  const current = deferred(); img.decode = () => current.promise;
  img.succeed(); loader.release(img); current.resolve(); await flush();
  assert.equal(loader.state(img),'idle'); assert.equal(timers.size,0);
});

test('callbacks can synchronously release work without stranding the queue',async () => {
  const {loader,timers} = environment({concurrency:1});
  const removed = new FakeImage(),next = new FakeImage();
  loader.load(removed,'removed.webp',{onState:state => { if (state === 'loading') loader.release(removed); }});
  loader.load(next,'next.webp'); next.succeed(); await flush();
  assert.equal(removed.requests.length,0);
  assert.equal(loader.state(next),'loaded'); assert.equal(timers.size,0);
});

test('nested refresh batches cancel obsolete work before prioritizing replacements',async () => {
  const {loader} = environment({concurrency:1});
  const old = Array.from({length:3},() => new FakeImage());
  const low = new FakeImage(),high = new FakeImage();
  old.forEach((img,index) => loader.load(img,`old-${index}.webp`));
  const result = loader.batch(() => {
    loader.release(old[0]);
    loader.batch(() => {
      loader.release(old[1]);
      loader.load(low,'low.webp');
    });
    loader.release(old[2]);
    loader.load(high,'high.webp',{priority:10});
    assert.equal(high.requests.length,0);
    assert.equal(low.requests.length,0);
    return 'refreshed';
  });
  assert.equal(result,'refreshed');
  assert.deepEqual(old.map(img => img.requests.length),[1,0,0]);
  assert.equal(loader.state(high),'loading'); assert.equal(loader.state(low),'queued');
  high.succeed(); await flush();
  assert.equal(loader.state(low),'loading');
  low.succeed(); await flush();
});

test('a throwing batch resumes scheduling and clear can run inside a batch',async () => {
  const {loader,timers} = environment({concurrency:1});
  const first = new FakeImage(),obsolete = new FakeImage(),next = new FakeImage();
  loader.load(first,'first.webp'); loader.load(obsolete,'obsolete.webp');
  assert.throws(() => loader.batch(() => {
    loader.clear();
    loader.load(next,'next.webp');
    throw new Error('Interrupted refresh');
  }),/Interrupted refresh/);
  assert.equal(obsolete.requests.length,0);
  assert.equal(loader.state(first),'idle'); assert.equal(loader.state(next),'loading');
  next.succeed(); await flush();
  assert.equal(loader.state(next),'loaded'); assert.equal(timers.size,0);
});
