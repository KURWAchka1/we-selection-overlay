# Native Remote Selection Gestures

Date: **2026-10-13**. Baseline **e5aa9aa**. This cycle adds test-only native
gesture/evidence tooling, not a new editing implementation or a public release.
Companion **0.1.0-dev**, client **1.8.0-bridge-dev**, protocol **1.2**.
Necessary client fixes remain mandatory; preserving published 1.8.0 downloads
does not freeze development source.

Tuple: Paper **1.21.11-132 / c5eb079**, official server-side WorldEdit **7.4.2**,
Fabric **0.18.4 / API 0.141.3+1.21.11**, NeoForge **21.11.42**, Temurin
**21.0.11+10**. Neither gameplay client has local WorldEdit installed.

## Test Boundary

Input enters the actual Minecraft mouse/keyboard callbacks inside each game JVM,
through the production mixins and controller. Test-only reflection uses exact
pinned mappings; it is not production networking or a simulated editor. Camera
aim is configured on the client, and pose/creative flight on the owned server.
All six faces and eight corners are taken from the runtime's actual domain enums.
The one initial RESIZE mode is fixture configuration; MOVE is selected through
the real held-G radial menu, followed by a native short G tap.

Both clients run on a guarded private Win32 desktop with the cursor isolation
agent. The preflight verifies both actual GLFW versions. No window activation,
desktop switch, cursor warp or injected operating-system input is used. Logical
focus changes are in-JVM test events, not activation of a hidden window.

## Cases

Each loader executes **28** ordered cases against a fresh Paper process:

| Cases | Independent observations |
| --- | --- |
| Six face handles with G | Real screen-space projection/hit, draft movement, unchanged camera after frame processing, one confirmation commit |
| Eight corner handles with G | Correct selected corner, three-axis draft, local mouse release keeps draft without applying; G confirms |
| Eight free corners without G | Camera remains uncaptured, view-ray-following renderer changes draft, small displacement, native LMB release commits |
| Escape during G drag / free drag | Actual Escape callback cancels; no pause screen or mutation; final button release clears ownership |
| Focus loss / chat during drag | Unsent draft cancels; grab/crosshair state matches the context; no mutation |
| Server resets selector during drag | Real CUI invalidates the old snapshot; fresh state cancels stale draft without a commit |
| Radial MOVE, X/Y/Z and wheel | Real mode selection and axis keys; exact +2 X / +1 Y / -2 Z shift; camera turn does not move the draft; LMB release does not commit; G confirms |

The first face is confirmed while LMB is still held, covering release ordering.
Direct corner checks exercise the native Shift callback and verify that descent
is not suppressed; this is not a sustained flight-physics benchmark. Subsequent
camera changes are deliberate fixture aim changes, not hardware mouse sampling.

Twelve quiet ticks during every resize drag do not advance the bridge request ID.
Post-acknowledgment quiet ticks also do not trigger mutation retries/recapture.
Restoration checks include camera capture, crosshair predicate, native grab flag,
attack state, screen state, blocked-button ownership and axis-key ownership.

## Authority And Evidence

After every result the console-only fixture independently reads actual WorldEdit
minimum/maximum points. The Node analyzer compares those with the client draft,
checks the complete unique handle set, ordered case IDs and live server counters.
Before confirmation and after G-mode LMB release, server bounds remain original
and host commit/APPLIED counters remain unchanged. Client log success alone is
insufficient for acceptance.

Per loader: **23 host commits / 23 APPLIED**, **five cancelled drafts**, **29
captures**. Twenty-eight are explicit case captures; the stale-selector case
adds exactly one new capture. No gesture polling or additional capture is allowed.
Disconnect leaves **zero peers / pending frames / dispatcher tasks**.

The fixture compares a hash of **3,315 already-loaded blocks** before/after and
checks fresh WorldEdit undo history remains empty. Pose setup is test-only and
may use normal server teleport/chunk loading; this is not a zero-generation
measurement or a whole-world hash. Production editing remains metadata-only.

The first complete matrix passed both loaders (**56 cases**) and is retained
under `build/bridge-native-gesture-proof/pre-render-audit-20261013/`, checked
**2026-10-13T18:24:39Z**. The strengthened frame-camera/screenshot matrix also
passes both loaders (**56 cases**), checked **2026-10-13T18:29:42Z**. Four fresh
native launches therefore executed **112 gesture cases**, not 112 different cases.
No production defect was reproduced and no production correction was required.

All four actual **1280 x 720** native F2 screenshots were opened and inspected:
corner axes, draft dimensions, the XYZ compass and highlighted Z are present;
the crosshair is absent and mod labels remain within the viewport without
overlapping one another. A vanilla tutorial toast appears in two captures; it is
not part of the mod. These are an empty selection in a disposable flat world,
not promotional screenshots or proof of complete terrain/render-performance QA.
The supervisor requires fresh PNGs with matching dimensions and stores hashes.

Pure regression remains **91 tests on each JVM**, Java 21 and 25. Node harness
checks are now **63**, including eight new negative/complete-matrix checks for
client-only success, unconfirmed/duplicate apply, recapture, retained tasks,
invalid coordinates and missing/invented handles. Existing JFR readers and
adversarial admission probes are repeated on both JVMs. Companion contents and
Java21 bytecode guards pass; published/downloaded client artifacts are unchanged.

## Reproduction

From the repository root, with cached pinned dependencies, development universal
clients already built and the owner's Minecraft EULA acceptance:

```powershell
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -ModVersion 1.8.0-bridge-dev -EditGestures -AcceptEula
.\tools\test-server-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -SkipNativeCodecs -Offline
```

Evidence root: `build/bridge-native-gesture-proof/`. `native-gesture-proof.json`
is published locally only after both clients, independent checks and normal
owned-server stops succeed. A failure writes diagnostics instead. Logs, generated
worlds and native F2 screenshots remain ignored by Git; no test classes ship.

Companion and universal client hashes remain those of the
[remote-editing candidate](BRIDGE_REMOTE_SELECTION_2026-10-13.md#reproduction-and-evidence).
The updated separate Paper fixture hash is
`0cf29fe36f90c3bac1d9568b0f8df2b9b87ef2929bbcfd3f29945578ac753029`.
See [pinned input/source methodology](SOURCES.md#s54-native-gesture-callbacks-and-authority-checks).

## Remaining Gates

This closes the named bounded native callback scenarios, not manual hardware
ergonomics, all close-range camera angles/resolutions/modpacks, sustained flying
physics, client frame-time acceptance, representative server cost, ungraceful
backend loss or production proxy/protection combinations. 26.x remote consumers,
other dedicated hosts, remote clipboard and command simulation remain separate
development stages. Existing fault/load evidence is not expanded by implication.
