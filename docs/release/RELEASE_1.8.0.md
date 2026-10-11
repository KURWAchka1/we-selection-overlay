# 1.8.0 Publication Packet

Status: **binary publication in progress**. Descriptions and two schematic gallery
images were published on CurseForge and Modrinth on 2026-10-11. They currently
label 1.8.0 as an upcoming preview; the player JARs have not yet been uploaded.
The owner has now authorized opening the source repository and retaining GPL.

## Binary Verification

Both packager test suites were rerun successfully on 2026-10-11 with Java 21,
two Gradle workers, offline dependencies and no Minecraft windows. The resulting
reproducible JARs match the archived artifacts used by the 2026-10-10 native
six-runtime command/editing/clipboard verification described in TESTING.md:

| Loader | Bytes | SHA-256 |
| --- | ---: | --- |
| Fabric | 1,579,903 | `912a4c0c455928ec05a24fe2001f009dd68322d8aa437c2c9e82d014e5db46d0` |
| NeoForge | 1,551,799 | `ea6a8e2e3d58cc1ffcb1473ddddc9036d850ae5f773ef108dab1e570eb379b1c` |

Use the files in each universal packager's `libs/1.8.0/`. Publish one version per
loader, not one primary download with a different-loader secondary JAR.
Fabric: Minecraft 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2; Fabric API required,
WorldEdit optional/recommended. NeoForge: the same launch targets; explicitly
state that local WorldEdit integration on 26.1 requires 26.1.2 or newer.
Java 21 for 1.21.11, Java 25 for 26.x. Do not tag the whole 1.21 branch.

## License and Source Publication

The current root/source notices and both binary metadata files say
`GPL-3.0-or-later`; both public pages still say All Rights Reserved. Neither
inconsistent label should be silently substituted for the other.

The owner initially wanted private sources, then explicitly authorized opening
them after the GPL distribution issue was explained. Keep GPL-3.0-or-later;
do not relabel the binaries All Rights Reserved. Public platform license labels
must be updated to match. Preserve the complete corresponding source with the
`1.8.0` tag and link [SOURCE_ACCESS.md](../../SOURCE_ACCESS.md) from the release.

Primary sources checked on 2026-10-11:

- [WorldEdit's actual license](https://github.com/EngineHub/WorldEdit/blob/version/7.4.x/LICENSE.txt).
- [GNU's plugin explanation](https://www.gnu.org/licenses/gpl-faq.en.html#GPLPlugins):
  whether components form a combined work depends on their interaction; this is
  the licensor's interpretation, not a jurisdiction-specific legal opinion.
- [WorldEditCUI-Protocol](https://github.com/EngineHub/WorldEditCUI-Protocol/blob/main/README.md)
  uses EPL-2.0. Our Fabric metadata is narrowed and NeoForge codecs are relocated.
- [EPL-2.0 FAQ](https://www.eclipse.org/legal/epl-2.0/faq/) and the retained upstream
  license, section 3.1, require an appropriate source-access notice for the covered
  Program. An upstream link alone needs review against the exact redistributed
  modifications.

Before changing repository visibility, review all Git history and confirm the
public-exposure action with the owner. Gitleaks 8.30.1 scanned 34 commits on
2026-10-11 with no findings. No tracked worlds, logs, accounts, private keys or
video/audio were found. Keep private build/security reports out of Git.

## Presentation

Use a clear English summary:

> See WorldEdit selections, inspect materials, and preview supported edits before you press Enter.

The owner's latest direction replaces the old inline screenshot collection:
embed the new YouTube review first, then use concise sections and no more than
two optional schematic feature banners. The existing project icon stays intact.
See `PAGE_STYLE_1.8.0.md` for the research, visual rules and mobile checks.
Two original schematic illustrations were uploaded, explicitly labeled as such;
the command illustration is also labeled an upcoming 1.8.0 preview. Do not
promise instant calculation, zero FPS loss, all commands,
every server integration, or unreleased Minecraft compatibility.

Keep the existing public project names, slugs, icon, contact and social links.
Do not change rewards, privacy, comments or repository visibility as part of
editing the presentation. The current full-review URL is
https://www.youtube.com/watch?v=nZlb22H432E; it describes a 1.8.0 preview build.

Gallery location credit: Celestia Town by Fireless007 / leolerenard,
https://www.curseforge.com/minecraft/worlds/celestia-town. Its page permits use on
websites and in videos with a download-page credit; this is not a map
redistribution license. Do not ship the map or shader pack.

Useful official presentation/submission references:
[CurseForge page guide](https://blog.curseforge.com/how-to-create-the-best-project-page/),
[CurseForge submission workflow](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project),
[Modrinth gallery fields](https://docs.modrinth.com/api/operations/getproject/).
These are operational references, not an assurance of moderation approval.
