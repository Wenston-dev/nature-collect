package io.github.wgd753.naturecollect.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * A collection category. The six built-in ones can be renamed or recoloured
 * and the user can add their own; all of them are offered to DeepSeek when it
 * sorts a new capture. Captures store only the [key].
 */
data class Category(
    val key: String,
    val label: String,
    val emoji: String,
    val color: Long,
    /** Optional note telling the model what belongs here. */
    val hint: String? = null,
) : java.io.Serializable {
    /** Kept so existing code that stored `category.name` still reads naturally. */
    val name: String get() = key

    /** Label to show: built-in names follow the app language until the user renames them. */
    val display: String
        get() {
            if (!io.github.wgd753.naturecollect.ui.Lang.english) return label
            val i = defaults.indexOfFirst { it.key == key }
            return if (i >= 0 && defaults[i].label == label) EnglishLabels[i] else label
        }

    companion object {
        const val OTHER_KEY = "OTHER"

        val defaults = listOf(
            Category("BIRD", "鸟类", "🐦", 0xFF3D7DCA, "所有鸟"),
            Category("FLOWER", "花卉", "🌸", 0xFFE45A92, "以花为主体"),
            Category("PLANT", "植物", "🌿", 0xFF3E9B57, "树木、草、叶子、蘑菇、果实等"),
            Category("INSECT", "昆虫", "🐞", 0xFFE58A1F, "昆虫、蜘蛛等小虫子"),
            Category("ANIMAL", "动物", "🐾", 0xFF9C6B3E, "其他动物，包括猫狗、鱼、爬行动物"),
            Category(OTHER_KEY, "其他", "✨", 0xFF7A6FD8, "不属于以上任何一类，或不是生物"),
        )

        private val EnglishLabels = listOf("Birds", "Flowers", "Plants", "Insects", "Animals", "Other")

        // Snapshot state: composables reading the list recompose when the user edits it.
        private val state = mutableStateOf(defaults)

        /** Current categories in display order; "其他" is always present. */
        val entries: List<Category> get() = state.value

        internal fun replaceAll(list: List<Category>) {
            state.value = if (list.any { it.key == OTHER_KEY }) list else list + defaults.last()
        }

        /** Matches a stored key, or a label the model may have answered with; unknown → 其他. */
        fun fromKey(key: String?): Category {
            val k = key?.trim().orEmpty()
            val all = entries
            return all.firstOrNull { it.key.equals(k, ignoreCase = true) }
                ?: all.firstOrNull { it.label == k }
                ?: all.firstOrNull { it.key == OTHER_KEY }
                ?: defaults.last()
        }

        val OTHER: Category get() = fromKey(OTHER_KEY)
    }
}

/** Persists the user's categories as JSON in shared preferences. */
class CategoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("categories", Context.MODE_PRIVATE)

    init {
        prefs.getString(KEY, null)?.let { runCatching { Category.replaceAll(decode(JSONArray(it))) } }
    }

    fun save(list: List<Category>) {
        Category.replaceAll(list)
        prefs.edit().putString(KEY, encode(Category.entries).toString()).apply()
    }

    fun resetToDefaults() = save(Category.defaults)

    fun newKey(): String = "C" + System.currentTimeMillis().toString(36).uppercase()

    companion object {
        private const val KEY = "list"

        fun encode(list: List<Category>): JSONArray = JSONArray(
            list.map {
                JSONObject().put("key", it.key).put("label", it.label).put("emoji", it.emoji)
                    .put("color", it.color).put("hint", it.hint ?: JSONObject.NULL)
            },
        )

        fun decode(array: JSONArray): List<Category> = List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Category(
                key = o.getString("key"),
                label = o.getString("label"),
                emoji = o.optString("emoji", "✨"),
                color = o.getLong("color"),
                hint = if (o.isNull("hint")) null else o.optString("hint").ifBlank { null },
            )
        }
    }
}
