plugins {
    java
    `maven-publish`
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://momento.jfrog.io/artifactory/maven-public")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

val javaSdkVersion = rootProject.ext["javaSdkVersion"]
val momentoSdkVersion = rootProject.ext["momentoSdkVersion"]

dependencies {
    implementation("com.amazonaws:aws-lambda-java-core:1.2.1")
    implementation("com.amazonaws:aws-java-sdk-secretsmanager:$javaSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-cloudwatchmetrics:$javaSdkVersion")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("momento.sandbox:momento-sdk:$momentoSdkVersion")
    testImplementation("org.apache.logging.log4j:log4j-api:[2.17.1,)")
    testImplementation("org.apache.logging.log4j:log4j-core:[2.17.1,)")
    testImplementation("org.apache.logging.log4j:log4j-slf4j18-impl:[2.17.1,)")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.6.0")
}

group = "com.example"
version = "1.0-SNAPSHOT"
description = "momento-signing-key-renewal-lambda"
java.sourceCompatibility = JavaVersion.VERSION_1_8

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.register<Copy>("copyDependencies") {
    from(configurations.runtimeClasspath)
    into("$buildDir/dependencies")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "example.Handler"
    }
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.INCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}
