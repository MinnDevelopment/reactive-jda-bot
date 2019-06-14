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

package club.minnced.bot.command

import club.minnced.bot.findUser
import club.minnced.jda.reactor.asMono
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import reactor.core.publisher.toMono
import java.time.Duration

fun onSoftban(arg: String?, event: GuildMessageReceivedEvent) {
    if (arg == null) {
        // No arguments provided, who should we ban then?
        return event.channel.sendMessage("Usage: `--softban <user>`").queue()
    }

    // member can't be null here since we don't allow webhook messages, thus we use null assertion
    if (!event.member!!.hasPermission(Permission.KICK_MEMBERS)) {
        // We only want authorized people to use this command.
        return event.channel.sendMessage("You lack the permission to kick members in this server!").queue()
    }

    // Either the user is mentioned or we need to look them up
    val user = event.message.mentionedUsers.firstOrNull()?.toMono() ?: findUser(event.jda, arg)
    // Temporarily safe userId (this creates an indirect reference object)
    var userId: String? = null
    // thenReturn is used to allow flatMap to work, asMono() is just Mono<Void?> which will never provide a value
    // instead we use thenReturn(Unit) which always returns the same object for convenience

    // ban and delete recent messages, side-effect: store id for unban
    user.flatMap { userId = it.id; event.guild.ban(it, 1).asMono().thenReturn(Unit) }
        // delay unban by 5 seconds
        .delayElement(Duration.ofSeconds(5))
        // unban the user again
        .flatMap { event.guild.unban(userId!!).asMono().thenReturn(Unit) }
        // send message to channel, we have completed our job
        .flatMap { event.channel.sendMessage("Softban concluded.").asMono() }
        // Ignore errors
        .onErrorResume { Mono.empty() }
        // If the user doesn't exist, tell them it failed
        .switchIfEmpty {
            event.channel.sendMessage("Cannot softban user!").queue()
            Mono.empty()
        }
        // Start pipeline
        .subscribe()
}