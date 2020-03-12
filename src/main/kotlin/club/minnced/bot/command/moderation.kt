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
@file:JvmName("Moderation")
package club.minnced.bot.command

import club.minnced.bot.awaitUnit
import club.minnced.bot.findUser
import club.minnced.jda.reactor.asFlux
import club.minnced.jda.reactor.asMono
import club.minnced.jda.reactor.toMono
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.mono
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.PermissionException
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import reactor.core.publisher.toFlux
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

// Ban and immediately unban a user
fun onSoftban(arg: String?, event: MessageReceivedEvent): Mono<*> {
    val channel = event.channel
    val guild = event.guild
    val selfMember = guild.selfMember
    if (arg == null) {
        // No arguments provided, who should we ban then?
        return channel.sendMessage("Usage: `--softban <user>`").asMono()
    }

    // member can't be null here since we don't allow webhook messages, thus we use null assertion
    if (!event.member!!.hasPermission(Permission.KICK_MEMBERS)) {
        // We only want authorized people to use this command.
        return channel.sendMessage("You lack the permission to kick members in this server!").asMono()
    }

    // Check that we have permissions for this
    if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
        return channel.sendMessage("I'm unable to ban members, please grant me permission to ban members!").asMono()
    }

    // Either the user is mentioned or we need to look them up
    val user = event.message.mentionedUsers
        .toFlux()
        .next() // take first mentioned user
        .switchIfEmpty { findUser(event.jda, arg) } // otherwise find the referenced user
        .map { it.id } // take the id (if available)

    return mono {
        val target = user.awaitFirstOrNull()
            ?: return@mono channel.sendMessage("Cannot find user!").awaitUnit()

        try {
            // Try to softban the user
            guild.ban(target, 1).awaitUnit()
            delay(300)
            guild.unban(target).awaitUnit()

            // Success
            channel.sendMessage("Softban conclueded.").awaitUnit()
        } catch (ex: PermissionException) {
            // We were missing permissions or something
            channel.sendMessage("Cannot ban this user!").awaitUnit()
        }
    }
}

// Remove messages of a user from the current channel
fun onPurge(arg: String?, event: MessageReceivedEvent): Mono<*> {
    val channel = event.textChannel
    if (arg == null) {
        // No arguments provided, who should we purge then?
        return channel.sendMessage("Usage: `--purge <user>`").asMono()
    }

    // member can't be null here since we don't allow webhook messages, thus we use null assertion
    if (!event.member!!.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
        // We only want authorized people to use this command.
        return channel.sendMessage("You lack the permission to delete messages in this channel!").asMono()
    }

    // Check that we have permissions for this
    if (!event.guild.selfMember.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
        return channel.sendMessage("I'm unable to delete messages, please allow me to manage messages!").asMono()
    }

    val user = event.message.mentionedUsers.firstOrNull().toMono().switchIfEmpty { findUser(event.jda, arg) }

    return mono {
        val target = user.awaitFirstOrNull()
            ?: return@mono channel.sendMessage("Unknown user!").awaitUnit()

        // Use asFlux() to iterate the entire paginated endpoint rather than toFlux()
        // toFlux() would only request the first 100 messages in this case since no limit() is applied
        //  we don't want to be limited by that though since we use a time limitation not an amount threshold
        channel.iterableHistory.asFlux()
            // Only go back 30 days, anything above that would be overkill and take too long
            .takeWhile { it.timeCreated.until(OffsetDateTime.now(), ChronoUnit.DAYS) < 30 }
            // Filer by author
            .filter { it.author == target }
            // Take list of 100 messages each
            .buffer(100)
            // Delete the messages
            .flatMap { channel.purgeMessages(it).asFlux() }
            .next()
            // Wait for it to be done
            .awaitFirstOrNull()

        // We are done!
        channel.sendMessage("Finished!").awaitUnit()
    }
}
