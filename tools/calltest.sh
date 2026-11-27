#!/usr/bin/env bash
# Kivi's phone test suite over the debug build's fake line (src/debug/.../FakeCarrier.kt). Needs adb, python and a
# debug build installed. Rings the phone with fake numbers 4000x; never touches a real call (checks before each test).
#   bash tools/calltest.sh            # everything
#   bash tools/calltest.sh answer urgent outage    # chosen scenarios
# Afterwards it deletes the test notes and unregisters the fake line, so the dialer never offers "Fake".
set -u
export MSYS_NO_PATHCONV=1 PYTHONIOENCODING=utf-8 # Telugu transcripts on a Windows console
# pwd -W: Git Bash gives a C:/ path, which Windows Python can open (with MSYS_NO_PATHCONV, /c/... reaches it unconverted)
OUT="${KIVI_TEST_OUT:-$(cd "$(dirname "$0")" && { pwd -W 2>/dev/null || pwd; })/.out}"; mkdir -p "$OUT"
LINE=dev.callai/dev.callai.debug.FakeCarrier
fake()    { adb shell am broadcast -n dev.callai/.debug.FakeCall "$@" >/dev/null 2>&1; }
ring()    { fake --es number "$1" "${@:2}"; }
hangup()  { fake --es do hangup; }
calls()   { adb shell dumpsys telecom 2>/dev/null | sed -n '/^  mCalls:/,/^  [A-Za-z]/p' | grep -c "Call id="; }
callstate() { adb shell "dumpsys telephony.registry | grep -m1 mCallState" 2>/dev/null | tr -d ' \r'; }
quiet()   { for _ in $(seq 1 "${1:-60}"); do [ "$(calls)" = "0" ] && return 0; sleep 1; done; fake --es do hangup-all; sleep 2; }
safe()    { [ "$(callstate)" = "mCallState=0" ] && [ "$(calls)" = "0" ]; }
net()     { adb shell cmd connectivity set-chain3-enabled true >/dev/null; adb shell cmd connectivity set-package-networking-enabled "$1" dev.callai >/dev/null; }
q()       { for f in kivi.db kivi.db-wal; do adb exec-out run-as dev.callai cat "databases/$f" > "$OUT/t_$f" 2>/dev/null; done
            python -c "import sqlite3,sys; c=sqlite3.connect(sys.argv[1]); exec(sys.argv[2])" "$OUT/t_kivi.db" "$1"; }
mark()    { MARK=$(q "print(c.execute('select coalesce(max(id),0) from events').fetchone()[0])"); }
events()  { q "print('\n'.join(t for (t,) in c.execute('select text from events where id > $MARK order by id')))"; }
expect()  { # expect <name> <regex>: pass when a log line since the mark matches
            local got; got=$(events)
            if echo "$got" | grep -Eq "$2"; then echo "PASS $1"; PASSED=$((PASSED+1)); else echo "FAIL $1 (wanted /$2/)"; echo "$got" | sed 's/^/    /'; FAILED=$((FAILED+1)); fi; }
# A connected Bluetooth headset may answer test calls by itself (seen: 3.7 s in, before Kivi), which fails every test.
headset() { adb shell dumpsys bluetooth_manager 2>/dev/null | sed -n '/^Profile: HeadsetService/,/^Profile:/p' | grep -m1 "mActiveDevice:" | grep -vq null; }
begin()   { quiet 30; safe || { echo "STOP: the phone is on a real call"; exit 2; }; mark; }

