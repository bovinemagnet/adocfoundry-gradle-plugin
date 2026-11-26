# Functional test fixtures

Each folder is copied into a TestKit project's directory, which is then the workspace root.

- `clean`: AdocFoundry's `tests/fixtures/antora-single-component`, as the component `demo`.
- `broken-xref`: the same, with `See xref:missing.adoc[Missing].` added to `demo/modules/ROOT/pages/index.adoc`.
- `themed`: the clean component, plus a `.adocfoundry.toml` choosing the `acme` theme from `themes/`, a `guide` PDF profile, and an `antora-playbook.yml` setting `product-name`, which the theme's footer prints.

The component files come from [AdocFoundry](https://github.com/bovinemagnet/AdocFoundry), under the Apache License 2.0.
