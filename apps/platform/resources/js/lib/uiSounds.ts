import { ref } from 'vue';

export type UiCue = 'tap' | 'correct' | 'adjust' | 'recorded' | 'complete';
export const soundsEnabled = ref(true);
export function feedbackCue(outcome: string): UiCue {
  return outcome === 'correct' ? 'correct' : outcome === 'incorrect' ? 'adjust' : 'recorded';
}
let context: AudioContext | undefined;
let source: AudioBufferSourceNode | undefined;
let generation = 0;
const buffers = new Map<UiCue, Promise<AudioBuffer>>();
function stop() {
  generation++;
  try { source?.stop(); } catch { /* It may already have ended. */ }
  source = undefined;
}
function audioContext(): AudioContext | undefined {
  if (typeof AudioContext === 'undefined') return;
  return context ??= new AudioContext();
}
export async function playUiSound(cue: UiCue): Promise<void> {
  if (!soundsEnabled.value || document.hidden) return;
  stop();
  const request = generation;
  try {
    const audio = audioContext();
    if (!audio) return;
    if (audio.state === 'suspended') await audio.resume();
    let buffer = buffers.get(cue);
    if (!buffer) {
      buffer = fetch(`/ui-sounds/${cue}.wav`).then(response => {
        if (!response.ok) throw new Error('Sound unavailable');
        return response.arrayBuffer();
      }).then(bytes => audio.decodeAudioData(bytes));
      buffers.set(cue, buffer);
      void buffer.catch(() => buffers.delete(cue));
    }
    const decoded = await buffer;
    if (request !== generation || !soundsEnabled.value || document.hidden || audio.state !== 'running') return;
    const node = audio.createBufferSource();
    source = node;
    node.buffer = decoded;
    const volume = audio.createGain();
    volume.gain.value = .55;
    node.connect(volume); volume.connect(audio.destination);
    node.onended = () => { node.disconnect(); volume.disconnect(); if (source === node) source = undefined; };
    node.start();
  } catch { /* Optional UI audio never blocks practice or creates a learning event. */ }
}
export function toggleSounds() {
  soundsEnabled.value = !soundsEnabled.value;
  try { localStorage.setItem('idiomas-ui-sounds', String(soundsEnabled.value)); } catch { /* Session preference still works. */ }
  if (soundsEnabled.value) void playUiSound('tap');
  else stop();
}
export function initializeUiSounds() {
  try { soundsEnabled.value = localStorage.getItem('idiomas-ui-sounds') !== 'false'; } catch { /* Use the default. */ }
  const unlock = () => {
    if (!soundsEnabled.value || document.hidden) return;
    try { void audioContext()?.resume().catch(() => {}); } catch { /* Audio may be disabled by the browser. */ }
  };
  document.addEventListener('pointerdown', unlock, { passive: true });
  document.addEventListener('keydown', unlock);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { stop(); void context?.suspend().catch(() => {}); }
  });
}
