package pos

import pos.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

class DomainTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC)
    private val factory = EvaluationFactory(clock)
    private val student = Student(StudentId("s1"), "Иванов")

    private fun rubric() = Rubric().also {
        it.addCriterion("A", 10.0, 1.0)
        it.addCriterion("B", 20.0, 3.0)
    }

    private fun Rubric.id(name: String) = criteria.first { it.name == name }.id

    // ---- инварианты ----

    @Test fun `score above criterion max is rejected`() {
        val r = rubric()
        assertFailsWith<DomainException> { factory.create(student, r, r.id("A"), 12.0, null) }
    }

    @Test fun `negative score is rejected`() {
        val r = rubric()
        assertFailsWith<DomainException> { factory.create(student, r, r.id("A"), -1.0, null) }
    }

    @Test fun `score equal to max is accepted`() {
        val r = rubric()
        assertEquals(10.0, factory.create(student, r, r.id("A"), 10.0, null).score.value)
    }

    @Test fun `evaluation for criterion outside rubric is rejected`() {
        assertFailsWith<DomainException> { factory.create(student, rubric(), CriterionId("nope"), 5.0, null) }
    }

    @Test fun `reassess re-checks the range`() {
        val r = rubric()
        val e = factory.create(student, r, r.id("A"), 5.0, null)
        assertFailsWith<DomainException> { e.reassess(r.criteria.first(), 11.0, null, java.time.LocalDate.now(clock)) }
        assertEquals(5.0, e.score.value)
    }

    @Test fun `duplicate criterion name is rejected`() {
        assertFailsWith<DomainException> { rubric().addCriterion("a", 5.0, 1.0) }
    }

    @Test fun `weight must be positive`() {
        assertFailsWith<DomainException> { rubric().addCriterion("C", 5.0, 0.0) }
    }

    // ---- Domain Service + стратегии ----

    private fun evals(r: Rubric, a: Double?, b: Double?) = listOfNotNull(
        a?.let { factory.create(student, r, r.id("A"), it, null) },
        b?.let { factory.create(student, r, r.id("B"), it, null) },
    )

    @Test fun `full policy weights scores and counts missing as zero`() {
        val r = rubric()
        val calc = FinalGradeCalculator()
        // (1*1.0 + 3*0.5) / 4 * 10 = 6.25
        assertEquals(6.25, calc.calculate(r, evals(r, 10.0, 10.0), FullRubricPolicy()).value, 1e-9)
        // (1*1.0) / 4 * 10 = 2.5
        assertEquals(2.5, calc.calculate(r, evals(r, 10.0, null), FullRubricPolicy()).value, 1e-9)
    }

    @Test fun `rated-only policy ignores unrated criteria`() {
        val r = rubric()
        assertEquals(10.0, FinalGradeCalculator().calculate(r, evals(r, 10.0, null), RatedOnlyPolicy()).value, 1e-9)
    }

    @Test fun `rounding decorator wraps any policy`() {
        val r = rubric()
        val rounded = RoundingPolicyDecorator(FullRubricPolicy(), 1)
        assertEquals(6.3, FinalGradeCalculator().calculate(r, evals(r, 10.0, 10.0), rounded).value, 1e-9)
        assertEquals("full-rounded", rounded.key)
    }

    @Test fun `grade without evaluations is zero and never leaves 0 to 10`() {
        val r = rubric()
        assertEquals(0.0, FinalGradeCalculator().calculate(r, emptyList(), FullRubricPolicy()).value)
        assertEquals(10.0, FinalGradeCalculator().calculate(r, evals(r, 10.0, 20.0), FullRubricPolicy()).value, 1e-9)
    }
}
