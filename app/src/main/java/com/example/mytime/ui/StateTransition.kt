package com.example.mytime.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** The transform is pure; only the effect paired with a committed state is returned. */
internal inline fun <S, E> MutableStateFlow<S>.updateAndGetEffect(transform: (S) -> Pair<S, E>): E {
    while (true) {
        val previous = value
        val (next, effect) = transform(previous)
        if (compareAndSet(previous, next)) return effect
    }
}
