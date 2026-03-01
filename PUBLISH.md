# PUBLISH

## Publishing `cdd-kotlin` to Maven Central

To publish this library and CLI to Maven Central, we use the `maven-publish` plugin along with the `signing` plugin in Gradle.

1. Ensure your `gradle.properties` has the required Sonatype credentials:
```properties
ossrhUsername=your-username
ossrhPassword=your-password
signing.keyId=YOUR_KEY_ID
signing.password=YOUR_KEY_PASSWORD
signing.secretKeyRingFile=/path/to/secring.gpg
```

2. Run the publish task:
```bash
./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
```

## Publishing Docs

We use Dokka for Kotlin API documentation.
To generate a static folder for your server:
```bash
make build_docs
# Output is placed in docs/
```
You can serve the `docs/` folder directly via any static HTTP server (e.g. Nginx, Apache).

To publish to GitHub Pages, use a CI Action:
```yaml
- name: Build Docs
  run: make build_docs
- name: Deploy to GitHub Pages
  uses: peaceiris/actions-gh-pages@v3
  with:
    github_token: ${{ secrets.GITHUB_TOKEN }}
    publish_dir: ./docs
```
