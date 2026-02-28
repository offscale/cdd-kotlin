# Publishing `cdd-kotlin`

This guide explains how to publish the `cdd-kotlin` library to Maven Central (the standard repository for Kotlin and Java libraries) and how to generate and host its documentation.

## Publishing to Maven Central

To publish this library so others can use it via Gradle (`implementation("org.cdd:cdd-kotlin:1.0-SNAPSHOT")`), we use the `maven-publish` and `signing` Gradle plugins.

### 1. Gradle Configuration

Ensure your `build.gradle.kts` is configured for publishing. You will need to add the `maven-publish` and `signing` plugins:

```kotlin
plugins {
    // ... existing plugins
    `maven-publish`
    signing
}

// Ensure you have sources and javadoc jars configured
java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            
            pom {
                name.set("cdd-kotlin")
                description.set("Bidirectional OpenAPI ↔ Kotlin Multiplatform code generator.")
                url.set("https://github.com/offscale/cdd-kotlin")
                
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("developerId")
                        name.set("Developer Name")
                        email.set("developer@example.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/offscale/cdd-kotlin.git")
                    developerConnection.set("scm:git:ssh://github.com/offscale/cdd-kotlin.git")
                    url.set("https://github.com/offscale/cdd-kotlin")
                }
            }
        }
    }
    
    repositories {
        maven {
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            
            credentials {
                username = project.findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                password = project.findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    val signingKey = project.findProperty("signingKey") as String? ?: System.getenv("SIGNING_KEY")
    val signingPassword = project.findProperty("signingPassword") as String? ?: System.getenv("SIGNING_PASSWORD")
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
}
```

### 2. Execution

To publish a release, provide your Sonatype credentials and GPG signing key:

```bash
export OSSRH_USERNAME="your-sonatype-username"
export OSSRH_PASSWORD="your-sonatype-password"
export SIGNING_KEY="your-ascii-armored-gpg-private-key"
export SIGNING_PASSWORD="your-gpg-password"

./gradlew publish
```

*Note: For official releases, you will then need to log into the Sonatype Nexus Repository Manager to "Close" and "Release" the staging repository, or automate this using the `nexus-publish-plugin`.*

---

## Generating and Publishing Documentation

Kotlin uses **Dokka** for documentation generation, which processes KDoc comments into HTML, Javadoc, or Markdown.

### 1. Generating Local Documentation for Static Serving

First, apply the Dokka plugin in your `build.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.dokka") version "1.9.20"
}
```

To generate a static HTML website that can be served locally or uploaded to any static web host:

```bash
./gradlew dokkaHtml
```

The output will be generated in `build/dokka/html/`. You can serve this locally using any simple HTTP server, for example:

```bash
cd build/dokka/html
python3 -m http.server 8080
# Open http://localhost:8080 in your browser
```

### 2. Uploading Docs to the Most Popular Location (GitHub Pages)

For open-source projects hosted on GitHub, **GitHub Pages** is the standard location for documentation. You can automate this using GitHub Actions.

Create a workflow file at `.github/workflows/docs.yml`:

```yaml
name: Deploy Dokka to GitHub Pages

on:
  push:
    branches:
      - master

permissions:
  contents: read
  pages: write
  id-token: write

jobs:
  deploy:
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Generate Dokka HTML
        run: ./gradlew dokkaHtml

      - name: Setup Pages
        uses: actions/configure-pages@v4

      - name: Upload artifact
        uses: actions/upload-pages-artifact@v3
        with:
          path: 'build/dokka/html'

      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4
```

This workflow will automatically regenerate and publish your documentation to `https://<your-org>.github.io/cdd-kotlin/` every time you push to `master`.

### 3. Javadoc for Maven Central

When publishing to Maven Central, a `-javadoc.jar` is required. You can configure Dokka to fulfill this requirement in `build.gradle.kts`:

```kotlin
tasks.register<Jar>("javadocJar") {
    dependsOn(tasks.dokkaJavadoc)
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
}

java {
    withSourcesJar()
}

// In your publishing configuration, ensure the javadoc jar is included:
// artifact(tasks["javadocJar"])
```
