package pos.application

/**
 * Паттерн GoF «Фасад» (структурный): единая точка входа для консоли.
 * UI не знает про пять сервисов и порядок их вызова; составные сценарии («карточка студента»)
 * собираются здесь, а не размазываются по командам консоли.
 */
class PosFacade(
    private val students: StudentService,
    private val rubric: RubricService,
    private val evaluations: EvaluationService,
    private val grades: GradeService,
) {
    fun addStudent(name: String) = students.add(name)
    fun listStudents() = students.list()
    fun removeStudent(id: String) = students.remove(id)

    fun addCriterion(name: String, maxScore: Double, weight: Double) = rubric.addCriterion(name, maxScore, weight)
    fun listCriteria() = rubric.list()
    fun removeCriterion(id: String) = rubric.removeCriterion(id)

    fun rate(studentId: String, criterionId: String, score: Double, comment: String?) =
        evaluations.rate(studentId, criterionId, score, comment)

    fun correct(studentId: String, criterionId: String, score: Double, comment: String?) =
        evaluations.correct(studentId, criterionId, score, comment)

    fun cancel(studentId: String, criterionId: String) = evaluations.cancel(studentId, criterionId)
    fun evaluationsOf(studentId: String) = evaluations.listFor(studentId)

    fun gradeOf(studentId: String, policy: String? = null) = grades.gradeOf(studentId, policy)
    fun gradeAll(policy: String? = null) = grades.gradeAll(policy)
    fun policies() = grades.availablePolicies
    fun formats() = grades.availableFormats
    fun export(format: String, target: String, policy: String? = null) = grades.export(format, target, policy)

    /** Составной сценарий: студент + все его оценки + итог — одним вызовом. */
    fun studentCard(studentId: String, policy: String? = null): StudentCard {
        val grade = grades.gradeOf(studentId, policy)
        return StudentCard(StudentView(grade.studentId, grade.studentName), evaluations.listFor(studentId), grade)
    }
}
