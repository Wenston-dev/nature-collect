package io.github.wgd753.naturecollect.ui.achievements

import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.data.groupBySpecies
import io.github.wgd753.naturecollect.ui.tr
import java.util.Calendar

/** Species milestones each nature category can reach. */
val SpeciesTiers = listOf(1, 5, 10, 25, 50, 100)

private const val ENCOUNTER_GOAL = 100

/** Everything except "其他" counts as nature, including categories the user added. */
val Category.isNature: Boolean get() = key != Category.OTHER_KEY

fun natureCategories(): List<Category> = Category.entries.filter { it.isNature }

/** Recognised species per category key; unnamed captures don't count. */
fun speciesCounts(captures: List<CaptureEntity>): Map<String, Int> =
    captures.groupBySpecies()
        .filter { !it.cover.nameZh.isNullOrBlank() }
        .groupingBy { it.category.key }
        .eachCount()

data class CategoryProgress(val category: Category, val species: Int) {
    val earned: Int get() = SpeciesTiers.count { species >= it }
    val next: Int? get() = SpeciesTiers.firstOrNull { it > species }

    /** Progress from the last tier reached toward the next one. */
    val fraction: Float
        get() {
            val target = next ?: return 1f
            val base = SpeciesTiers.lastOrNull { it <= species } ?: 0
            return ((species - base).toFloat() / (target - base)).coerceIn(0f, 1f)
        }
}

data class ExtraAchievement(val key: String, val title: String, val current: Int, val goal: Int) {
    val done: Boolean get() = current >= goal
}

data class AchievementSummary(val categories: List<CategoryProgress>, val extras: List<ExtraAchievement>) {
    val earned: Int get() = categories.sumOf { it.earned } + extras.count { it.done }
    val total: Int get() = categories.size * SpeciesTiers.size + extras.size
}

private fun season(millis: Long): Int {
    val month = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.MONTH) + 1
    return when (month) {
        in 3..5 -> 0
        in 6..8 -> 1
        in 9..11 -> 2
        else -> 3
    }
}

fun achievementSummary(captures: List<CaptureEntity>): AchievementSummary {
    val nature = natureCategories()
    val counts = speciesCounts(captures)
    val progress = nature.map { CategoryProgress(it, counts[it.key] ?: 0) }
    val natureKeys = nature.map { it.key }.toSet()
    val natureCaptures = captures.filter { Category.fromKey(it.category).key in natureKeys }
    val seasons = natureCaptures.map { season(it.createdAt) }.toSet().size
    val extras = listOf(
        ExtraAchievement("all", tr("每个自然分类都有收集", "Every nature category"), progress.count { it.species > 0 }, nature.size.coerceAtLeast(1)),
        ExtraAchievement("seasons", tr("四季", "Four seasons"), seasons, 4),
        ExtraAchievement("encounters", tr("第 100 次遇见", "100th encounter"), natureCaptures.size.coerceAtMost(ENCOUNTER_GOAL), ENCOUNTER_GOAL),
    )
    return AchievementSummary(progress, extras)
}

data class TierUnlock(val category: Category, val tier: Int)

/** The species tier this capture pushes its category across, if it is a new species. */
fun tierUnlockedBy(all: List<CaptureEntity>, capture: CaptureEntity): TierUnlock? {
    val category = Category.fromKey(capture.category)
    if (!category.isNature || capture.nameZh.isNullOrBlank()) return null
    val others = all.filter { it.id != capture.id }
    val before = speciesCounts(others)[category.key] ?: 0
    val after = speciesCounts(others + capture)[category.key] ?: 0
    if (after <= before) return null
    return SpeciesTiers.lastOrNull { it in (before + 1)..after }?.let { TierUnlock(category, it) }
}
