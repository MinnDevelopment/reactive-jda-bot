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
import club.minnced.jda.reactor.toMono
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import java.time.Duration
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

fun onSoftban(arg: String?, event: MessageReceivedEvent) {
    if (arg == null) {
        // No arguments provided, who should we ban then?
        return event.channel.sendMessage("Usage: `--softban <user>`").queue()
    }

    // member can't be null here since we don't allow webhook messages, thus we use null assertion
    if (!event.member!!.hasPermission(Permission.KICK_MEMBERS)) {
        // We only want authorized people to use this command.
        return event.channel.sendMessage("You lack the permission to kick members in this server!").queue()
    }

    // Check that we have permissions for this
    if (!event.guild.selfMember.hasPermission(Permission.BAN_MEMBERS)) {
        return event.channel.sendMessage("I'm unable to ban members, please grant me permission to ban members!").queue()
    }

    // Either the user is mentioned or we need to look them up
    val user = event.message.mentionedUsers.firstOrNull().toMono().switchIfEmpty { findUser(event.jda, arg) }

    // ban and delete recent messages, side-effect: store id for unban
    user.flatMap {
            // Remember the userId for unban
            // Use thenReturn(Unit) to make fake element for mapping
            Mono.zip(it.id.toMono(), event.guild.ban(it, 1).asMono().thenReturn(Unit))
        }
        // delay unban by 5 seconds
        .delayElement(Duration.ofSeconds(5))
        // unban the user again
        .flatMap { event.guild.unban(it[0] as String).asMono().thenReturn(Unit) }
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
    // Handle missing user
    user.switchIfEmpty {
            channel.sendMessage("Unknown user!").queue()
            Mono.empty()
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
        // Convert Flux<Message> to Mono<Void> representing the delete process
        .map { channel.purgeMessages(it) }
        .map { it.map { future -> Mono.fromFuture(future) } }
        .flatMapSequential { Flux.merge(it) }
        // Once we finished deleting messages, tell the user about it
        .doOnComplete { channel.sendMessage("Finished!").queue() }
        // Start pipeline
        .subscribe()
}