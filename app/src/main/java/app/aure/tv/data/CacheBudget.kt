package app.aure.tv.data

/**
 * Capacity after a pressure trim. The cache must be resized, not only trimmed:
 * otherwise a refill returns to the old budget (AQ-15). A full trim keeps a
 * minimal non-zero capacity because LruCache rejects a zero max size.
 */
internal fun trimmedCapacity(max: Int, percent: Int): Int =
    if (percent >= 100) (max / 10).coerceAtLeast(1)
    else (max * (100 - percent) / 100).coerceAtLeast(1)
