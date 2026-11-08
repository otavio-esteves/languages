import assert from 'node:assert/strict';
import test from 'node:test';
// Load Vue before installing the deliberately minimal audio-only document fake.
import 'vue';

let serial = 0;
async function fixture(saved = null) {
  const events = new EventTarget();
  events.hidden = false;
  globalThis.document = events;
  const storage = new Map(saved === null ? [] : [['idiomas-ui-sounds', saved]]);
  globalThis.localStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) };
  const calls = { start: 0, stop: 0, suspend: 0 };
  let decode = async () => ({});
  globalThis.AudioContext = class {
    state = 'running'; destination = {};
    async resume() { this.state = 'running'; }
    async suspend() { this.state = 'suspended'; calls.suspend++; }
    decodeAudioData() { return decode(); }
    createGain() { return { gain: {}, connect() {}, disconnect() {} }; }
    createBufferSource() { return { connect() {}, disconnect() {}, start() { calls.start++; }, stop() { calls.stop++; } }; }
  };
  globalThis.fetch = async () => ({ ok: true, arrayBuffer: async () => new ArrayBuffer(0) });
  const sounds = await import(`../../resources/js/lib/uiSounds.ts?test=${++serial}`);
  sounds.initializeUiSounds();
  return { sounds, calls, storage, events, decode: value => { decode = value; } };
}

test('saved mute prevents all playback and persists when toggled', async () => {
  const f = await fixture('false');
  await f.sounds.playUiSound('correct');
  assert.equal(f.calls.start, 0);
  f.sounds.toggleSounds();
  assert.equal(f.storage.get('idiomas-ui-sounds'), 'true');
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(f.calls.start, 1);
  f.sounds.toggleSounds();
  await f.sounds.playUiSound('complete');
  assert.equal(f.calls.start, 1);
  assert.equal(f.storage.get('idiomas-ui-sounds'), 'false');
});

test('muting during a delayed load cancels playback rather than sounding later', async () => {
  const f = await fixture();
  let ready;
  f.decode(() => new Promise(resolve => { ready = resolve; }));
  const playback = f.sounds.playUiSound('correct');
  await new Promise(resolve => setTimeout(resolve, 0));
  f.sounds.toggleSounds();
  ready({}); await playback;
  assert.equal(f.calls.start, 0);
});

test('backgrounding stops audio and never replays a celebration on return', async () => {
  const f = await fixture();
  await f.sounds.playUiSound('complete');
  f.events.hidden = true;
  f.events.dispatchEvent(new Event('visibilitychange'));
  assert.equal(f.calls.stop, 1);
  assert.equal(f.calls.suspend, 1);
  await f.sounds.playUiSound('complete');
  f.events.hidden = false;
  f.events.dispatchEvent(new Event('visibilitychange'));
  assert.equal(f.calls.start, 1);
});

test('unavailable audio and denied storage remain optional and can recover', async () => {
  const f = await fixture();
  globalThis.fetch = async () => { throw new Error('Sound unavailable'); };
  await assert.doesNotReject(f.sounds.playUiSound('tap'));
  globalThis.fetch = async () => ({ ok: true, arrayBuffer: async () => new ArrayBuffer(0) });
  await f.sounds.playUiSound('tap');
  assert.equal(f.calls.start, 1);
  globalThis.localStorage = { getItem() { throw new Error('Denied'); }, setItem() { throw new Error('Denied'); } };
  assert.doesNotThrow(() => f.sounds.toggleSounds());
  assert.equal(f.sounds.soundsEnabled.value, false);
});
