# CLAUDE.md

This file provides guidance when working with code in this repository. The repository guidelines are in `AGENTS.md`, imported here:

@AGENTS.md

## Before writing code

- Read `docs/IMPLEMENTATION-SPEC.md` in full, then the section for the work package in hand. Work the packages in §11's order, one per pull request.
- Make a plan with a todo per step, and work test first: the acceptance tests named in §11 come first.
- Read AdocFoundry's command-line reference (`src/docs/modules/ROOT/pages/command-line.adoc` in `bovinemagnet/AdocFoundry`, or <https://bovinemagnet.github.io/AdocFoundry>) before changing how the CLI is called. Check behaviour against a real `adocfoundry-cli` rather than assuming it.
- The PDF look is AdocFoundry's to decide (§4.4). If a change could alter which theme, fonts, attributes or settings file the CLI sees, `pdfMatchesAdocFoundry` must still pass.

## Done means

- `./gradlew build` passes, functional tests and the configuration-cache runs included.
- The package's acceptance tests exist with the names §11 gives.
- `README.md` and, for any departure from the spec, §12 are updated.
- The **Current state** line in `AGENTS.md` says which work packages have landed.

## Easy to miss in the spec

These cut across sections, and are planned rather than built:

- The docs tree is a filtered `ConfigurableFileTree` over `workspaceDir`, never an `@InputDirectory`: the CLI writes its diagram cache to `.cache` inside the tree, which would make every task out of date (§4.2, §7).
- For a `profile` entry, the plugin's `--output` replaces the profile's own `output`, so Gradle knows where the PDF goes; the bytes are unchanged (§6).
- `adocfoundryAudit` runs the CLI twice, once per report format. The second run passes `--fail-on none`, so the verdict comes from the first alone (§7).
- An unresolved xref is a warning, so `check` passes it under the default `--fail-on error`; the `broken-xref` fixture depends on this (§4.2, §10).
