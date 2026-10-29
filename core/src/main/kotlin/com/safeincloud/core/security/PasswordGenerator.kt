package com.safeincloud.core.security

import java.security.SecureRandom

data class GeneratorOptions(
    val length: Int = 20,
    val lowercase: Boolean = true,
    val uppercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = true,
    val requireEach: Boolean = true
)

object PasswordGenerator {
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val LOWER_FULL = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val UPPER_FULL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val DIGITS_FULL = "0123456789"
    private const val SYMBOLS = "!@#%^&*()-_=+[]{};:,.?"
    private const val AMBIGUOUS = "O0Il1|`'\""

    private val random = SecureRandom()

    fun generate(options: GeneratorOptions): String {
        val lower = if (options.excludeAmbiguous) LOWER else LOWER_FULL
        val upper = if (options.excludeAmbiguous) UPPER else UPPER_FULL
        val digits = if (options.excludeAmbiguous) DIGITS else DIGITS_FULL
        val symbols = if (options.excludeAmbiguous) SYMBOLS else SYMBOLS + AMBIGUOUS

        val active = buildList {
            if (options.lowercase) add(lower)
            if (options.uppercase) add(upper)
            if (options.digits) add(digits)
            if (options.symbols) add(symbols)
        }.ifEmpty { listOf(lower) }

        var length = options.length.coerceIn(4, 128)
        if (options.requireEach && length < active.size) length = active.size
        val all = active.joinToString("")
        val chars = StringBuilder(length)

        if (options.requireEach) {
            active.forEach { chars.append(it[random.nextInt(it.length)]) }
        }
        while (chars.length < length) {
            chars.append(all[random.nextInt(all.length)])
        }

        val array = chars.toString().toCharArray()
        for (i in array.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = array[i]
            array[i] = array[j]
            array[j] = tmp
        }
        return String(array)
    }

    fun passphrase(
        wordCount: Int = 4,
        separator: String = "-",
        capitalize: Boolean = true,
        includeNumber: Boolean = true
    ): String {
        val count = wordCount.coerceIn(3, 8)
        val pool = words.toMutableList()
        java.util.Collections.shuffle(pool, random)
        val words = pool.take(count).map {
            if (capitalize) it.replaceFirstChar { c -> c.uppercase() } else it
        }.toMutableList()
        if (includeNumber) {
            words.add(random.nextInt(10, 100).toString())
        }
        return words.joinToString(separator)
    }

    private val words = listOf(
        "apple", "arrow", "atlas", "autumn", "banjo", "beach", "berry", "birch",
        "blade", "bloom", "breeze", "bridge", "brook", "cabin", "candle", "cedar",
        "chalk", "cherry", "cloud", "clover", "coast", "comet", "coral", "crane",
        "creek", "crown", "dawn", "delta", "dove", "dune", "eagle", "earth",
        "ember", "fable", "fern", "field", "flame", "flint", "forest", "frost",
        "garden", "glade", "glass", "globe", "gold", "grain", "grape", "grove",
        "harbor", "hazel", "heron", "hill", "honey", "island", "ivory", "jade",
        "jasmine", "juniper", "kelp", "lake", "lantern", "leaf", "lemon", "lily",
        "linen", "lotus", "lunar", "maple", "marble", "meadow", "melon", "mist",
        "moon", "moss", "mountain", "nest", "night", "north", "oak", "ocean",
        "olive", "onyx", "opal", "orange", "orchid", "otter", "pearl", "pebble",
        "pine", "plum", "pond", "poppy", "prairie", "quartz", "rain", "raven",
        "reed", "ridge", "river", "robin", "rose", "sail", "sand", "scarlet",
        "sea", "shell", "silver", "sky", "snow", "solar", "sparrow", "spring",
        "spruce", "star", "stone", "storm", "stream", "summer", "sun", "swan",
        "thistle", "tide", "timber", "topaz", "trail", "tulip", "valley", "violet",
        "wave", "willow", "wind", "winter", "wolf", "wood", "wren", "zephyr"
    ).map { it.trim().lowercase() }
}
