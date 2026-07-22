package com.freedomplay.app.domain.model

/** A titled shelf from the YouTube Music home/explore feed (e.g. "Listen again"). */
data class MusicSection(
    val title: String,
    val items: List<StreamItem>
)
