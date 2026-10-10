package io.github.wgd753.naturecollect.data

/** Broad groups used for filter chips and recognition output. */
enum class Category(val label: String, val emoji: String) {
    BIRD("鸟类", "🐦"),
    FLOWER("花卉", "🌸"),
    PLANT("植物", "🌿"),
    INSECT("昆虫", "🐞"),
    ANIMAL("动物", "🐾"),
    OTHER("其他", "✨");

    companion object {
        fun fromKey(key: String?): Category =
            entries.firstOrNull { it.name.equals(key?.trim(), ignoreCase = true) } ?: OTHER
    }
}
