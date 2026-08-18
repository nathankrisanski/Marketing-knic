# MediaKit

A native Android app that does two jobs:

1. **Pull an HLS (`.m3u8`) stream down as a playable MP4** — starting from either
   the page the video plays on, or a direct playlist link.
2. **Transcribe any video or audio file to markdown**, with timestamps.

Both run as background jobs with progress notifications, so a long download or a
90-minute transcription survives you leaving the app.

---

## Building

Open `android/mediakit` in Android Studio (Ladybug or newer) and hit Run.

From the command line you need an Android SDK with platform 35 installed, plus
`local.properties` pointing at it:

```bash
cd android/mediakit
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle wrapper          # once, to generate ./gradlew and the wrapper jar
./gradlew assembleDebug
./gradlew test          # JVM unit tests for the playlist parser and markdown renderer
```

- minSdk 26, targetSdk/compileSdk 35, Kotlin 2.0, Jetpack Compose (Material 3).
- No native libraries and no bundled FFmpeg — see *How the MP4 gets made* below.

---

## Using it

### Fetch a video

The **Fetch** tab takes either kind of input:

| You paste | What happens |
| --- | --- |
| `https://site.com/watch/123` | The page is fetched and scraped for playlist URLs — inline scripts, JSON blobs, `<video>`/`<source>` tags, and one level of `<iframe>`. |
| `https://cdn.site.com/master.m3u8` | Used directly. |
| `https://cdn.site.com/file.mp4` | Downloaded as a progressive file. |

If nothing is found in the HTML — which is what happens when the player builds
its URL in JavaScript — the app falls back to loading the page in an off-screen
WebView and recording every playlist request the page's own code makes. That
catches signed and token-gated URLs that simply do not exist in the served HTML.

Candidates are then fetched and verified, so what the list shows is what is
actually a playlist. Pick one, pick a quality rendition if it is a master
playlist, and download. Flip **Transcribe after download** on to chain both
steps into one job.

You can also share a URL into MediaKit straight from your browser's share sheet.

### Transcribe a file

The **Transcribe** tab takes any video or audio file the device can decode — one
you picked, one shared in from another app, or one MediaKit just downloaded. The
transcript is written to `Documents/MediaKit/<name>.md`.

Output looks like this (the default `Timestamps` style):

```markdown
---
title: "Quarterly update"
source: "https://example.com/watch/123"
duration: "42:17"
language: "en"
transcribed_with: "Whisper API (whisper-1)"
transcribed_at: "2026-08-18 09:14"
word_count: 6218
---

# Quarterly update

**[00:00]**

Welcome to the quarterly update. Before we start on numbers, I want to…

**[00:31]**

Revenue grew twelve percent quarter over quarter, and the bulk of that…
```

`Prose` (no timestamps) and `Bullets` (one line per segment) are the other two
styles, in Settings.

---

## Where files land

| Artefact | Location |
| --- | --- |
| Video | `Movies/MediaKit/<name>.mp4` — shows up in your gallery |
| Transcript | `Documents/MediaKit/<name>.md` |

On Android 10+ these go through MediaStore, so the app needs **no storage
permission at all**.

---

## Transcription engines

Set this in **Settings**.

**Whisper-compatible server** (default). Any OpenAI-shaped
`/v1/audio/transcriptions` endpoint:

- OpenAI — base URL `https://api.openai.com/v1`, model `whisper-1`, plus a key.
- Groq — base URL `https://api.groq.com/openai/v1`, model `whisper-large-v3`.
- **Your own machine** — run [`whisper.cpp`](https://github.com/ggerganov/whisper.cpp)'s
  `whisper-server` or [`faster-whisper-server`](https://github.com/fedirz/faster-whisper-server)
  on your LAN, point the base URL at `http://192.168.x.x:8080/v1`, and leave the
  API key blank. Nothing leaves your network, and it is free.

This path gives the best accuracy and real per-phrase timestamps.

**Android on-device speech.** Fully offline, no key, nothing sent anywhere. It
needs Android 13+ and a device with an on-device speech model installed, accuracy
is noticeably below Whisper's, and it reports no timings — so segment timestamps
are interpolated across each chunk rather than measured. Android also demands the
microphone permission before it will start *any* recogniser, even one reading
from a file; the app asks for it when you select this mode.

Long files are split into chunks (10 minutes by default, adjustable) so memory
stays flat and uploads stay under server limits. Chunk timings are shifted back
onto the source timeline, so timestamps are absolute across the whole file.

---

## How the MP4 gets made

No FFmpeg. The pipeline is:

1. **Parse** the playlist (`hls/M3u8.kt`) — master and media playlists,
   `EXT-X-STREAM-INF` variants, `EXT-X-KEY`, `EXT-X-MAP`, `EXT-X-BYTERANGE`.
2. **Download** every segment (`hls/HlsDownloader.kt`), 4 at a time by default,
   with retries, decrypting AES-128 as it goes (explicit `IV=` or the media
   sequence number, per the HLS spec). Segments are appended strictly in playlist
   order into one contiguous MPEG-TS or fMP4 stream.
3. **Remux** that stream into MP4 (`hls/Remuxer.kt`) using the platform's own
   `MediaExtractor` and `MediaMuxer`. This is a container-level copy, not a
   transcode: H.264/H.265 and AAC samples are written through untouched, so it is
   fast and lossless.

Audio for transcription is extracted the same way — `MediaExtractor` +
`MediaCodec` decode, downmix to mono, linear-resample to 16 kHz, write WAV
(`transcribe/AudioExtractor.kt`).

---

## Limits worth knowing

- **DRM is not supported and will not be.** Widevine, FairPlay, and
  `SAMPLE-AES` streams fail with a clear message. Only clear and AES-128 HLS
  works.
- **Remuxing depends on the device's muxer.** Anything Android can demux and mux
  is fine (the common H.264 + AAC case always is). If the muxer refuses a stream,
  the app saves the raw `.ts` instead of throwing the download away — toggleable
  in Settings.
- **Live streams** capture whatever is in the current playlist window, not a
  continuous recording.
- **Login-gated video**: paste a `Cookie` header in Settings. The app already
  sends a browser `User-Agent`, `Referer`, and `Origin`, which is enough for most
  CDNs that reject non-browser clients.
- **The on-device engine is the weaker of the two.** Use a Whisper server when
  accuracy matters.

Download what you have the right to download. A stream being technically
reachable is not permission to keep a copy of it.

---

## Layout

```
android/mediakit/app/src/main/java/com/knicventures/mediakit/
├── MainActivity.kt              share-sheet intake, permissions, Compose host
├── hls/
│   ├── M3u8.kt                  playlist model + parser
│   ├── StreamResolver.kt        page URL → playlist candidates (static scrape)
│   ├── WebViewSniffer.kt        page URL → playlist candidates (network capture)
│   ├── HlsDownloader.kt         segment fetch, AES-128, ordered concatenation
│   └── Remuxer.kt               MPEG-TS/fMP4 → MP4
├── transcribe/
│   ├── AudioExtractor.kt        decode → mono → 16 kHz → chunked WAV
│   ├── TranscriptionEngine.kt   backend interface
│   ├── WhisperApiEngine.kt      OpenAI-compatible backend
│   ├── OnDeviceSpeechEngine.kt  offline Android backend
│   ├── Transcriber.kt           chunk orchestration + timeline stitching
│   └── TranscriptDocument.kt    markdown rendering
├── work/                        WorkManager jobs (foreground, chainable)
├── data/Settings.kt             preferences
├── util/                        HTTP, MediaStore output, notifications
└── ui/                          Compose screens
```
