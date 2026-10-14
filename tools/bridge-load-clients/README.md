# Test-Only 26.2 Load Clients

This separate executable supplies stationary offline actors to the existing
Paper bridge load supervisor. It is **not a mod/plugin**, not a renderer, and
not bundled into player downloads. It does not use Minecraft client classes,
OpenGL, OS input, accounts, credentials or a user Minecraft profile.

The implementation delegates login/configuration/compression/keepalive and
packet codecs to [GeyserMC MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib).
The official repository's timestamped **26.2-20261012.101744-21** artifact is
pinned; the launcher and executable independently require the actual **776 /
26.2** codec. The runtime protocol JAR SHA256 is
`5757ef29742d75b4af90fcdaf3d997e4b6dcf23928e601b4869487286854779a`.
Upstream is MIT-licensed; dependency code is not copied into this project.

Build from the repository root with JDK 25 selected:

```powershell
.\WEaddon_fabric_1.21.11\gradlew.bat -p tools/bridge-load-clients check installDist --no-daemon --max-workers=2
```

The first build needs network access to the official Maven repositories.
Subsequent test runs use `--offline`. Transitive dependencies have a committed
Gradle lockfile, with SHA256 verification of artifacts and metadata. Gradle
normalizes a timestamp snapshot to its base SNAPSHOT in lock state, which
conflicts with the direct timestamp requirement. Only that explicitly timestamp-
and checksum-pinned component is excluded from lock-state matching; its
transitive dependencies remain locked. No `latest`, protocol alias or unreviewed
fork is used. Recorded checksums are integrity pins obtained over HTTPS, not a
claim that artifact signatures were verified.

Use `tools/test-paper-bridge-load.ps1 -Minecraft 26.2 -EditLoad` to launch the
owned fixture, not the executable against an ordinary server. The executable
requires an explicit fixture flag and can connect **only to 127.0.0.1:25579**,
using at most 32 distinct `WesoLoad00..31` identities. Automatic server transfers
are disabled. The command surface permits only bounded bridge/CUI payloads and
their exact registration strings, not arbitrary chat or commands.

One helper JVM handles all actors: 256 MiB heap, two available processors, two
Netty event-loop threads and one shared bounded 4096-task packet callback queue.
The Node adapter bounds IPC commands, lines, pipe backlog and retained logging;
malformed events, wrong identities/channels, decoder errors and unexpected exits
fail the run. Proof cannot pass until every actor disconnects and the owned
helper/server exit normally. Eight independent Node tests exercise those failure
boundaries without network connections. `verifyGuards` additionally checks the
resolved Java codec and outgoing payload guards.

The installed test classpath is about 23 MB including upstream Netty platform
dependencies. It stays under ignored `build/` output; mod/companion size is
unchanged. This is headless wire-load evidence, **not** Fabric/NeoForge native
gameplay, client FPS, arbitrary proxy support or production cost acceptance.
