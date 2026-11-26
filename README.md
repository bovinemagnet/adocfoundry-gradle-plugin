# AdocFoundry Gradle plugin

A Gradle plugin that validates AsciiDoc documentation laid out the Antora way and renders it to PDF as part of a Gradle build. It runs [`adocfoundry-cli`](https://github.com/bovinemagnet/AdocFoundry), a single native binary that needs no Node.js, JVM or Ruby at run time.

> **Status:** in development. Checking, auditing and PDF export work; nothing is published yet. The implementation specification is [docs/IMPLEMENTATION-SPEC.md](docs/IMPLEMENTATION-SPEC.md), and the discussion behind it is [AdocFoundry#805](https://github.com/bovinemagnet/AdocFoundry/issues/805).

## What it will do

- **Validate** a documentation tree with `adocfoundry-cli check` and `audit`, failing the build on broken references and other problems. Audit reports are written as JSON and SARIF.
- **Render PDFs** of Antora components, or of the PDF profiles a workspace's `.adocfoundry.toml` defines.
- **Later**, export Word, EPUB, Kindle and DocBook the same way.

PDFs look exactly as AdocFoundry exports them: the plugin opens the same workspace and leaves the theme, fonts and attributes to the workspace's `.adocfoundry.toml`, `antora.yml` and playbook. Keep the look in the repository, not only in your AdocFoundry user settings, which the build does not read.

It does not build the Antora HTML site; keep the `org.antora` plugin for that, beside this one. It works on local content only and never clones a playbook's remote sources.

## Checking the documentation

```groovy
plugins {
    id 'io.github.bovinemagnet.adocfoundry' version '0.0.0'
}

adocfoundry {
    check {
        failOn = 'warning'    // default 'error'
    }
}
```

`gradle check` runs `adocfoundryCheck`. It downloads `adocfoundry-cli` for the platform from AdocFoundry's releases, verifies it against the release's `SHA256SUMS`, caches it in the Gradle user home, and runs `adocfoundry-cli check` over the workspace. The report is saved as `build/reports/adocfoundry/check.json`, even when the check fails.

- **The workspace** is the project directory, the folder a writer opens in AdocFoundry. Set `workspaceDir` to change it.
- **`check.failOn`** is `error` or `warning`. An unresolved xref is a warning: it is logged under the default, and fails the build under `warning`.
- **`toolVersion`** chooses the CLI release (default 0.15.0, the oldest supported). **`executable`** runs a local `adocfoundry-cli` instead, and **`baseUrl`** points at a mirror of the releases. An `--offline` build uses the cache or `executable`.
- **Up to date.** The task reruns only when the workspace's files change. It leaves out the folders AdocFoundry leaves out by default: `.cache`, `.git`, `.zed`, `node_modules`, `build`, `target`, `dist`, `.idea` and `.gradle`, except `build`, `target` and `dist` inside a component's `modules` folder. A workspace's own `[workspace] ignore` list is not read, so a change in a folder it lists still reruns the task.
- **Linux** binaries need glibc 2.35 or later (Ubuntu 22.04+, Debian 12+). On RHEL 8 or 9, Amazon Linux 2 or Alpine, set `executable` to a binary built for the system.

## Auditing the documentation

```groovy
adocfoundry {
    audit {
        component = '2.0@manual'   // optional; the whole workspace otherwise
        failOn = 'warning'         // optional: 'none', 'warning' or 'error'
        requireComplete = true     // optional
    }
}
```

`gradle adocfoundryAudit` runs `adocfoundry-cli audit` and writes `build/reports/adocfoundry/audit.json` and `audit.sarif` (SARIF 2.1.0, for code scanning). It is not part of `check`. Options left unset are not passed, so the `[quality]` table of the workspace's `.adocfoundry.toml` decides; `requireComplete = false` cannot turn off a `require_complete` set there. Both reports are written even when the audit fails.

## Exporting PDFs

```groovy
adocfoundry {
    pdfs {
        manual {
            component = '2.0@manual'   // a component version, as a book
        }
        intro {
            page = file('docs/modules/ROOT/pages/index.adoc')   // a page, as an article
        }
        handbook {
            profile = 'handbook'       // [pdf.profiles.handbook] in .adocfoundry.toml
        }
    }
}
```

Each entry sets exactly one of `component`, `page` and `profile`, and gets its own task: `adocfoundryPdfManual`, `adocfoundryPdfIntro` and so on. `gradle adocfoundryPdf` runs them all. A PDF is written to `build/adocfoundry/pdf/<name>.pdf` unless the entry sets `output`; for a profile, this replaces the profile's own `output`. The task logs the CLI's `<output> (<n> pages)` line, and is up to date while the workspace and the entry are unchanged.

**The look is the workspace's.** A PDF from the plugin is byte for byte the one `adocfoundry-cli pdf` writes for the same workspace, and so the one the AdocFoundry app exports. The theme, fonts and attributes come from the workspace's `.adocfoundry.toml`, the component's `antora.yml` and the playbook. Keep the look there, in the repository, and not only in your AdocFoundry user settings, which the build does not read. When the user settings file on the machine has a `[pdf]` table, each PDF task warns that it is not used; set `warnAboutUserSettings = false` to stop the warning.

An entry may also set these, though normally they are left unset so that the workspace decides:

- `theme`: a bundled theme, a `<name>-theme.yml` in the themes directory, or a `.yml` file relative to the project directory;
- `fontsDir`: the theme's fonts;
- `standards`: such as `['pdf-a-2a']`, added to the workspace's;
- `failOn`: `none` (the default), `warning` or `error`, for export diagnostics such as a missing image. The PDF is written either way.

## Decisions

- The CLI is downloaded from AdocFoundry's GitHub releases and verified against `SHA256SUMS`. A local executable or a mirror can be used instead.
- The first release covers `check`, `audit` and `pdf`.
- Each plugin release pins a tested default CLI version, which a build may override.
- The plugin supports the oldest Gradle release still maintained when it ships, and is tested up to the current one: today Gradle 8.14 to 9.9. It needs Java 17 or later to run Gradle.

## Depends on

These changes to AdocFoundry's CLI and releases make the plugin's contract firm:

- [#806](https://github.com/bovinemagnet/AdocFoundry/issues/806): a standalone CLI archive for Windows.
- [#807](https://github.com/bovinemagnet/AdocFoundry/issues/807): versioned `check` JSON output.
- [#808](https://github.com/bovinemagnet/AdocFoundry/issues/808): `--format json` for exports.
- [#809](https://github.com/bovinemagnet/AdocFoundry/issues/809): a `--timeout` option.
- [#810](https://github.com/bovinemagnet/AdocFoundry/issues/810): a `--cache-dir` option for the diagram cache.
- [#811](https://github.com/bovinemagnet/AdocFoundry/issues/811): Linux builds for older glibc and musl.
- [#812](https://github.com/bovinemagnet/AdocFoundry/issues/812): name the PDF theme used, and note when user settings would choose another.

## Licence

Apache License 2.0; see [LICENSE](LICENSE).
