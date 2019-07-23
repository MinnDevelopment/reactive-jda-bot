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
@file:JvmName("Basic")
package club.minnced.bot.command

import club.minnced.bot.findUser
import club.minnced.jda.reactor.asMono
import club.minnced.jda.reactor.onMessage
import club.minnced.jda.reactor.toMono
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import java.awt.Color
import java.util.concurrent.ThreadLocalRandom

fun onPing(channel: MessageChannel) {
    // Send message for the message ping (in the meantime get the rest ping)
    Mono.zip(channel.sendMessage("Calculating...").asMono().elapsed(),
             channel.jda.restPing.asMono())
        .flatMap {
            // the elapsed() gave us the time between subscribe() and next() signals
            val messagePing = it.t1.t1
            // we need the message again to edit it with the times
            val message = it.t1.t2
            // we retrieved the rest ping in the meantime (getting the user from the api)
            val restPing = it.t2
            // the gateway ping is provided by jda
            val gatewayPing = channel.jda.gatewayPing
            message.editMessage(
                       "**Message Ping**: ${messagePing}ms\n" +
                       "**Gateway Ping**: ${gatewayPing}ms\n" +
                       "**Rest Ping**: ${restPing}ms")
                   .asMono()
        }
        .subscribe()
}

fun onRTT(channel: MessageChannel) {
    val time = System.currentTimeMillis()
    val nonce = ThreadLocalRandom.current().nextLong().toString()

    // Register listener for nonce
    channel.onMessage()
        .map { it.message }
        .filter { it.nonce == nonce }
        .next() // Convert Flux<Message> to Mono<Message> representing first element
        .subscribe { it.editMessage("RTT: ${System.currentTimeMillis() - time}ms").queue() }

    // Send message to listen to
    channel.sendMessage("Calculating...").nonce(nonce).queue()
}

fun onAvatar(arg: String?, event: MessageReceivedEvent) {
    val mono: Mono<MessageEmbed> = Mono.defer {
        val builder = EmbedBuilder()
        // Find the user
        var user = event.message.mentionedUsers.firstOrNull().toMono()
        user = when (arg) {
            null -> event.author.toMono()                           // no user-input => use caller
            else -> user.switchIfEmpty { findUser(event.jda, arg) } // user wasn't mentioned, check if we can find them though
        }

        user.map {
                builder.setAuthor(it.name)
                builder.setImage(it.effectiveAvatarUrl)
                builder.build()
            }
            // handle the case that the user doesn't exist
            .switchIfEmpty {
                builder.setColor(Color.RED)
                builder.setDescription("Unable to find user $arg")
                builder.build().toMono()
            }

    }
    // Consume the constructed embed
    mono.subscribe {
        event.channel.sendMessage(it).queue()
    }
}