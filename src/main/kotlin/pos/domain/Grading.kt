package pos.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Паттерн GoF «Стратегия» (поведенческий): правило подсчёта итога сменяемо.
 * Сам расчёт делает Domain Service [FinalGradeCalculator], а правило подставляется снаружи.
 */
interface GradingPolicy {
    val key: String
    val description: String
    fun calculate(rubric: Rubric, evaluations: Collection<Evaluation>): FinalGrade
}

/** Общая формула: Σ(вес × балл/макс) / Σ(вес) × 10 по переданным критериям. Оценки «чужих» критериев игнорируются. */
private fun weightedTen(criteria: List<Criterion>, evaluations: Collection<Evaluation>): FinalGrade {
    val scores = evaluations.associate { it.criterionId to it.score }
    val totalWeight = criteria.sumOf { it.weight.value }
    if (totalWeight == 0.0) return FinalGrade(0.0)
    val acc = criteria.sumOf { c -> scores[c.id]?.let { c.normalized(it) * c.weight.value } ?: 0.0 }
    return FinalGrade((acc / totalWeight * FinalGrade.MAX).coerceIn(0.0, FinalGrade.MAX))
}

/** Несдавшее/неоценённое считается нулём: итог «честный» по всему рубрикатору. */
class FullRubricPolicy : GradingPolicy {
    override val key = "full"
    override val description = "по всему рубрикатору, отсутствующая оценка = 0"
    override fun calculate(rubric: Rubric, evaluations: Collection<Evaluation>) =
        weightedTen(rubric.criteria, evaluations)
}

/** Промежуточный итог: учитываются только критерии, по которым уже есть оценка. */
class RatedOnlyPolicy : GradingPolicy {
    override val key = "rated"
    override val description = "только по уже оценённым критериям (промежуточный итог)"
    override fun calculate(rubric: Rubric, evaluations: Collection<Evaluation>): FinalGrade {
        val rated = evaluations.map { it.criterionId }.toSet()
        return weightedTen(rubric.criteria.filter { it.id in rated }, evaluations)
    }
}

/**
 * Паттерн GoF «Декоратор» (структурный): добавляет округление поверх любой политики,
 * не меняя её код. Так комбинируются «правило подсчёта» × «особенность подсчёта».
 */
class RoundingPolicyDecorator(private val inner: GradingPolicy, private val decimals: Int = 1) : GradingPolicy {
    override val key = "${inner.key}-rounded"
    override val description = "${inner.description}; округление до $decimals зн."
    override fun calculate(rubric: Rubric, evaluations: Collection<Evaluation>): FinalGrade {
        val raw = inner.calculate(rubric, evaluations).value
        return FinalGrade(BigDecimal.valueOf(raw).setScale(decimals, RoundingMode.HALF_UP).toDouble())
    }
}

/**
 * Domain Service: правило «итог согласован с текущим набором оценок» не принадлежит
 * ни студенту, ни оценке, ни рубрикатору по отдельности — оно строится на всех трёх.
 */
class FinalGradeCalculator {
    fun calculate(rubric: Rubric, evaluations: Collection<Evaluation>, policy: GradingPolicy): FinalGrade =
        policy.calculate(rubric, evaluations)
}
