/*
 *    Copyright 2019 Florian Spieß
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
import club.minnced.jda.reactor.ReactiveEventManager
import club.minnced.jda.reactor.on
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.guild.GuildBanEvent
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberLeaveEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        error("Cannot start bot without a token!")
    }

    val manager = ReactiveEventManager()
    //Apply ready handler before calling build() to avoid race condition
    // READY -> set status from DND to ONLINE
    manager.on<ReadyEvent>()
        .next()
        .map { it.jda.presence }
        .subscribe { it.setStatus(OnlineStatus.ONLINE) }

    // Start the JDA connection
    val jda = JDABuilder(args[0])
        .setEventManager(manager) // alternatively just reactive() if the manager doesn't need to be used directly
        .setActivity(Activity.listening("for commands"))
        .setStatus(OnlineStatus.DO_NOT_DISTURB) // status DND during setup
        .build()

    // Handle commands
    jda.on<MessageReceivedEvent>()
        // don't respond to bots
        .filter { !it.author.isBot }
        // filter by prefix
        .filter { it.message.contentRaw.startsWith("--") }
        .subscribe {
            // Commands that work anywhere
            onBasicCommand(it)
            // Commands that only work in guilds
            if (it.isFromGuild && it.textChannel.checkWrite())
                onGuildCommand(it)
        }

    //Handle events for mod-log, note that all of these only work when the audit entry is generated
    // This means the leave event will only trigger the mod-log update if it can be seen as a kick through audit logs.

    // Ban
    jda.on<GuildBanEvent>()
       .subscribe { onMemberBan(it.guild, it.user) }
    // Unban
    jda.on<GuildUnbanEvent>()
       .subscribe { onMemberUnban(it.guild, it.user) }
    // Possibly kick (usually just leave, this is also triggered by bans)
    jda.on<GuildMemberLeaveEvent>()
       .subscribe { onMemberKick(it.guild, it.user) }
}

fun onBasicCommand(event: MessageReceivedEvent) {
    val content = event.message.contentRaw
    val parts = content.split(" ", limit = 2)
    val command = parts[0].substring(2).toLowerCase()
    when (command) {
        "ping" -> onPing(event.channel)
        "rtt" -> onRTT(event.channel)
        "avatar" -> onAvatar(parts.getOrNull(1), event)
    }
}

fun onGuildCommand(event: MessageReceivedEvent) {
    val content = event.message.contentRaw
    val parts = content.split(" ", limit = 2)
    val command = parts[0].substring(2).toLowerCase()
    when (command) {
        "softban" -> onSoftban(parts.getOrNull(1), event)
        "purge" -> onPurge(parts.getOrNull(1), event)
    }
}

