plugins {
    kotlin("jvm") version "2.2.21"
}

group = "org.cdd"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // The Kotlin Standard Library
    implementation(kotlin("stdlib"))

    // REQUIRED FOR NEXT STEPS: The Kotin Compiler PSI (Embeddable)
    // We will use this in Feature D-01/D-02 to parse code
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.21")

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17) // Use JDK 17 or higher
}
