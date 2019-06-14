import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.3.21"
}

application.mainClassName = "club.minnced.bot.MainKt"

group = "club.minnced"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
}

dependencies {
    implementation("club.minnced:jda-reactor:0.1.11")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("net.dv8tion:JDA:4.ALPHA.0_107")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}