# Publishing

## Maven Central

`cdd-kotlin` targets JVM, allowing it to be published to Maven Central.
It requires standard `maven-publish` plugin inside `build.gradle.kts`.

```bash
./gradlew publishToMavenLocal
# or to push to Maven Central (requires Sonatype credentials)
./gradlew publish
```

## Documentation

To generate static API documentation via Dokka, run:
```bash
make build_docs
```

The output will be found in `docs/` and can be served through any static hosting like NGINX or GitHub Pages:

```yaml
# Upload docs
name: Deploy docs
on:
  push:
    branches: ["main"]
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: make build_docs
      - uses: peaceiris/actions-gh-pages@v3
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./docs
```