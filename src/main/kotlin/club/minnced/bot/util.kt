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
@file:JvmName("Util")
package club.minnced.bot

import club.minnced.jda.reactor.asMono
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import reactor.core.publisher.toFlux
import reactor.core.publisher.toMono

val NUMERICAL = Regex("\\d+")
val DISCORD_TAG = Regex("\\w+?#\\d{4}")

fun TextChannel.checkWrite() = guild.selfMember.hasPermission(this, Permission.MESSAGE_WRITE)

fun findUser(jda: JDA, arg: String): Mono<User> {
    return Mono.defer {
            when {
                // Check id if numerical
                arg.matches(NUMERICAL) -> jda.retrieveUserById(arg).asMono()
                // Check discord tag if proper format
                arg.matches(DISCORD_TAG) -> jda.getUserByTag(arg)?.toMono() ?: Mono.empty()
                // Empty for alternate case
                else -> Mono.empty()
            }
        }
        // Error indicates lookup failure, return empty mono instead
        .onErrorResume { Mono.empty() }
        // Check by name otherwise
        .switchIfEmpty { jda.getUsersByName(arg, true).toFlux().next() }
}