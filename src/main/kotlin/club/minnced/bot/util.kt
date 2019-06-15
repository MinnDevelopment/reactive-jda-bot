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
import net.dv8tion.jda.api.entities.User
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import reactor.core.publisher.toMono

fun findUser(jda: JDA, arg: String): Mono<User> {
    // I'm ignoring the special case that the user has a # in their name or a number as their username because those people are stupid.
    return when {
        arg.matches(Regex("\\d+")) -> jda.retrieveUserById(arg).asMono().onErrorResume { Mono.empty() }
        arg.matches(Regex("\\w+?#\\d{4}")) -> jda.getUserByTag(arg)?.toMono() ?: Mono.empty()
        else -> jda.getUsersByName(arg, true).toFlux().next()
    }
}