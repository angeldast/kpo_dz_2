package pos.infrastructure

import pos.application.GradeExporter
import pos.application.GradeReportRow
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Паттерн GoF «Шаблонный метод» (поведенческий): каркас экспорта (подготовить путь → отрендерить → записать)
 * общий, а формат — только [render]. Новый формат = один маленький подкласс.
 */
abstract class FileGradeExporter : GradeExporter {
    protected abstract fun render(rows: List<GradeReportRow>): String

    final override fun export(rows: List<GradeReportRow>, target: String) {
        val path = Path.of(target)
        path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        Files.writeString(path, render(rows))
    }

    protected fun num(v: Double): String = String.format(Locale.ROOT, "%.2f", v)
}

class CsvGradeExporter : FileGradeExporter() {
    override val format = "csv"

    override fun render(rows: List<GradeReportRow>): String = buildString {
        appendLine("student_id,student_name,grade,rated,criteria")
        rows.forEach { appendLine("${it.studentId},${quote(it.studentName)},${num(it.grade)},${it.ratedCount},${it.criteriaCount}") }
    }

    private fun quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
}

class JsonGradeExporter : FileGradeExporter() {
    override val format = "json"

    override fun render(rows: List<GradeReportRow>): String =
        rows.joinToString(",\n", "[\n", "\n]\n") {
            "  {\"studentId\": \"${esc(it.studentId)}\", \"studentName\": \"${esc(it.studentName)}\", " +
                "\"grade\": ${num(it.grade)}, \"rated\": ${it.ratedCount}, \"criteria\": ${it.criteriaCount}}"
        }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
