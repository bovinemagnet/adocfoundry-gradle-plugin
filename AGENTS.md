# Repository guidelines

## Project

A Gradle plugin, `io.github.bovinemagnet.adocfoundry`, that runs `adocfoundry-cli` from [AdocFoundry](https://github.com/bovinemagnet/AdocFoundry) in a Gradle build. It validates Antora documentation (`check`, `audit`) and renders PDFs (`pdf`) that look exactly as AdocFoundry's own export does.

**Start with `docs/IMPLEMENTATION-SPEC.md`.** It is the source of truth for scope, design and the order of work. It sets out:

- the CLI contract the plugin relies on (§4) and how the PDF look is decided (§4.4);
- binary provisioning (§5), the DSL (§6) and the tasks (§7);
- exit codes (§8), layout (§9) and tests (§10);
- the work packages (§11).

Decisions already made are in §3; do not reopen them without asking. Departures go in §12, in the pull request that makes them.

**Current state:** WP 1 (skeleton), WP 2 (tool provisioning), WP 3 (`adocfoundryCheck`), WP 4 (`adocfoundryAudit`), WP 5 (`pdfs`) and WP 6 (publishing) are done. WP 7, the measurements, is next.

## Commands

```sh
./gradlew build                   # compile, unit tests, functional tests, checks
./gradlew test                    # unit tests
./gradlew functionalTest          # TestKit tests (from WP 1)
./gradlew functionalTest --tests '*CheckTaskTest*'
./gradlew functionalTest -PfunctionalTestGradleVersions=9.9.2   # one Gradle version only
./gradlew build -PskipNetworkTests  # leave out tests tagged `network`, which run the real CLI
./gradlew publishToMavenLocal     # try the plugin from another build
./gradlew wrapper --gradle-version 9.3.1   # change the wrapper version
```

Functional tests that download the real CLI need the network. Set `ADOCFOUNDRY_CLI=/path/to/adocfoundry-cli` to run them with a local binary instead.

## Conventions that must be preserved

**The CLI and the PDF look**
- **The look is AdocFoundry's.** Never pass `--theme`, `--fonts-dir` or `--standard` unless the build author set them, and always open the same workspace root the app opens (`workspaceDir`, by default the project directory). A plugin PDF must be byte-for-byte the CLI's PDF for the same workspace (§4.4, test `pdfMatchesAdocFoundry`).
- **The CLI is the only engine.** The plugin never parses AsciiDoc, resolves Antora coordinates or chooses themes itself; it builds arguments, runs the binary and reports.
- **Absolute paths only.** Pass absolute paths to the CLI, always pass `--output` for exports, and set the working directory to the project directory.

**Gradle**
- **Configuration cache.** No `Project` at execution time; use injected services (`ExecOperations`, `ArchiveOperations`, `FileSystemOperations`) and lazy `Property`/`Provider` values. Every functional test runs with `--configuration-cache` twice.
- **Up-to-date checks, not the build cache.** Declare inputs and outputs (§7). Do not mark tasks `@CacheableTask` until cross-machine reproducibility is shown.

**The download**
- **Verify every download** against the release's `SHA256SUMS` before use.
- **Cache safely.** Extract atomically under a file lock and refuse archive entries with absolute paths or `..`.
- **Never execute an unverified binary**, except a user-supplied `executable`.

**Dependencies and code**
- **No runtime dependencies** beyond the Gradle API and the JDK. Test dependencies are pinned to exact versions.
- **Java, compiled with `--release 17`.**
- **Typed failures.** Fail with a `GradleException` whose message names the task, the CLI's exit code and what to do; never a bare stack trace for an expected failure.

**Repository practice**
- **British spelling** in code, comments and documentation.
- **Author.** Use Paul Snow where an author is needed, and version 0.0.0 until a release says otherwise.
- **Pinned CI.** GitHub Actions are pinned to commit SHAs, every job has `timeout-minutes`, and checkouts set `persist-credentials: false`, as in AdocFoundry's workflows.

## Testing

- Work test first: write the failing test named in §11 for the package, then make it pass.
- Unit tests live in `src/test/java`; TestKit tests in `src/functionalTest/java`, with fixtures under `src/functionalTest/resources/fixtures/` (§10).
- Fixtures are copied from AdocFoundry's `tests/fixtures/antora-single-component` (Apache-2.0); keep them small.
- Never test against a developer's real Gradle user home or AdocFoundry settings file; point both at temporary folders.

## Working rules

- One work package per pull request, in the order of §11, titled `WP <n>: <name>`. Each leaves `./gradlew build` green.
- The pull request description lists the spec section, the tests added and the commands run.
- When the CLI lacks something the plugin needs, open an issue in `bovinemagnet/AdocFoundry` and reference it from §11, rather than working around it silently. Related issues already open: AdocFoundry#806–#812 and #817.
- Update `README.md` when usage or visible behaviour changes.
- Commit messages are short and imperative (`Add the tool provisioning service`).
