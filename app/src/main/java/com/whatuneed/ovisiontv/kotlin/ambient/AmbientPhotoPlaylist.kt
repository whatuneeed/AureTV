package com.whatuneed.ovisiontv.kotlin.ambient

import kotlin.random.Random

/**
 * One shuffle per ambient session, like the Aerial playlist. Sequential
 * filename order would show the same first picture every evening.
 */
object AmbientPhotoPlaylist {
    fun <T> ordered(photos: List<T>, random: Random? = null): List<T> =
        if (photos.size < 2) photos else photos.shuffled(random ?: Random.Default)
}
