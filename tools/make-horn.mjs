// Generates bitsports' original 8-bit goal horn: a square-wave arena-style horn chord with
// chiptune vibrato, then a siren sweep. Writes the same WAV for Android and the web page.
// Run: node tools/make-horn.mjs
import { writeFileSync } from "node:fs";

const RATE = 22050;
const samples = [];

const square = (phase) => (phase % 1 < 0.5 ? 1 : -1);

function horn(seconds) {
  const notes = [233.08, 311.13, 466.16]; // Bb3, Eb4, Bb4: the classic horn interval, chiptuned
  const phases = notes.map(() => 0);
  const n = Math.round(seconds * RATE);
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    const vibrato = 1 + 0.012 * Math.sign(Math.sin(2 * Math.PI * 6 * t)); // stepped, 8-bit style
    let v = 0;
    notes.forEach((f, k) => {
      phases[k] += (f * vibrato) / RATE;
      v += square(phases[k]) * (k === 2 ? 0.18 : 0.26);
    });
    const attack = Math.min(1, t / 0.03);
    const release = Math.min(1, (seconds - t) / 0.08);
    samples.push(v * attack * release);
  }
}

function siren(seconds) {
  let phase = 0;
  const n = Math.round(seconds * RATE);
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    const x = t / seconds;
    const f = 600 + 300 * Math.sin(Math.PI * x); // 600 → 900 → 600 Hz
    phase += f / RATE;
    // Quantize frequency steps a little for a chiptune wail.
    const v = square(Math.floor(phase * 8) / 8) * 0.32;
    const fade = Math.min(1, (seconds - t) / 0.25) * Math.min(1, t / 0.02);
    samples.push(v * fade);
  }
}

function silence(seconds) {
  for (let i = 0; i < Math.round(seconds * RATE); i++) samples.push(0);
}

horn(0.55);
silence(0.06);
horn(0.6);
silence(0.05);
siren(0.95);

const data = Buffer.alloc(samples.length * 2);
samples.forEach((s, i) => data.writeInt16LE(Math.max(-1, Math.min(1, s)) * 32000 | 0, i * 2));
const header = Buffer.alloc(44);
header.write("RIFF", 0);
header.writeUInt32LE(36 + data.length, 4);
header.write("WAVE", 8);
header.write("fmt ", 12);
header.writeUInt32LE(16, 16);
header.writeUInt16LE(1, 20); // PCM
header.writeUInt16LE(1, 22); // mono
header.writeUInt32LE(RATE, 24);
header.writeUInt32LE(RATE * 2, 28);
header.writeUInt16LE(2, 32);
header.writeUInt16LE(16, 34);
header.write("data", 36);
header.writeUInt32LE(data.length, 40);
const wav = Buffer.concat([header, data]);

for (const out of ["android/app/src/main/res/raw/goal_horn.wav", "assets/goal_horn.wav"]) {
  writeFileSync(new URL(`../${out}`, import.meta.url), wav);
  console.log(`${out}  ${(wav.length / 1024).toFixed(1)} KB  ${(samples.length / RATE).toFixed(2)}s`);
}
