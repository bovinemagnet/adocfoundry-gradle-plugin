# AdocFoundry Gradle plugin

A Gradle plugin that validates AsciiDoc documentation laid out the Antora way and renders it to PDF as part of a Gradle build. It runs [`adocfoundry-cli`](https://github.com/bovinemagnet/AdocFoundry), a single native binary that needs no Node.js, JVM or Ruby at run time.

> **Status:** planned. Nothing here is usable yet. The design and the decisions behind it are in [AdocFoundry#805](https://github.com/bovinemagnet/AdocFoundry/issues/805).

## What it will do

- **Validate** a documentation tree with `adocfoundry-cli check` and `audit`, failing the build on broken references and other problems. Audit reports are written as JSON and SARIF.
- **Render PDFs** of Antora components, or of the PDF profiles a workspace's `.adocfoundry.toml` defines.
- **Later**, export Word, EPUB, Kindle and DocBook the same way.

It does not build the Antora HTML site; keep the `org.antora` plugin for that, beside this one. It works on local content only and never clones a playbook's remote sources.

## Planned use

```groovy
plugins {
    id 'io.github.bovinemagnet.adocfoundry' version '0.0.0'
}

adocfoundry {
    toolVersion = '0.15.0'
    docsDir = file('src/docs')

    check {
        failOn = 'warning'
    }
    pdfs {
        manual {
            component = '2.0@manual'
            output = layout.buildDirectory.file('pdf/manual.pdf')
        }
    }
}
```

`gradle check` would then download the CLI for the platform, verify it against the release's `SHA256SUMS`, cache it, and run `adocfoundryCheck`. `gradle adocfoundryPdf` would write the PDFs. The names are illustrative until the first release.

## Decisions

- The CLI is downloaded from AdocFoundry's GitHub releases and verified against `SHA256SUMS`. A local executable or a mirror can be used instead.
- The first release covers `check`, `audit` and `pdf`.
- Each plugin release pins a tested default CLI version, which a build may override.
- The plugin supports the oldest Gradle release still maintained when it ships, and is tested up to the current one.

## Depends on

These changes to AdocFoundry's CLI and releases make the plugin's contract firm:

- [#806](https://github.com/bovinemagnet/AdocFoundry/issues/806): a standalone CLI archive for Windows.
- [#807](https://github.com/bovinemagnet/AdocFoundry/issues/807): versioned `check` JSON output.
- [#808](https://github.com/bovinemagnet/AdocFoundry/issues/808): `--format json` for exports.
- [#809](https://github.com/bovinemagnet/AdocFoundry/issues/809): a `--timeout` option.
- [#810](https://github.com/bovinemagnet/AdocFoundry/issues/810): a `--cache-dir` option for the diagram cache.
- [#811](https://github.com/bovinemagnet/AdocFoundry/issues/811): Linux builds for older glibc and musl.

## Licence

Apache License 2.0; see [LICENSE](LICENSE).
