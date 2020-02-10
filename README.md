
[jda-reactor]: https://github.com/MinnDevelopment/jda-reactor
[command]: https://github.com/MinnDevelopment/reactive-jda-bot/tree/master/src/main/kotlin/club/minnced/bot/command
[Mono]: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html
[coroutines for reactor]: https://github.com/Kotlin/kotlinx.coroutines/tree/master/reactive/kotlinx-coroutines-reactor

## Reactive JDA Bot

This is an example implementation for a discord bot written in [jda-reactor][].
Additionally, I used [coroutines for reactor][] to simplify code in some situations.

### Commands

You can find all command implementations in the functions present in the [command][] package.
I made heavy use of [Mono][] to illustrate how powerful reactive extensions can be.