// How long Kivi pauses before speaking at each thinking level: time from the greeting cue, and from a caller's turn,
// to Gemini's first audio. Three runs per level; prints the median.   GEMINI_KEY=... node thinkspeed.mjs
import WebSocket from "ws";
const URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + process.env.GEMINI_KEY;
const CALLER = "Hello, Ravi from Blue Dart here. Is Arjun there? I have a parcel and the security won't take it.";
function once(level) {
  return new Promise((resolve) => {
    const ws = new WebSocket(URL); let t = 0, greet = 0, reply = 0, stage = 0;
    const send = (text) => { t = Date.now(); ws.send(JSON.stringify({ clientContent: { turns: [{ role: "user", parts: [{ text }] }], turnComplete: true } })); };
    const timer = setTimeout(() => { ws.terminate(); resolve({ greet, reply: reply || null }); }, 45000);
    ws.on("open", () => ws.send(JSON.stringify({ setup: { model: "models/gemini-3.8-live-extended-thinking",
      generationConfig: { responseModalities: ["AUDIO"], thinkingConfig: { thinkingLevel: level }, speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: "en-in-csagent-5" } } } },
      systemInstruction: { parts: [{ text: "You are Kivi, Arjun's AI assistant, answering Arjun's phone in friendly Indian English. Take a message; one or two short sentences per turn." }] } } })));
    ws.on("message", (d) => {
      const m = JSON.parse(d.toString());
      if (m.setupComplete) send("(The call just connected. Answer warmly in Indian English, in one short breath.)");
      const audio = (m.serverContent?.modelTurn?.parts ?? []).some((p) => p.inlineData);
      if (audio && stage === 0 && !greet) greet = Date.now() - t;
      if (audio && stage === 1 && !reply) reply = Date.now() - t;
      if (m.serverContent?.turnComplete) {
        if (stage === 0) { stage = 1; send(CALLER); } else { clearTimeout(timer); ws.close(); resolve({ greet, reply }); }
      }
    });
    ws.on("close", () => { clearTimeout(timer); resolve({ greet, reply }); });
  });
}
const median = (xs) => { const s = xs.filter(Boolean).sort((a, b) => a - b); return s.length ? s[Math.floor(s.length / 2)] : null; };
for (const level of ["low", "medium", "high"]) {
  const runs = [];
  for (let i = 0; i < 3; i++) runs.push(await once(level));
  console.log(`${level.padEnd(6)} greeting ${median(runs.map((r) => r.greet))} ms, reply ${median(runs.map((r) => r.reply))} ms   (runs: ${runs.map((r) => `${r.greet}/${r.reply}`).join(", ")})`);
}
