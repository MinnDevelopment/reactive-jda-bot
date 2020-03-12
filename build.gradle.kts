import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.3.21"
}

application.mainClassName = "club.minnced.bot.Main"

group = "club.minnced"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
}

dependencies {
    implementation("club.minnced:jda-reactor:1.0.0")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("net.dv8tion:JDA:4.1.1_111")

    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.3.2")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
