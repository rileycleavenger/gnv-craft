#!/usr/bin/env bash
# Generates placeholder HeySir voice lines with macOS text-to-speech.
#
#   tools/gen_voices.sh
#
# Minecraft needs mono OGG Vorbis for sounds to fade with distance. To use real recordings
# instead, convert them with:
#   ffmpeg -i recording.m4a -ac 1 -ar 44100 -c:a libvorbis -q:a 5 src/main/resources/assets/heysir/sounds/hey_sir_1.ogg
# keeping the file names below (or update sounds.json).
set -euo pipefail

OUT="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/assets/heysir/sounds"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

# Trim leading/trailing silence and even out loudness.
FILTER="silenceremove=start_periods=1:start_threshold=-50dB,areverse,silenceremove=start_periods=1:start_threshold=-50dB,areverse,loudnorm=I=-14:TP=-1.5"

line() {
	local name="$1" voice="$2" rate="$3" text="$4"
	say -v "$voice" -r "$rate" -o "$TMP/$name.aiff" "$text"
	ffmpeg -loglevel error -y -i "$TMP/$name.aiff" -af "$FILTER" -ac 1 -ar 44100 -c:a libvorbis -q:a 5 "$OUT/$name.ogg"
	echo "wrote $name.ogg"
}

line hey_sir_1 "Reed (English (US))" 180 "Hey sir!"
line hey_sir_2 "Rocko (English (US))" 170 "Hey sir."
line hey_sir_3 "Eddy (English (US))" 185 "Hey sir, hey sir!"
line hey_sir_4 "Ralph" 175 "Hey, sir!"
line hey_sir_excuse_me_1 "Reed (English (US))" 175 "Hey sir, excuse me!"
line hey_sir_excuse_me_2 "Rocko (English (US))" 170 "Hey sir. Excuse me."
line wife_and_kids "Reed (English (US))" 165 "I have a wife and kids."
