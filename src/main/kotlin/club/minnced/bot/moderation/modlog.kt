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

package club.minnced.bot.moderation

import club.minnced.jda.reactor.asFlux
import club.minnced.jda.reactor.asMono
import club.minnced.jda.reactor.toFluxLocked
import club.minnced.jda.reactor.toMono
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.audit.ActionType
import net.dv8tion.jda.api.audit.AuditLogEntry
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import reactor.util.function.component1
import reactor.util.function.component2
import java.awt.Color
import java.time.Duration
import java.time.Instant

const val HAMMER_EMOJI = "\uD83D\uDD28"
const val SURPRISED_EMOJI = "\uD83D\uDE2E"
const val BOOT_EMOJI = "\uD83D\uDC62"

fun onMemberBan(guild: Guild, user: User) {
    val userId = user.idLong

    // Create the embed structure
    val embed = createEmbed(HAMMER_EMOJI, user, ActionType.BAN)

    findAuditLog(guild, userId, ActionType.BAN)
         // Send the message, nested publishers
         .flatMap { sendMessage(it, embed) }
         // Start the pipeline
         .subscribe()
}

fun onMemberUnban(guild: Guild, user: User) {
    val userId = user.idLong

    // Create the embed structure
    val embed = createEmbed(SURPRISED_EMOJI, user, ActionType.UNBAN)

    findAuditLog(guild, userId, ActionType.UNBAN)
        // Send the message, nested publishers
        .flatMap { sendMessage(it, embed) }
        // Start the pipeline
        .subscribe()
}

fun onMemberKick(guild: Guild, user: User) {
    // You know the drill now
    val userId = user.idLong
    val embed = createEmbed(BOOT_EMOJI, user, ActionType.KICK)
    findAuditLog(guild, userId, ActionType.KICK)
        .flatMap { sendMessage(it, embed) }
        .subscribe()
}

private fun sendMessage(zip: Tuple2<TextChannel, AuditLogEntry>, embed: EmbedBuilder): Mono<Message> {
    val (channel, entry) = zip
    // Inject audit information, if available
    applyAuditEntry(embed, entry)
    // Send off the message
    return channel.sendMessage(embed.build()).asMono()
}

private fun applyAuditEntry(embed: EmbedBuilder, entry: AuditLogEntry) {
    embed.apply {
        if (entry.reason != null)
            setFooter("Reason: ${entry.reason}")

        val author = entry.user
        if (author != null)
            setAuthor(author.name, null, author.effectiveAvatarUrl)
    }
}

private fun createEmbed(emoji: String, user: User, type: ActionType): EmbedBuilder {
    return EmbedBuilder().apply {
        setDescription("$emoji ")
        appendDescription(when (type) {
            ActionType.BAN -> "Banned "
            ActionType.UNBAN -> "Unbanned "
            ActionType.KICK -> "Kicked "
            else -> throw IllegalArgumentException("Unhandled type")
        })
        appendDescription("**${user.asTag}** (${user.id})")
        setTimestamp(Instant.now())
        setColor(when (type) {
            ActionType.BAN -> Color.RED
            ActionType.UNBAN -> Color.GREEN
            ActionType.KICK -> Color.ORANGE
            else -> null
        })
    }
}

private fun findAuditLog(guild: Guild, userId: Long, type: ActionType): Mono<Tuple2<TextChannel, AuditLogEntry>> {
    return guild.retrieveAuditLogs()
        // Filter only the specific type before starting flux
        .type(type)
        .asFlux()
        // Delay by 200 milliseconds to make sure the log entry exists
        .delaySubscription(Duration.ofMillis(200))
        // Only inspect entries for the right user
        .filter { it.targetIdLong == userId }
        // Take the first match
        .next()
        // Find mod-log channel
        .flatMap { Mono.zip(findModLog(guild), it.toMono()) }
}

private fun findModLog(guild: Guild): Mono<TextChannel> {
    //I am using toFluxLocked() here for better performance, this is ok because it will be completed right away
    //The flux returned will apply a read-lock on the text-channel cache once subscribe() is called and release it
    // again once the flux terminated (cancel/complete/error)
    return guild.textChannelCache.toFluxLocked()
        // If the channel has the name mod-log
        .filter { it.name == "mod-log" }
        // And we can send a message to it
        .filter { guild.selfMember.hasPermission(it, Permission.MESSAGE_WRITE) }
        // Then use the first result as our mod-log
        .next()
}
