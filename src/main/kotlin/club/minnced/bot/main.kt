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

package club.minnced.bot

import club.minnced.bot.command.onAvatar
import club.minnced.bot.command.onPing
import club.minnced.bot.command.onSoftban
import club.minnced.jda.reactor.ReactiveEventManager
import club.minnced.jda.reactor.on
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        error("Cannot start bot without a token!")
    }

    val manager = ReactiveEventManager()
    val jda = JDABuilder(args[0])
        .setEventManager(manager)
        .setActivity(Activity.listening("for clues"))
        .build()

    // Handle general commands
    jda.on<MessageReceivedEvent>()
        .filter { !it.author.isBot }
        .filter { it.message.contentRaw.startsWith("--") }
        .filter { it.author.asTag == "Minn#6688" }
        .subscribe(::onBasicCommand)

    // Handle guild-only commands
    jda.on<GuildMessageReceivedEvent>()
        .filter { !it.author.isBot }
        .filter { it.message.contentRaw.startsWith("--") }
        .filter { it.author.asTag == "Minn#6688" }
        .subscribe(::onGuildCommand)
}

fun onBasicCommand(event: MessageReceivedEvent) {
    val content = event.message.contentRaw
    val parts = content.split(" ", limit = 2)
    val command = parts[0].substring(2).toLowerCase()
    when (command) {
        "ping" -> onPing(event.channel)
        "avatar" -> onAvatar(parts.getOrNull(1), event)
    }
}

fun onGuildCommand(event: GuildMessageReceivedEvent) {
    val content = event.message.contentRaw
    val parts = content.split(" ", limit = 2)
    val command = parts[0].substring(2).toLowerCase()
    when (command) {
        "softban" -> onSoftban(parts.getOrNull(1), event)
    }
}

