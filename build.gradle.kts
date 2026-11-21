plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(17) }

application { mainClass.set("pos.MainKt") }

tasks.test { useJUnitPlatform() }

// Чтобы `gradle run` принимал ввод из консоли
tasks.named<JavaExec>("run") { standardInput = System.`in` }
