// Is the line to Gemini healthy from here? Times session setup 5 times, then holds one session for 50 s,
// streaming silence like a call and pinging every 15 s, and prints each pong's delay.
//   cd tools && GEMINI_KEY=... node linecheck.mjs
import WebSocket from "ws";
const URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + process.env.GEMINI_KEY;
const setup = { setup: { model: "models/gemini-3.8-live-extended-thinking",
  generationConfig: { responseModalities: ["AUDIO"], thinkingConfig: { thinkingLevel: "medium" }, speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: "en-in-csagent-5" } } } },
  systemInstruction: { parts: [{ text: "You are Kivi, answering a phone call in India in friendly Indian English." }] } } };

function timeSetup() {
  return new Promise((resolve) => {
    const t0 = Date.now(); const ws = new WebSocket(URL);
    const timer = setTimeout(() => { ws.terminate(); resolve("timeout"); }, 20000);
    ws.on("open", () => ws.send(JSON.stringify(setup)));
    ws.on("message", (d) => { if (JSON.parse(d.toString()).setupComplete) { clearTimeout(timer); ws.close(); resolve(`${Date.now() - t0} ms`); } });
    ws.on("close", (c, r) => { clearTimeout(timer); resolve(`closed ${c} ${r}`); });
  });
}
for (let i = 0; i < 5; i++) console.log("setup", await timeSetup());

const ws = new WebSocket(URL); const t0 = Date.now(); let pingAt = 0, timer;
const at = () => ((Date.now() - t0) / 1000).toFixed(1) + "s";
ws.on("open", () => ws.send(JSON.stringify(setup)));
ws.on("message", (d) => {
  const m = JSON.parse(d.toString());
  if (m.setupComplete) {
    const silence = Buffer.alloc(3200).toString("base64"); // 100 ms of 16 kHz PCM
    timer = setInterval(() => ws.send(JSON.stringify({ realtimeInput: { audio: { data: silence, mimeType: "audio/pcm;rate=16000" } } })), 100);
    setInterval(() => { pingAt = Date.now(); ws.ping(); }, 15000);
  }
  if (m.goAway) console.log(at(), "goAway", JSON.stringify(m.goAway));
});
ws.on("pong", () => console.log(at(), "pong after", Date.now() - pingAt, "ms"));
ws.on("close", (c, r) => { console.log(at(), "closed", c, r.toString()); process.exit(0); });
setTimeout(() => { clearInterval(timer); ws.close(); }, 50000);
