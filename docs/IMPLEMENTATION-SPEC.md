# AdocFoundry Gradle plugin: implementation specification

Author: Paul Snow
Version: 0.0.0
Tracking issue: [AdocFoundry#805](https://github.com/bovinemagnet/AdocFoundry/issues/805)

This is the source of truth for what the plugin's first release does and in what order it is built. Where the implementation has to depart from it, record the departure in §12 (Implementation notes) in the same pull request.

## 1. Purpose

A Gradle plugin that runs [`adocfoundry-cli`](https://github.com/bovinemagnet/AdocFoundry) in a Gradle build to:

- **validate** an Antora documentation tree (`check`, `audit`) and fail the build on problems;
- **render PDFs** of Antora component versions, single pages, or the PDF profiles a workspace's `.adocfoundry.toml` defines (`pdf`).

The CLI is one native binary with no Node.js, JVM or Ruby at run time. The plugin downloads it, verifies it, caches it and runs it.

## 2. Out of scope for the first release

- **The Antora HTML site.** The CLI does not build one. Projects keep the `org.antora` plugin for the site; the two plugins must work side by side.
- **Remote content sources.** The CLI reads local folders only and never clones a playbook's remote sources. Multi-repository playbooks need their sources checked out first.
- **Word, EPUB, Kindle and DocBook** export. These come after the first release, in the same pattern as PDF (§11).
- **Gradle's Problems API** reporting. Diagnostics are printed in the task's failure message and log.
- **Maven-hosted binaries.** The binary comes from GitHub Releases or a local path (§5).

## 3. Decisions already made

These were settled in AdocFoundry#805; do not reopen them without asking.

| Question | Decision |
|---|---|
| Where the plugin lives | This repository, not a folder in AdocFoundry |
| Plugin id | `io.github.bovinemagnet.adocfoundry` |
| Group | `io.github.bovinemagnet` |
| Licence | Apache-2.0 |
| Language | Java (no Kotlin runtime on the plugin's classpath) |
| How the binary is obtained | Downloaded from AdocFoundry's GitHub Releases and verified against the release's `SHA256SUMS`; a local `executable` overrides; a `baseUrl` points at a mirror |
| First release covers | `check`, `audit`, `pdf` |
| Gradle versions | The oldest Gradle release still maintained when the plugin ships, tested up to the current one. This repository's wrapper is 9.3.1. Confirm the lower bound against Gradle's support policy in WP 1 and record it in §12 |
| Plugin and CLI versions | Independent. Each plugin release pins a tested default `toolVersion`, which builds may override |
| PDF look | A PDF built by the plugin uses the same theme (style and layout), theme folder, fonts, attributes and profiles as AdocFoundry's own export of the same workspace, and is byte-for-byte the PDF `adocfoundry-cli` writes. The plugin never chooses a theme of its own (§4.4) |
| Workspace | `workspaceDir` is the folder opened in AdocFoundry, by default the project directory, so the same `.adocfoundry.toml` and playbook apply (§4.4) |

## 4. The CLI the plugin wraps

Everything here was checked against `adocfoundry-cli` 0.15.0. The CLI's own reference is AdocFoundry's `src/docs/modules/ROOT/pages/command-line.adoc`, published at <https://bovinemagnet.github.io/AdocFoundry>. Read it before WP 3.

### 4.1 Commands used

```text
adocfoundry-cli --version
adocfoundry-cli check <root> [--format text|json] [--fail-on warning|error]
adocfoundry-cli audit <root> [--component [<version>@]<name>] [--format text|json|sarif]
    [--output <file>] [--fail-on none|warning|error] [--require-complete]
adocfoundry-cli pdf <file|component-dir> [--root <dir>] [--component [<version>@]<name>]
    [--theme <name|file.yml>] [--fonts-dir <dir>] [--output <file.pdf>]
    [--standard pdf-a-1b|pdf-a-1a|pdf-a-2a|pdf-ua-1] [--fail-on none|warning|error]
adocfoundry-cli pdf --profile <name> [<root>] [--output <file.pdf>] [--fail-on none|warning|error]
```

### 4.2 Behaviour the plugin relies on

- **`--version`** prints one line: `adocfoundry-cli 0.15.0`.
- **The workspace root** is the folder opened in AdocFoundry (§4.4). The CLI reads the root's `.adocfoundry.toml` and never a user settings file, so results are the same on every machine.
- **Exit codes** (`check`, `audit`, `pdf`):

  | Code | Meaning |
  |---|---|
  | 0 | Passed |
  | 1 | `check`: a diagnostic met `--fail-on`. `audit`: a finding met the threshold. `pdf`: the export failed or was refused, or a diagnostic met `--fail-on` (the PDF is still written in that case) |
  | 2 | Usage or runtime error: unknown option, workspace not indexed in time or at all. For `audit`, also unavailable, invalid `[quality]` settings, or partial while completeness is required |

- **Severities.** An unresolved xref is a **warning**, so `check` passes it under the default `--fail-on error`. Information diagnostics never fail anything.
- **`check --format json`** prints a bare JSON array. Each entry has:
  - `path` (relative to the root, with `/`);
  - `range` (`start` and `end`, as byte offsets);
  - `line` and `column` (1-based);
  - `severity`;
  - `code`;
  - `message`.

  It is not versioned yet (AdocFoundry#807), so parse it leniently and ignore unknown fields.
- **`audit --format json`** is versioned (`schema_version: 1`). `--format sarif` writes SARIF 2.1.0. `--output` writes the report to a file; only the report goes to standard output, and problems go to standard error.
- **`pdf`**:
  - it prints `<output> (<n> pages)` on standard output, and warnings as `path:line: warning: message` on standard error;
  - it creates the output's parent folders;
  - without `--output`, a component's PDF goes to the current directory. The plugin therefore **always passes an absolute `--output`**.
- **Paths.** Relative paths are read against the current directory. The plugin passes absolute paths and sets the working directory to the project directory.
- **Timeout.** The CLI gives up after a fixed 300 seconds of indexing or exporting, and exits with 2 (AdocFoundry#809).
- **Diagram cache.** The CLI writes rendered diagrams to `<root>/.cache/adocfoundry/diagrams`, inside the documentation tree (AdocFoundry#810). Exclude it from task inputs (§7).
- **External tools.** Diagrams drawn by Graphviz, PlantUML or D2 use programs on `PATH`. Mermaid is drawn inside the CLI. These programs are optional, and they are inputs Gradle cannot see.

### 4.3 Release assets

Download URL: `<baseUrl>/v<version>/<asset>`. The default `baseUrl` is `https://github.com/bovinemagnet/AdocFoundry/releases/download`; GitHub answers with a redirect, which must be followed.

| Platform (`os.name` / `os.arch`) | Asset | Binary inside |
|---|---|---|
| Linux / `amd64`, `x86_64` | `adocfoundry-cli-<v>-linux-x86_64.tar.gz` | `adocfoundry-cli` |
| Linux / `aarch64`, `arm64` | `adocfoundry-cli-<v>-linux-aarch64.tar.gz` | `adocfoundry-cli` |
| macOS / any | `adocfoundry-cli-<v>-macos-universal.tar.gz` | `adocfoundry-cli` |
| Windows / `amd64`, `x86_64` | `AdocFoundry-<v>-windows-x64.zip` until AdocFoundry#806, then `adocfoundry-cli-<v>-windows-x64.zip` | `adocfoundry-cli.exe` |
| Windows / `aarch64`, `arm64` | `AdocFoundry-<v>-windows-arm64.zip` until AdocFoundry#806, then `adocfoundry-cli-<v>-windows-arm64.zip` | `adocfoundry-cli.exe` |

- Each archive keeps the binary at its top level, with `licenses/SCOWL.txt` and `licenses/THIRD-PARTY-NOTICES.txt` beside it.
- The Windows application zip also holds the app, `adoc-ls.exe`, `adoc-mcp.exe`, `LICENSE` and `README.md`. Extract only `adocfoundry-cli.exe` and `licenses/`.
- `SHA256SUMS` sits beside the assets, in `sha256sum` format: `<hex>  <asset>` per line.
- The Linux binaries are dynamically linked against glibc and need glibc 2.35 or later (Ubuntu 22.04+, Debian 12+). They do not run on RHEL 8 or 9, Amazon Linux 2 or Alpine (AdocFoundry#811).
- The 0.15.0 Linux archive is 39 MB, and the binary 102 MB unpacked.
- macOS assets may become Apple Silicon only (AdocFoundry#494). Keep the platform-to-asset mapping in one class, so a change touches one place.

### 4.4 The PDF look

The PDF's theme (its style and layout), fonts and metadata are AdocFoundry's to decide, never the plugin's. The app and the CLI run the same engine. AdocFoundry documents that, for a profile, "the application and the command line write the same PDF, byte for byte". The plugin keeps that true by passing only what the build author set, and by opening the workspace the app opens. See AdocFoundry's `pdf-export.adoc` ("Choosing a theme", "Fonts", "Publication profiles") and `settings.adoc`.

**How the theme is chosen**, first match wins:

1. The export's own `--theme`. The plugin passes it **only** when the build sets `theme` on a `pdfs` entry; there is no plugin-wide default.
2. The page's `pdf-theme` attribute. For a book, the attributes come from the component's `antora.yml` (`asciidoc.attributes`) or the playbook's.
3. `[pdf] theme` in the settings.
4. The bundled `default` theme.

**Themes, fonts and attributes**
- A theme name that is not bundled is found as `<name>-theme.yml` in the page's `pdf-themesdir`, else in `[pdf] themes_dir`, resolved against the first workspace root.
- Fonts come from `--fonts-dir`, else `pdf-fontsdir`, else `[pdf] fonts_dir`. The bundled fonts are always available.
- A theme's own images (logos, covers) and relative font paths are found beside the theme file.
- `[pdf] standards` adds PDF/A or PDF/UA conformance.
- Source blocks are highlighted only when the page sets `source-highlighter`, as in the app.

**Profiles.** `[pdf.profiles.<name>]` holds a profile's source, theme, fonts, standards, attributes and output. `pdf --profile <name>` applies all of them. A `pdfs` entry with `profile` should set nothing else, unless the author means to override the profile.

**Settings files: the one difference to manage**
- The app reads `[pdf]` and `[pdf.profiles]` from two files:
  - the user settings file, `<config dir>/adocfoundry/adocfoundry.toml`. The config directory is `$XDG_CONFIG_HOME` or `~/.config` on Linux, `~/Library/Application Support` on macOS, and `%APPDATA%` on Windows.
  - the workspace's `.adocfoundry.toml`, whose keys win.
- The CLI reads **only** the workspace's file, so CI gets the same result everywhere.
- A theme chosen in a writer's user settings therefore shapes their app's PDFs but not the build's.
- The plugin's README must say: **put the look in the repository**, in `.adocfoundry.toml`, `antora.yml` or the playbook, never only in the user settings.
- Until the CLI reports this itself (AdocFoundry#812), the plugin warns when the user settings file on the machine has a line beginning `[pdf` (a `[pdf]` or `[pdf.profiles.…]` table). It reads the file only for that warning, and never passes it to the CLI. Disable the warning with `warnAboutUserSettings = false`.

**Checked with 0.15.0**, on one Linux machine, over the `fixtures/themed` layout in §10:
- with no `--theme`, the PDF is byte-identical to the one written with `--theme acme`, and differs from `--theme default`, so `[pdf] theme` in `.adocfoundry.toml` was used;
- two runs, different output paths and a different working directory all gave identical bytes;
- `pdf --profile guide .` exported the profile.

Whether the bytes are the same across machines and operating systems is not yet known (§7, "Not cacheable yet").

**The workspace root decides what is found**
- `.adocfoundry.toml` is read from the root the CLI opens. The playbook, whose attributes may set `pdf-theme`, is found at the root, or else beside the first component version's `antora.yml`.
- A writer usually opens the repository's top folder in the app, so `workspaceDir` defaults to the **project directory**, and every task passes it as the root (`--root` for `pdf`). Pointing it at a subfolder such as `src/docs` would miss a `.adocfoundry.toml` or `antora-playbook.yml` at the top, and could change the look.

## 5. Getting the binary

A `BuildService`, `AdocFoundryToolService`, resolves the executable once per build and is shared by every task. It is registered with `gradle.getSharedServices().registerIfAbsent(...)` and reached through `@ServiceReference`. Resolution order:

1. **`executable` set:** use it as given; nothing is downloaded. Check that it exists and is executable.
2. **Cached:** use `<gradleUserHome>/caches/adocfoundry/<version>/<platform>/adocfoundry-cli[.exe]` if present, with a `.sha256` marker matching the verified archive.
3. **Otherwise download:**
   1. Fetch `SHA256SUMS`, then the platform's asset, from `baseUrl`. Use `java.net.http.HttpClient`, following redirects, with a connect timeout and an overall timeout.
   2. Verify the archive's SHA-256 against its `SHA256SUMS` line. On a mismatch, delete the download and fail with both hashes. A missing line fails too.
   3. Extract the archive into a temporary folder beside the cache folder, then move it into place atomically. Use Gradle's injected `ArchiveOperations` (`tarTree`/`zipTree`) with `FileSystemOperations`, with no extra dependency. Set the executable bit on Unix.
   4. Hold a file lock (`FileChannel.lock` on `<cache>/<version>/.lock`) from the check of the cache through the move. Concurrent builds and Gradle daemons then download once, and a half-written cache is never used.
   5. Refuse archive entries with absolute paths or `..`.
4. **Check the version.** Run `--version` and parse `adocfoundry-cli <x.y.z>`. A version below `MINIMUM_TOOL_VERSION` (0.15.0) fails with a message naming both versions. When `executable` is set, a version different from `toolVersion` is only a warning.

Offline builds (`--offline`) use the cache or `executable` only, and fail with a message naming both options if neither is there.

On Linux, a binary that cannot start fails with a message naming the glibc 2.35 requirement and the `executable` override. This usually shows as "No such file or directory" from the missing loader on musl.

## 6. Extension (DSL)

```groovy
adocfoundry {
    toolVersion = '0.15.0'                    // Property<String>; default: the plugin's pinned version
    executable = file('/opt/adocfoundry-cli')  // RegularFileProperty; optional
    baseUrl = 'https://mirror.example/adocfoundry/releases/download'   // Property<String>; optional
    workspaceDir = layout.projectDirectory    // DirectoryProperty; default: the project directory, the folder opened in AdocFoundry (§4.4)
    warnAboutUserSettings = true              // Property<Boolean>; §4.4

    check {
        failOn = 'error'                      // 'warning' | 'error'; default 'error', as the CLI
    }

    audit {
        component = '2.0@manual'              // optional; whole workspace otherwise
        failOn = 'error'                      // 'none' | 'warning' | 'error'; default: from [quality], else 'error'
        requireComplete = false               // optional; default: from [quality]
    }

    pdfs {                                    // NamedDomainObjectContainer<PdfExport>
        manual {
            component = '2.0@manual'          // one of component, page or profile
            theme = 'src/docs/themes/manual-theme.yml'   // optional, and normally left unset so the workspace decides (§4.4)
            fontsDir = file('src/docs/fonts') // optional; normally unset, as theme
            standards = ['pdf-a-2a']          // optional ListProperty<String>
            failOn = 'none'                   // default 'none', as the CLI
            output = layout.buildDirectory.file('adocfoundry/pdf/manual.pdf')
        }
        intro {
            page = file('src/docs/manual/modules/ROOT/pages/index.adoc')
        }
        handbook {
            profile = 'handbook'              // [pdf.profiles.handbook] in workspaceDir/.adocfoundry.toml
        }
    }
}
```

- Every value is a lazy `Property`, wired to its task's inputs with `convention(...)` and providers. Nothing is read at configuration time.
- Default PDF output: `build/adocfoundry/pdf/<entry-name>.pdf`. For a profile entry, the plugin's `--output` replaces the profile's `output`, because Gradle must know where the file goes. The PDF's bytes are the same.
- `theme`, `fontsDir` and `standards` are passed only when set. With none set, the PDF is the app's (§4.4).
- `audit`'s `failOn` and `requireComplete` are passed to the CLI only when set, so the workspace's `[quality]` table decides otherwise.
- A `pdfs` entry with none, or more than one, of `component`, `page` and `profile` fails at task execution with a message naming the entry.
- String values that the CLI checks itself (`failOn`, `standards`) are passed through unchanged. An unknown value is the CLI's exit 2, reported as a usage error.

## 7. Tasks

All tasks are in the `documentation` group, extend one abstract `AdocFoundryTask`, and run the CLI through injected `ExecOperations` with `isIgnoreExitValue = true`. They capture standard output and standard error and map the exit code (§8).

| Task | Runs | Inputs | Outputs |
|---|---|---|---|
| `adocfoundryCheck` | `check <workspaceDir> --format json --fail-on <failOn>` | docs tree, tool version, `failOn` | `build/reports/adocfoundry/check.json` (standard output saved) |
| `adocfoundryAudit` | `audit <workspaceDir> --format json --output …json`, then `audit … --format sarif --output …sarif --fail-on none` | docs tree, tool version, `component`, `failOn`, `requireComplete` | `build/reports/adocfoundry/audit.json`, `audit.sarif` |
| `adocfoundryPdf<Name>` | `pdf <component-dir or page> --root <workspaceDir> --output <file>`, or `pdf --profile <name> <workspaceDir> --output <file>`, with `--theme`, `--fonts-dir`, `--standard`, `--fail-on` when set; for `component`, `--component <value>` with the root as the positional argument | docs tree (which holds `.adocfoundry.toml`, `antora.yml`, the playbook, themes and fonts), tool version, theme file and fonts dir when set and outside it, standards, `failOn`, the entry's selector | the PDF |
| `adocfoundryPdf` | nothing; depends on every `adocfoundryPdf<Name>` | | |

- **Lifecycle.** The base plugin's `check` depends on `adocfoundryCheck`. `adocfoundryAudit` and the PDF tasks are run only when asked for.
- **The docs tree as input.** Declare it as an `@InputFiles` `ConfigurableFileTree` over `workspaceDir`, with `@PathSensitive(RELATIVE)` and `@IgnoreEmptyDirectories`.
  - Leave out what AdocFoundry leaves out by default (`DEFAULT_IGNORED_DIRECTORIES` in AdocFoundry's `adoc-core`): `.cache`, `.git`, `.zed`, `node_modules`, `build`, `target`, `dist`, `.idea` and `.gradle`, at any depth.
  - Exception: `build`, `target` and `dist` beneath a `modules` folder are ordinary content and stay in.
  - Implement the filter as a static `Spec<FileTreeElement>` class, so it is configuration-cache safe.
  - This also leaves out the CLI's diagram cache (`.cache`, §4.2). Declaring the tree as an `@InputDirectory` would see the CLI's own cache writes as changes.
  - A workspace's own `[workspace] ignore` list is not read; note this in the README.
- **Look inputs outside the workspace.** A `theme` file or `fontsDir` given on an entry that lies outside `workspaceDir` is declared as an extra input.
- **Up to date.** A second run with nothing changed is up to date. `check` and `audit` are given report outputs for this reason, and their reports are rewritten even when the check fails, so the failure is reproducible from the report.
- **Not cacheable yet.** Do not mark tasks `@CacheableTask`. PDF output has not been shown byte-for-byte reproducible across machines, and tools on `PATH` (§4.2) are invisible inputs. Revisit after a reproducibility test across machines (§11).
- **Configuration cache.** Every task must work with `--configuration-cache`: no `Project` at execution time, injected services only, values from providers.
- **Two audit runs.** `audit` runs twice because each call writes one format. The second run passes `--fail-on none`, so the verdict comes from the first run alone.
- **Pages counted.** The PDF task logs the CLI's standard output (`<output> (<n> pages)`) at lifecycle level.

## 8. Exit codes to build results

| CLI exit | Task result |
|---|---|
| 0 | Success. Warnings on standard error are logged at `warn` |
| 1 | `GradleException`, with a message naming the task and up to 20 diagnostics, then "and N more". For `check` these come from the saved JSON; otherwise from standard error. The full report path is named too |
| 2 | `GradleException` stating that the tool could not run the request, with standard error in full |
| other, or a process that cannot start | `GradleException` naming the executable and its platform, with the §5 hint on Linux |

## 9. Project layout and build

```text
settings.gradle
build.gradle                      // java-gradle-plugin, com.gradle.plugin-publish, Java release 17
gradle/wrapper/…                  // 9.3.1
src/main/java/io/github/bovinemagnet/adocfoundry/
    AdocFoundryPlugin.java        // registers the extension, the service and the tasks
    AdocFoundryExtension.java
    CheckSpec.java  AuditSpec.java  PdfExport.java
    tool/AdocFoundryToolService.java  tool/Platform.java  tool/ReleaseAssets.java
    tool/Sha256Sums.java  tool/ToolVersion.java
    tasks/AdocFoundryTask.java  tasks/CheckTask.java  tasks/AuditTask.java  tasks/PdfTask.java
    tasks/ExitCodes.java  tasks/CheckReport.java
src/test/java/…                   // unit tests (JUnit 5)
src/functionalTest/java/…         // Gradle TestKit
src/functionalTest/resources/fixtures/   // Antora fixtures (§10)
.github/workflows/ci.yml
.github/workflows/release.yml
```

- Use the `java-gradle-plugin` with `gradlePlugin { website, vcsUrl, plugins { adocfoundry { id = 'io.github.bovinemagnet.adocfoundry'; implementationClass = …; displayName; description; tags } } }`.
- Use `com.gradle.plugin-publish` for the Portal. Confirm the plugin-publish version and the Portal's group and namespace rules in WP 6.
- Compile with `options.release = 17`.
- The plugin has **no runtime dependencies** beyond the Gradle API and the JDK. JSON parsing for `check.json` uses a small hand-written reader of the fields in §4.2, or Gradle's bundled Groovy JSON support where it works with the configuration cache. Decide in WP 3 and record it in §12. Never shade a JSON library onto the classpath.
- Test dependencies: JUnit 5 and AssertJ, pinned exactly.

## 10. Tests

- **Unit tests**, each class with its own:
  - `Platform` mapping for every `os.name`/`os.arch` pair in §4.3, and unknown ones;
  - `Sha256Sums` parsing, including CRLF, missing entries and a wrong hash;
  - `ToolVersion` parsing and comparison;
  - argument building for each task and each `pdfs` selector;
  - the exit code mapping;
  - the `check.json` reader on the sample in §4.2.
- **Download tests** against an in-process HTTP server (`com.sun.net.httpserver.HttpServer`) serving small fake archives and a `SHA256SUMS`. Cover:
  - success, then a cache hit with no request;
  - a hash mismatch;
  - a missing entry;
  - a redirect;
  - two concurrent resolutions downloading once;
  - an archive entry with `..`;
  - `--offline` without a cache.
- **Functional tests** (TestKit, `withPluginClasspath()`) over fixtures copied from AdocFoundry's `tests/fixtures/antora-single-component`, under its Apache-2.0 licence:
  - `fixtures/clean`: the component as it is. `check` passes.
  - `fixtures/broken-xref`: the same with `See xref:missing.adoc[Missing].` added to `modules/ROOT/pages/index.adoc`.
    - `check` passes under `failOn = 'error'` and fails under `'warning'`.
    - The failure message names `demo/modules/ROOT/pages/index.adoc:5:5` and `adoc.unresolved-xref-file`.
  - `fixtures/themed`: the clean component plus:
    - a `.adocfoundry.toml` with `[pdf] theme = "acme"` and `themes_dir = "themes"`;
    - `themes/acme-theme.yml`, extending `default` with a different `base_font_color`, page size and running footer;
    - a `[pdf.profiles.guide]` naming the component with an `attributes` table;
    - an `antora-playbook.yml` at the workspace root that sets an attribute the PDF shows.
- **Same PDF as AdocFoundry.** For `fixtures/themed`, each PDF the plugin writes, for a component entry, a page entry and the profile, all with no `theme` set, is byte-for-byte identical to the PDF from running `adocfoundry-cli pdf` directly over the same workspace root.
  - The component PDF must also differ from one written with `--theme default`, which shows the workspace's theme was used.
  - This holds the plugin to AdocFoundry's own export, since the app and the CLI write the same bytes.
- **User settings warning.** With a fake user config directory holding `adocfoundry/adocfoundry.toml` with a `[pdf]` table, the plugin logs the §4.4 warning and the PDF is unchanged. Without one, nothing is logged. With `warnAboutUserSettings = false`, nothing is logged. Point the config directory at a temporary folder, never at the developer's real one: by environment for TestKit, and through an internal override for unit tests.
- **Real-binary tests.** Functional tests run the real CLI. They download `toolVersion` once into a test Gradle user home cached by CI, or use `ADOCFOUNDRY_CLI` when set. Tests that need the network are tagged, so the rest run offline.
- **Configuration cache.** Each functional test runs twice with `--configuration-cache`: the second run reuses the cache, and its tasks are up to date.
- **Gradle versions.** Functional tests run on the minimum supported Gradle version and the current one (`GradleRunner.withGradleVersion`).

## 11. Work packages

Do them in order, one pull request each. Each leaves `./gradlew build` green. A package is done when its acceptance tests exist under the names given, and the README reflects any visible change.

| WP | Package | Acceptance tests |
|---|---|---|
| 1 | **Skeleton.** Wrapper 9.3.1, `java-gradle-plugin`, plugin id, empty extension and plugin, unit and functional test source sets, CI on Ubuntu, macOS and Windows. Record the minimum Gradle version in §12 | `pluginApplies`, `extensionHasDefaults` |
| 2 | **Tool provisioning.** `Platform`, `ReleaseAssets`, `Sha256Sums`, `ToolVersion`, `AdocFoundryToolService` as §5 | `mapsEveryPlatform`, `downloadsVerifiesAndCaches`, `refusesWrongHash`, `concurrentResolutionDownloadsOnce`, `usesExecutableWithoutDownloading`, `offlineWithoutCacheFails`, `refusesOldToolVersion` |
| 3 | **`adocfoundryCheck`**, its report, the exit code mapping and the lifecycle wiring | `checkPassesCleanDocs`, `checkFailsOnWarningWhenAsked`, `checkIsUpToDateSecondTime`, `checkWorksWithConfigurationCache` |
| 4 | **`adocfoundryAudit`**, writing JSON and SARIF | `auditWritesJsonAndSarif`, `auditFailsOnThreshold`, `auditRequireCompletePassesThrough` |
| 5 | **`pdfs` and `adocfoundryPdf<Name>`** for component, page and profile, with the look left to the workspace (§4.4) and the user settings warning | `pdfOfComponent`, `pdfOfPage`, `pdfOfProfile`, `pdfEntryNeedsOneSelector`, `pdfIsUpToDateSecondTime`, `pdfOutputUnderBuildDir`, `pdfMatchesAdocFoundry`, `pdfUsesWorkspaceTheme`, `pdfPassesThemeOnlyWhenSet`, `warnsWhenUserSettingsSetPdf` |
| 6 | **Publishing.** Plugin-publish configuration, `release.yml` publishing on `v*` tags with the Portal keys as secrets (`GRADLE_PUBLISH_KEY`, `GRADLE_PUBLISH_SECRET`), the README's usage section | `pluginMarkerIsPublishedLocally` (publish to a local Maven repository and apply from it) |
| 7 | **Measurement.** Time `check` and a component PDF of AdocFoundry's own `src/docs` through the plugin against Antora (`gradle antora` with `log.failure_level: warn`) and asciidoctor-pdf, on one machine, cold and warm. Record the figures in the README | none: the numbers are the deliverable |

After the first release, in any order:

- Word, EPUB, Kindle and DocBook containers in the pattern of `pdfs`. EPUB sets `SOURCE_DATE_EPOCH` from the commit time, or `0` when there is no Git.
- Adopt AdocFoundry#812 when released: log the CLI's `theme:` line at lifecycle level, and use its note in place of the plugin's own user-settings check.
- Adopt AdocFoundry#807 (versioned `check` JSON), #808 (`--format json` from exports, to declare and verify outputs), #809 (`timeout` in the extension) and #810 (`--cache-dir` into `build/`, then drop the `.cache/**` exclusion) as each is released. Raise `MINIMUM_TOOL_VERSION` only when a feature needs it.
- The standalone Windows asset once AdocFoundry#806 ships: pick it by `toolVersion`, falling back to the application zip for older versions.
- Problems API reporting, once it is stable on the minimum Gradle version.
- `@CacheableTask`, after a test shows byte-identical PDFs from two machines and documents the inputs that matter.

## 12. Implementation notes

Record here, in the pull request that makes it, any departure from this specification and why.

- (none yet)
