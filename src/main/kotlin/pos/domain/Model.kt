package pos.domain

import java.time.LocalDate
import java.util.UUID

/** Нарушение правила предметной области. */
class DomainException(message: String) : RuntimeException(message)

// ---------- Идентификаторы (Value Objects) ----------

@JvmInline
value class StudentId(val value: String) {
    override fun toString() = value

    companion object {
        fun generate() = StudentId(UUID.randomUUID().toString().take(8))
    }
}

@JvmInline
value class CriterionId(val value: String) {
    override fun toString() = value

    companion object {
        fun generate() = CriterionId(UUID.randomUUID().toString().take(8))
    }
}

// ---------- Value Objects ----------

/** Балл. Сам по себе неотрицателен; верхняя граница зависит от критерия и проверяется в [Criterion.scoreOf]. */
data class Score(val value: Double) {
    init {
        if (!value.isFinite() || value < 0) throw DomainException("Балл должен быть неотрицательным числом, получено: $value")
    }
}

/** Вклад критерия в итог. Строго положителен. */
data class Weight(val value: Double) {
    init {
        if (!value.isFinite() || value <= 0) throw DomainException("Вес должен быть положительным числом, получено: $value")
    }
}

/** Итог по студенту по шкале 0..10. Не «цифра наугад», а результат расчёта (см. [FinalGradeCalculator]). */
data class FinalGrade(val value: Double) {
    init {
        if (value.isNaN() || value < 0.0 || value > MAX) throw DomainException("Итог вне шкалы 0..$MAX: $value")
    }

    companion object {
        const val MAX = 10.0
    }
}

// ---------- Entities ----------

/** Студент — тот, кого оценивают. Идентичность по [id], имя можно менять. */
class Student(val id: StudentId, name: String) {
    var name: String = requireName(name)
        private set

    fun rename(newName: String) {
        name = requireName(newName)
    }

    override fun equals(other: Any?) = other is Student && other.id == id
    override fun hashCode() = id.hashCode()

    private fun requireName(n: String): String {
        if (n.isBlank()) throw DomainException("Имя студента не может быть пустым")
        return n.trim()
    }
}

/**
 * Критерий: за что ставят балл. Сущность внутри агрегата [Rubric] (у неё есть идентичность —
 * на неё ссылаются оценки), и ей принадлежит инвариант «балл ∈ [0, maxScore]».
 */
class Criterion(val id: CriterionId, name: String, val maxScore: Double, val weight: Weight) {
    val name: String = name.trim()

    init {
        if (this.name.isEmpty()) throw DomainException("Название критерия не может быть пустым")
        if (!maxScore.isFinite() || maxScore <= 0) throw DomainException("max_score должен быть положительным, получено: $maxScore")
    }

    /** Единственный способ получить [Score] для этого критерия — тут охраняется верхняя граница. */
    fun scoreOf(value: Double): Score {
        val score = Score(value)
        if (value > maxScore) throw DomainException("Балл $value выше максимума $maxScore по критерию «$name»")
        return score
    }

    /** Доля набранного: 0..1. */
    fun normalized(score: Score): Double = score.value / maxScore

    override fun equals(other: Any?) = other is Criterion && other.id == id
    override fun hashCode() = id.hashCode()
}

/**
 * Рубрикатор — корень агрегата, владеет критериями. Добавление/удаление критериев идёт только через него,
 * поэтому уникальность названий и существование критерия проверяются в одном месте.
 */
class Rubric {
    private val items = linkedMapOf<CriterionId, Criterion>()

    val criteria: List<Criterion> get() = items.values.toList()

    fun addCriterion(name: String, maxScore: Double, weight: Double): Criterion {
        if (items.values.any { it.name.equals(name.trim(), ignoreCase = true) }) {
            throw DomainException("Критерий «${name.trim()}» уже есть в рубрикаторе")
        }
        val criterion = Criterion(CriterionId.generate(), name, maxScore, Weight(weight))
        items[criterion.id] = criterion
        return criterion
    }

    fun removeCriterion(id: CriterionId) {
        requireCriterion(id)
        items.remove(id)
    }

    /** Инвариант: оценивать можно только критерий из рубрикатора. */
    fun requireCriterion(id: CriterionId): Criterion =
        items[id] ?: throw DomainException("Критерия $id нет в рубрикаторе")

    fun contains(id: CriterionId) = id in items
}

/**
 * Оценка — факт «студенту S по критерию C поставили балл». Идентичность — пара (студент, критерий):
 * по критерию у студента не больше одной оценки. Конструктор internal: создавать оценку
 * можно только через [EvaluationFactory], которая проверяет инварианты.
 */
class Evaluation internal constructor(
    val studentId: StudentId,
    val criterionId: CriterionId,
    score: Score,
    comment: String?,
    ratedAt: LocalDate,
) {
    var score: Score = score
        private set
    var comment: String? = comment
        private set
    var ratedAt: LocalDate = ratedAt
        private set

    /** Исправление оценки; инвариант диапазона проверяется заново. */
    fun reassess(criterion: Criterion, newValue: Double, newComment: String?, on: LocalDate) {
        if (criterion.id != criterionId) throw DomainException("Оценка относится к другому критерию")
        score = criterion.scoreOf(newValue)
        comment = newComment
        ratedAt = on
    }
}
