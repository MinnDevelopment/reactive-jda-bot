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
    implementation("club.minnced:jda-reactor:0.2.7")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("net.dv8tion:JDA:4.0.0_39")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
