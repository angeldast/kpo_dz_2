package pos.domain

import java.time.Clock
import java.time.LocalDate

/**
 * Паттерн GoF «Фабрика» (порождающий): единая точка создания [Evaluation].
 * Оценку нельзя получить в обход проверок: критерий должен быть в рубрикаторе, балл — в диапазоне критерия.
 */
class EvaluationFactory(private val clock: Clock) {
    fun create(student: Student, rubric: Rubric, criterionId: CriterionId, value: Double, comment: String?): Evaluation {
        val criterion = rubric.requireCriterion(criterionId)
        return Evaluation(student.id, criterion.id, criterion.scoreOf(value), comment?.takeIf { it.isNotBlank() }, LocalDate.now(clock))
    }
}
