plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

javafx {
    version = "21.0.2"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("org.example.Main")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}
dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}


tasks.named<JavaExec>("run") {
    jvmArgs("-Dfile.encoding=UTF-8")
    // Дополнительно — для stdout/stderr
    doFirst {
        systemProperty("stdout.encoding", "UTF-8")
        systemProperty("stderr.encoding", "UTF-8")
    }
}