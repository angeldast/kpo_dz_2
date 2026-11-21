package pos.application

/** Строка ведомости для экспорта (DTO на границе приложения). */
data class GradeReportRow(
    val studentId: String,
    val studentName: String,
    val grade: Double,
    val ratedCount: Int,
    val criteriaCount: Int,
)

/** Порт экспорта: приложение не знает ни про CSV, ни про JSON, ни про файловую систему. */
interface GradeExporter {
    val format: String
    fun export(rows: List<GradeReportRow>, target: String)
}

// ---------- Views (то, что приложение отдаёт наружу) ----------

data class StudentView(val id: String, val name: String)
data class CriterionView(val id: String, val name: String, val maxScore: Double, val weight: Double)
data class EvaluationView(
    val studentId: String,
    val criterionId: String,
    val criterionName: String,
    val score: Double,
    val maxScore: Double,
    val comment: String?,
    val date: String,
)
data class GradeView(val studentId: String, val studentName: String, val grade: Double, val ratedCount: Int, val criteriaCount: Int)
data class StudentCard(val student: StudentView, val evaluations: List<EvaluationView>, val grade: GradeView)
