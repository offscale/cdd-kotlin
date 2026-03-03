# Client SDK Publishing

This outlines the process of publishing an SDK automatically created by `cdd-kotlin`.

When you output an SDK via `cdd-kotlin from_openapi to_sdk`, the output directory is configured as a standalone Kotlin project ready to be published to Maven Central.

## Cronjob updating OpenAPI

Use a GitHub Action to fetch the latest OpenAPI spec, run `cdd-kotlin` on it, and commit the changes if they exist.

```yaml
name: SDK Synchronizer
on:
  schedule:
    - cron: '0 0 * * *' # Daily
jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Generate SDK
        run: cdd-kotlin from_openapi to_sdk -i http://api.server.com/openapi.json -o ./my-sdk
      - name: Commit if changed
        run: |
          git config user.name "bot"
          git add ./my-sdk
          git commit -m "Auto-update SDK" || echo "No changes"
          git push
```

## Releasing the client to Maven Central

Similarly, `my-sdk/build.gradle.kts` comes prepared with publishing components.

```bash
cd my-sdk
./gradlew publish
```