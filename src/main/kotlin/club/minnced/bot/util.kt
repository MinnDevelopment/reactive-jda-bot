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
@file:JvmName("Util")
package club.minnced.bot

import club.minnced.jda.reactor.asMono
import club.minnced.jda.reactor.toMono
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.GuildMessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.requests.RestAction
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import reactor.core.publisher.toFlux
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

val NUMERICAL = Regex("\\d+")
val DISCORD_TAG = Regex("\\w+?#\\d{4}")

fun GuildMessageChannel.checkWrite() = canTalk(guild.selfMember)

suspend fun RestAction<*>.awaitUnit() = suspendCoroutine<Unit> { continuation ->
    queue(
        { continuation.resume(Unit) },
        { continuation.resumeWithException(it) }
    )
}

fun findUser(jda: JDA, arg: String): Mono<User> {
    return Mono.defer {
            when {
                // Check id if numerical
                arg.matches(NUMERICAL) -> jda.retrieveUserById(arg).asMono()
                // Check discord tag if proper format
                arg.matches(DISCORD_TAG) -> jda.getUserByTag(arg).toMono()
                // Empty for alternate case
                else -> Mono.empty()
            }
        }
        // Error indicates lookup failure, return empty mono instead
        .onErrorResume { Mono.empty() }
        // Check by name otherwise
        .switchIfEmpty { jda.getUsersByName(arg, true).toFlux().next() }
}