answer()   { begin; ring 40001; sleep 12; hangup; quiet; expect answer "Answering .*0001"; }
hidden()   { begin; fake; sleep 12; hangup; quiet; expect hidden "Answering hidden number"; }
pileup()   { begin; for n in 1 2 3 4 5 6 7; do ring 40003$n; sleep 2; hangup; sleep 2; done; quiet 20; mark; ring 400039; sleep 12; hangup; quiet; expect pileup "Answering .*0039"; }
urgent()   { begin; for _ in 1 2 3; do ring 400004; sleep 2; hangup; sleep 3; done; quiet; expect urgent "Called 3 times in a row"; }
offline()  { begin; net false; ring 400005; sleep 24; hangup; net true; quiet; expect offline "rang through because there's no internet"; }
comeback() { begin; net false; ring 400015; sleep 8; net true; sleep 10; hangup; quiet; expect comeback "Answering .*0015"; }
outage()   { begin; ring 400006; sleep 10; net false; sleep 6; net true; sleep 12; hangup; quiet; expect outage "Back on the line"; }
longdrop() { begin; ring 400026; sleep 10; net false; sleep 40; net true; quiet 30; expect longdrop "The line to Gemini dropped"; }
early()    { begin; ring 400007; sleep 2; hangup; quiet; expect early "hung up after .* before Kivi answered"; }
bridge()   { begin; adb shell kill "$(adb shell "ps -A -o PID,NAME | grep dev.callai:bridge" | awk '{print $1}' | tr -d '\r')"; sleep 6; ring 400008; sleep 12; hangup; quiet; expect bridge "Answering .*0008"; }
pickup()   { begin; ring 400012; sleep 2; adb shell input keyevent KEYCODE_CALL; sleep 6; fake --es do hangup-all; quiet; expect pickup "You picked up"; }
waiting()  { begin; ring 400010; sleep 9; ring 400011; sleep 4; fake --es do hangup-all; quiet; expect waiting "called while Kivi was on another call"; }
# Do Not Disturb with every mode off: Kivi answers (busy by day, asleep at night). Restores DND and Strangers mode.
dnd()      { begin; fake --es do strangers --ez on false; adb shell cmd notification set_dnd priority >/dev/null; ring 400060; sleep 12; hangup
             adb shell cmd notification set_dnd off >/dev/null; fake --es do strangers --ez on true; quiet; expect dnd "Answering .*0060 \((busy|sleep)"; }
decline()  { begin; fake --es do block --es number 400050; ring 400050; sleep 5; quiet 15; fake --es do block; expect decline "Declined .*0050"; }
voice()    { # needs tools/caller.wav from make_caller.mjs on the phone (see make_caller.mjs)
             [ -f "$(dirname "$0")/caller.wav" ] || { echo "SKIP voice (no tools/caller.wav)"; return; }
             adb push "$(dirname "$0")/caller.wav" /data/local/tmp/caller.wav >/dev/null; adb shell run-as dev.callai cp /data/local/tmp/caller.wav files/caller.wav
             begin; ring 400030 --es audio caller.wav; sleep 30; hangup; quiet
             for _ in $(seq 1 20); do # the note is written a few seconds after the call, once the note model replies
               got=$(q "r=c.execute(\"select transcript from calls where number='400030' order by time desc\").fetchone(); print(r[0] if r else '')"); [ -n "$got" ] && break; sleep 2; done
             echo "$got" | grep -q "Caller:" \
               && { echo "PASS voice"; PASSED=$((PASSED+1)); } || { echo "FAIL voice (no caller words in the transcript)"; FAILED=$((FAILED+1)); }; }

headset && { echo "STOP: a Bluetooth headset is connected and may answer the test calls itself. Disconnect it and run again."; exit 2; }
ALL="answer hidden pileup urgent offline comeback outage longdrop early bridge pickup waiting decline dnd voice"
PASSED=0; FAILED=0
# Test numbers are strangers: Strangers mode on for the run, then back to how it was.
WAS=$(adb shell run-as dev.callai cat shared_prefs/kivi.xml | grep -o 'name="strangers" value="[a-z]*"' | grep -o 'true\|false'); WAS=${WAS:-false}
DND=$(adb shell settings get global zen_mode | tr -d '') # 0 = Do Not Disturb off
trap 'fake --es do strangers --ez on $WAS; net true; adb shell cmd connectivity set-chain3-enabled false >/dev/null; fake --es do hangup-all; fake --es do clean --el since "$START"; adb shell telecom unregister-phone-account $LINE fake 0 >/dev/null' EXIT
START=$(($(date +%s) * 1000))
adb shell telecom register-phone-account $LINE fake 0 Fake >/dev/null && adb shell telecom set-phone-account-enabled $LINE fake 0 >/dev/null
[ "$DND" = "0" ] || ALL=${ALL/ dnd/} # your Do Not Disturb is on: leave it alone
fake --es do strangers --ez on true
for t in ${@:-$ALL}; do $t; done
echo "$PASSED passed, $FAILED failed"
exit "$FAILED"
