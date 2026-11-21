package pos.application

import pos.domain.*
import java.time.Clock
import java.time.LocalDate

private fun Student.toView() = StudentView(id.value, name)
private fun Criterion.toView() = CriterionView(id.value, name, maxScore, weight.value)

/** Сценарии «студенты». */
class StudentService(
    private val students: StudentRepository,
    private val evaluations: EvaluationRepository,
) {
    fun add(name: String): StudentView {
        val student = Student(StudentId.generate(), name)
        students.save(student)
        return student.toView()
    }

    fun list(): List<StudentView> = students.findAll().map { it.toView() }

    fun remove(id: String) {
        val sid = StudentId(id)
        requireStudent(students, sid)
        evaluations.deleteByStudent(sid)
        students.delete(sid)
    }
}

/** Сценарии «рубрикатор». */
class RubricService(
    private val rubrics: RubricRepository,
    private val evaluations: EvaluationRepository,
) {
    fun addCriterion(name: String, maxScore: Double, weight: Double): CriterionView {
        val rubric = rubrics.get()
        val criterion = rubric.addCriterion(name, maxScore, weight)
        rubrics.save(rubric)
        return criterion.toView()
    }

    fun list(): List<CriterionView> = rubrics.get().criteria.map { it.toView() }

    /** Удаление критерия каскадно убирает оценки по нему, иначе они ссылались бы на несуществующий критерий. */
    fun removeCriterion(id: String) {
        val rubric = rubrics.get()
        rubric.removeCriterion(CriterionId(id))
        rubrics.save(rubric)
        evaluations.deleteByCriterion(CriterionId(id))
    }
}

/** Сценарии «оценки»: выставить, исправить, отменить, посмотреть. */
class EvaluationService(
    private val students: StudentRepository,
    private val rubrics: RubricRepository,
    private val evaluations: EvaluationRepository,
    private val factory: EvaluationFactory,
    private val clock: Clock,
) {
    fun rate(studentId: String, criterionId: String, score: Double, comment: String?): EvaluationView {
        val student = requireStudent(students, StudentId(studentId))
        val cid = CriterionId(criterionId)
        if (evaluations.find(student.id, cid) != null) {
            throw DomainException("Оценка уже выставлена — используйте исправление")
        }
        val rubric = rubrics.get()
        val evaluation = factory.create(student, rubric, cid, score, comment)
        evaluations.save(evaluation)
        return evaluation.toView(rubric)
    }

    fun correct(studentId: String, criterionId: String, score: Double, comment: String?): EvaluationView {
        val student = requireStudent(students, StudentId(studentId))
        val cid = CriterionId(criterionId)
        val rubric = rubrics.get()
        val criterion = rubric.requireCriterion(cid)
        val evaluation = evaluations.find(student.id, cid) ?: throw DomainException("Оценки ещё нет — используйте выставление")
        evaluation.reassess(criterion, score, comment?.takeIf { it.isNotBlank() }, LocalDate.now(clock))
        evaluations.save(evaluation)
        return evaluation.toView(rubric)
    }

    fun cancel(studentId: String, criterionId: String) {
        val student = requireStudent(students, StudentId(studentId))
        val cid = CriterionId(criterionId)
        evaluations.find(student.id, cid) ?: throw DomainException("Такой оценки нет")
        evaluations.delete(student.id, cid)
    }

    fun listFor(studentId: String): List<EvaluationView> {
        val student = requireStudent(students, StudentId(studentId))
        val rubric = rubrics.get()
        return evaluations.findByStudent(student.id).filter { rubric.contains(it.criterionId) }.map { it.toView(rubric) }
    }
}

/** Сценарии «итог» и «экспорт». Правило подсчёта выбирается по ключу из реестра стратегий. */
class GradeService(
    private val students: StudentRepository,
    private val rubrics: RubricRepository,
    private val evaluations: EvaluationRepository,
    private val calculator: FinalGradeCalculator,
    policies: List<GradingPolicy>,
    exporters: List<GradeExporter>,
    private val defaultPolicyKey: String,
) {
    private val policies = policies.associateBy { it.key }
    private val exporters = exporters.associateBy { it.format }

    val availablePolicies: List<Pair<String, String>> get() = policies.values.map { it.key to it.description }
    val availableFormats: Set<String> get() = exporters.keys

    fun gradeOf(studentId: String, policyKey: String? = null): GradeView {
        val student = requireStudent(students, StudentId(studentId))
        return gradeFor(student, rubrics.get(), policy(policyKey))
    }

    fun gradeAll(policyKey: String? = null): List<GradeView> {
        val rubric = rubrics.get()
        val policy = policy(policyKey)
        return students.findAll().map { gradeFor(it, rubric, policy) }
    }

    fun export(format: String, target: String, policyKey: String? = null): Int {
        val exporter = exporters[format.lowercase()]
            ?: throw IllegalArgumentException("Неизвестный формат «$format». Доступно: ${exporters.keys.joinToString()}")
        val rows = gradeAll(policyKey).map { GradeReportRow(it.studentId, it.studentName, it.grade, it.ratedCount, it.criteriaCount) }
        exporter.export(rows, target)
        return rows.size
    }

    private fun policy(key: String?): GradingPolicy {
        val k = key ?: defaultPolicyKey
        return policies[k] ?: throw IllegalArgumentException("Неизвестная политика «$k». Доступно: ${policies.keys.joinToString()}")
    }

    private fun gradeFor(student: Student, rubric: Rubric, policy: GradingPolicy): GradeView {
        val evals = evaluations.findByStudent(student.id).filter { rubric.contains(it.criterionId) }
        val grade = calculator.calculate(rubric, evals, policy)
        return GradeView(student.id.value, student.name, grade.value, evals.size, rubric.criteria.size)
    }
}

private fun requireStudent(repo: StudentRepository, id: StudentId): Student =
    repo.findById(id) ?: throw DomainException("Студента $id не существует")

private fun Evaluation.toView(rubric: Rubric): EvaluationView {
    val c = rubric.requireCriterion(criterionId)
    return EvaluationView(studentId.value, criterionId.value, c.name, score.value, c.maxScore, comment, ratedAt.toString())
}
