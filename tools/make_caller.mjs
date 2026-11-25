// Makes a recorded caller for fake calls: Gemini speaks your line in its own voice; the WAV goes onto the phone,
// and the debug build's fake line plays it to Kivi after the greeting (src/debug/.../FakeCarrier.kt).
//   cd tools && GEMINI_KEY=... node make_caller.mjs telugu.wav "Hello andi, nenu Suresh. Arjun unnara?"
//   adb push telugu.wav /data/local/tmp/ && adb shell run-as dev.callai cp /data/local/tmp/telugu.wav files/
//   adb shell am broadcast -n dev.callai/.debug.FakeCall --es number 40001 --es audio telugu.wav
import { writeFileSync } from "node:fs";
import WebSocket from "ws";
const [out, line, voice = "en-in-csagent-1"] = process.argv.slice(2);
if (!out || !line) { console.error('usage: node make_caller.mjs out.wav "what the caller says" [voice]'); process.exit(1); }
const ws = new WebSocket("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + process.env.GEMINI_KEY);
const chunks = [];
ws.on("open", () => ws.send(JSON.stringify({ setup: { model: "models/gemini-3.8-live-extended-thinking",
  generationConfig: { responseModalities: ["AUDIO"], thinkingConfig: { thinkingLevel: "low" }, speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: voice } } } },
  systemInstruction: { parts: [{ text: "You are an actor recording a phone caller's line. Say exactly the words you are given, in the language they are written in, naturally, and nothing else." }] } } })));
ws.on("message", (d) => {
  const m = JSON.parse(d.toString());
  if (m.setupComplete) ws.send(JSON.stringify({ clientContent: { turns: [{ role: "user", parts: [{ text: line }] }], turnComplete: true } }));
  for (const p of m.serverContent?.modelTurn?.parts ?? []) if (p.inlineData) chunks.push(Buffer.from(p.inlineData.data, "base64"));
  if (m.serverContent?.turnComplete) {
    const pcm = Buffer.concat(chunks), rate = 24000, h = Buffer.alloc(44);
    h.write("RIFF", 0); h.writeUInt32LE(36 + pcm.length, 4); h.write("WAVE", 8); h.write("fmt ", 12); h.writeUInt32LE(16, 16);
    h.writeUInt16LE(1, 20); h.writeUInt16LE(1, 22); h.writeUInt32LE(rate, 24); h.writeUInt32LE(rate * 2, 28); h.writeUInt16LE(2, 32);
    h.writeUInt16LE(16, 34); h.write("data", 36); h.writeUInt32LE(pcm.length, 40);
    writeFileSync(out, Buffer.concat([h, pcm]));
    console.log(`wrote ${out}: ${(pcm.length / 2 / rate).toFixed(1)} s`);
    ws.close();
  }
});
ws.on("close", (c, r) => { if (!chunks.length) { console.error(`closed ${c} ${r}`); process.exit(1); } });
