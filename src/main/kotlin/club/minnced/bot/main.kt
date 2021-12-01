/*
 *    Copyright 2019-2020 Florian Spieß
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("Main")
package club.minnced.bot

import club.minnced.bot.command.*
import club.minnced.bot.moderation.onMemberBan
import club.minnced.bot.moderation.onMemberKick
import club.minnced.bot.moderation.onMemberUnban
import club.minnced.jda.reactor.asMono
import club.minnced.jda.reactor.createManager
import club.minnced.jda.reactor.on
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.mono
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.ShutdownEvent
import net.dv8tion.jda.api.events.guild.GenericGuildEvent
import net.dv8tion.jda.api.events.guild.GuildBanEvent
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.requests.GatewayIntent.*
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import kotlin.concurrent.thread
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val token = getToken(args)

    // Create a shared executor for the flux processor and JDA
    var count = 0
    val executor = Executors.newScheduledThreadPool(ForkJoinPool.getCommonPoolParallelism()) {
        thread(start=false, block=it::run, name="jda-thread-${count++}", isDaemon=true)
    }

    // Create a reactive event manager
    val manager = createManager()

    // Apply ready handler before calling build() to avoid race condition
    manager.on<ReadyEvent>()
        // Convert to Mono<ReadyEvent> to simplify
        .next()
        // Take the JDA instance
        .map { it.jda }
        // READY -> set status from DND to ONLINE
        .doOnSuccess { it.presence.setStatus(OnlineStatus.ONLINE) }
        // Retrieve bot owner
        .flatMap { it.retrieveApplicationInfo().asMono() }
        // We only need the id
        .map { it.owner.idLong } // Alternatively use the team with it.team and check the members!
        // Map to owner-only commands
        .flatMapMany { ownerId ->
            manager.on<MessageReceivedEvent>()
                   .filter { it.author.idLong == ownerId }
        }
        // We only have one owner-only command called shutdown
        .filter { it.message.contentRaw == "--shutdown" }
        // Convert to JDA instance of the event
        .map(MessageReceivedEvent::getJDA)
        // Shutdown
        .subscribe {
            // To make the JVM shutdown we have to get rid of all the user threads (non-daemon threads)
            //  JDA creates at least 2 user threads for the gateway connection (receiving events from discord)
            //  and the HTTP client creates at least 1 user thread for the discord connection (http/2 re-used socket)
            // Shutdown JDA connection
            it.shutdown()
            // Prune http client threads
            it.httpClient.connectionPool().evictAll()
        }


    // Start the JDA connection
    val jda = JDABuilder.createLight(token, GUILD_MESSAGES, GUILD_MEMBERS, GUILD_BANS)
        .setEventManager(manager) // alternatively just reactive() if the manager doesn't need to be used directly
        .setActivity(Activity.listening("for commands"))
        .setStatus(OnlineStatus.DO_NOT_DISTURB) // status DND during setup
        .setRateLimitPool(executor)
        .setGatewayPool(executor)
        .build()

    // Handle commands
    jda.on<MessageReceivedEvent>()
        // don't respond to bots
        .filter { !it.author.isBot }
        // filter by prefix
        .filter { it.message.contentRaw.startsWith("--") }
        .flatMap(::handleCommand)
        .subscribe()

    //Handle events for mod-log, note that all of these only work when the audit entry is generated
    // This means the leave event will only trigger the mod-log update if it can be seen as a kick through audit logs.

    // Only handle if audit logs are readable
    val hasPermission: (GenericGuildEvent) -> Boolean = { it.guild.selfMember.hasPermission(Permission.VIEW_AUDIT_LOGS) }

    // Ban
    jda.on<GuildBanEvent>()
       .filter(hasPermission)
       .subscribe { onMemberBan(it.guild, it.user) }
    // Unban
    jda.on<GuildUnbanEvent>()
       .filter(hasPermission)
       .subscribe { onMemberUnban(it.guild, it.user) }
    // Possibly kick (usually just leave, this is also triggered by bans)
    jda.on<GuildMemberRemoveEvent>()
       .filter(hasPermission)
       .subscribe { onMemberKick(it.guild, it.user) }

    // Handle shutdown cleanup
    jda.on<ShutdownEvent>()
       .subscribe {
           // Cleanup HTTP connections that keep the JVM from shutting down
           it.jda.httpClient.connectionPool().evictAll()
       }
}

private fun handleCommand(it: MessageReceivedEvent) = mono {
    // Commands that work anywhere
    onBasicCommand(it).awaitFirstOrNull()
    // Commands that only work in guilds
    if (it.isFromGuild && it.textChannel.checkWrite())
        onGuildCommand(it).awaitFirstOrNull()
}

private fun getToken(args: Array<String>): String {
    if (args.isEmpty()) {
        println("Cannot start bot without a token!")
        exitProcess(1)
    }

    val tokenFile = File(args[0])
    if (!tokenFile.canRead()) {
        println("Cannot read from file ${args[0]}")
        exitProcess(2)
    }

    return tokenFile.readText().trim()
}

fun onBasicCommand(event: MessageReceivedEvent): Mono<*> {
    val content = event.message.contentRaw
    val parts = content.split(" ", limit = 2)
    val command = parts[0].substring(2).toLowerCase()
    return when (command) {
        "ping" -> onPing(event.channel)
        "rtt" -> onRTT(event.channel)
        "avatar" -> onAvatar(parts.getOrNull(1), event)
        else -> Mono.empty<Unit>()
    }
}

fun onGuildCommand(event: MessageReceivedEvent): Mono<*> {
    val content = event.message.contentRaw
    val parts = content.split(" ", limit = 2)
    val command = parts[0].substring(2).toLowerCase()
    return when (command) {
        "softban" -> onSoftban(parts.getOrNull(1), event)
        "purge" -> onPurge(parts.getOrNull(1), event)
        else -> Mono.empty<Unit>()
    }
}

