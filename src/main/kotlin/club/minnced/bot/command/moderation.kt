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
@file:JvmName("Moderation")
package club.minnced.bot.command

import club.minnced.bot.findUser
import club.minnced.jda.reactor.asFlux
import club.minnced.jda.reactor.asMono
import club.minnced.jda.reactor.then
import club.minnced.jda.reactor.toMono
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import reactor.core.publisher.toFlux
import java.time.Duration
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

fun onSoftban(arg: String?, event: MessageReceivedEvent) {
    val channel = event.channel
    val selfMember = event.guild.selfMember
    if (arg == null) {
        // No arguments provided, who should we ban then?
        return channel.sendMessage("Usage: `--softban <user>`").queue()
    }

    // member can't be null here since we don't allow webhook messages, thus we use null assertion
    if (!event.member!!.hasPermission(Permission.KICK_MEMBERS)) {
        // We only want authorized people to use this command.
        return channel.sendMessage("You lack the permission to kick members in this server!").queue()
    }

    // Check that we have permissions for this
    if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
        return channel.sendMessage("I'm unable to ban members, please grant me permission to ban members!").queue()
    }

    // Either the user is mentioned or we need to look them up
    val user = event.message.mentionedUsers
        .toFlux()
        .next() // take first mentioned user
        .switchIfEmpty { findUser(event.jda, arg) } // otherwise find the referenced user
        .map { it.id } // take the id (if available)

    // ban and delete recent messages
    user.flatMap {
            // Use thenReturn(it) to keep the id for the unban
            event.guild.ban(it, 1).asMono().thenReturn(it)
        }
        // delay unban by 300 milliseconds
        .delayElement(Duration.ofMillis(300))
        // unban the user again, return the id again for error handling
        .flatMap { event.guild.unban(it).asMono().thenReturn(it) }
        // Ignore errors
        .onErrorResume { Mono.empty() }
        // Check if the ban worked
        .hasElement()
        .flatMap {
            if (it) // confirm
                channel.sendMessage("Softban concluded.").asMono()
            else    // empty -> reject due to missing user or error
                channel.sendMessage("Cannot softban this user!").asMono()
        }
        // Start pipeline
        .subscribe()
}

fun onPurge(arg: String?, event: MessageReceivedEvent) {
    val channel = event.textChannel
    if (arg == null) {
        // No arguments provided, who should we purge then?
        return channel.sendMessage("Usage: `--purge <user>`").queue()
    }

    // member can't be null here since we don't allow webhook messages, thus we use null assertion
    if (!event.member!!.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
        // We only want authorized people to use this command.
        return channel.sendMessage("You lack the permission to delete messages in this channel!").queue()
    }

    // Check that we have permissions for this
    if (!event.guild.selfMember.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
        return channel.sendMessage("I'm unable to delete messages, please allow me to manage messages!").queue()
    }

    val user = event.message.mentionedUsers.firstOrNull().toMono().switchIfEmpty { findUser(event.jda, arg) }
    user.switchIfEmpty {
            // Handle missing user
            channel.sendMessage("Unknown user!").asMono().then(Mono.empty())
        }
        // Map to Flux<Message> for the channel message history
        .flatMapMany { target ->
            // Use asFlux() to iterate the entire paginated endpoint rather than toFlux()
            // toFlux() would only request the first 100 messages in this case since no limit() is applied
            //  we don't want to be limited by that though since we use a time limitation not an amount threshold
            channel.iterableHistory.asFlux().filter { it.author == target }
        }
        // Only go back 30 days, anything above that would be overkill and take too long
        .takeWhile {
            // Limited by 30 days into the past
            it.timeCreated.until(OffsetDateTime.now(), ChronoUnit.DAYS) < 30
        }
        // Take list of 100 messages each
        .buffer(100)
        // Tell the user that we collected the messages and started purging
        .doOnComplete { channel.sendMessage("Working on it...").queue() }
        // Convert Flux<Message> to Flux<Void> representing the delete process
        .flatMap { channel.purgeMessages(it).asFlux() }
        // Continue when one future has an error, this could just be that the message is gone already (not an issue)
        .onErrorContinue { error, _ -> error.printStackTrace() }
        // Use then() to hook to the termination signal as Mono<Message> to tell the user we are done
        .then { channel.sendMessage("Finished!").asMono() }
        // Start pipeline
        .subscribe()
}
