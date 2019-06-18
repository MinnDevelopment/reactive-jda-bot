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
    // JDA provides both ping for REST as well as Gateway
    val gatewayPing = channel.jda.gatewayPing
    channel.jda.restPing.asMono()
        .flatMap { channel.sendMessage("**Message Ping**: ${it}ms\n" +
                                       "**Gateway Ping**: ${gatewayPing}ms").asMono() }
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
    val mono: Mono<MessageEmbed> = Mono.create { sink ->
        val builder = EmbedBuilder()
        // Find the user
        var user = event.message.mentionedUsers.firstOrNull().toMono()
        user = when (arg) {
            null -> event.author.toMono()                           // no user-input => use caller
            else -> user.switchIfEmpty { findUser(event.jda, arg) } // user wasn't mentioned, check if we can find them though
        }

        // First handle the case that the user doesn't exist
        user.switchIfEmpty {
                builder.setColor(Color.RED)
                builder.setDescription("Unable to find user $arg")
                sink.success(builder.build())
                Mono.empty()
            }
            // Start pipeline and finish task
            .subscribe {
                builder.setAuthor(it.name)
                builder.setImage(it.effectiveAvatarUrl)
                sink.success(builder.build())
            }
    }
    // Consume the constructed embed
    mono.subscribe {
        event.channel.sendMessage(it).queue()
    }
}