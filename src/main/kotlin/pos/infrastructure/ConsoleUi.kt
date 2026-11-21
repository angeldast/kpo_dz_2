package pos.infrastructure

import pos.application.*
import pos.domain.DomainException
import java.util.Locale

/** Консольный адаптер. Знает только фасад и его view-объекты. */
class ConsoleUi(private val pos: PosFacade) {

    fun run() {
        println("ПОС — преподавательская оценка студентов. Команда help покажет список команд.")
        while (true) {
            print("pos> ")
            val line = readlnOrNull() ?: return
            if (line.trim() in setOf("exit", "quit")) return
            execute(line)
        }
    }

    fun execute(line: String) {
        val args = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (args.isEmpty()) return
        try {
            dispatch(args[0].lowercase(), args.drop(1))
        } catch (e: DomainException) {
            println("  ✗ Нарушено правило: ${e.message}")
        } catch (e: IllegalArgumentException) {
            println("  ✗ ${e.message}")
        } catch (e: Exception) {
            println("  ✗ Ошибка: ${e.message}")
        }
    }

    private fun dispatch(cmd: String, a: List<String>) {
        when (cmd) {
            "help" -> help()
            "add-student" -> need(a, 1, "add-student <имя...>").let { println("  ✓ Студент добавлен: ${pos.addStudent(a.joinToString(" ")).line()}") }
            "students" -> pos.listStudents().ifEmpty { println("  (пусто)"); emptyList() }.forEach { println("  ${it.line()}") }
            "remove-student" -> need(a, 1, "remove-student <id>").also { pos.removeStudent(a[0]); println("  ✓ Удалён вместе с оценками") }
            "add-criterion" -> {
                need(a, 3, "add-criterion <название...> <max_score> <вес>")
                val c = pos.addCriterion(a.dropLast(2).joinToString(" "), num(a[a.size - 2]), num(a.last()))
                println("  ✓ Критерий добавлен: ${c.line()}")
            }
            "criteria" -> pos.listCriteria().ifEmpty { println("  (пусто)"); emptyList() }.forEach { println("  ${it.line()}") }
            "remove-criterion" -> need(a, 1, "remove-criterion <id>").also { pos.removeCriterion(a[0]); println("  ✓ Удалён вместе с оценками по нему") }
            "rate" -> {
                need(a, 3, "rate <student_id> <criterion_id> <балл> [комментарий...]")
                println("  ✓ Выставлено: ${pos.rate(a[0], a[1], num(a[2]), a.drop(3).joinToString(" ")).line()}")
            }
            "correct" -> {
                need(a, 3, "correct <student_id> <criterion_id> <балл> [комментарий...]")
                println("  ✓ Исправлено: ${pos.correct(a[0], a[1], num(a[2]), a.drop(3).joinToString(" ")).line()}")
            }
            "cancel" -> need(a, 2, "cancel <student_id> <criterion_id>").also { pos.cancel(a[0], a[1]); println("  ✓ Оценка отменена") }
            "evaluations" -> {
                need(a, 1, "evaluations <student_id>")
                pos.evaluationsOf(a[0]).ifEmpty { println("  (оценок нет)"); emptyList() }.forEach { println("  ${it.line()}") }
            }
            "grade" -> {
                need(a, 1, "grade <student_id> [политика]")
                val card = pos.studentCard(a[0], a.getOrNull(1))
                println("  ${card.student.name}:")
                card.evaluations.forEach { println("    ${it.line()}") }
                println("  Итог: ${fmt(card.grade.grade)} / 10 (оценено ${card.grade.ratedCount} из ${card.grade.criteriaCount})")
            }
            "grades" -> pos.gradeAll(a.getOrNull(0)).forEach {
                println("  ${it.studentName.padEnd(22)} ${fmt(it.grade).padStart(5)} / 10   (${it.ratedCount}/${it.criteriaCount})")
            }
            "policies" -> pos.policies().forEach { println("  ${it.first.padEnd(14)} — ${it.second}") }
            "export" -> {
                need(a, 2, "export <${pos.formats().joinToString("|")}> <файл> [политика]")
                println("  ✓ Выгружено строк: ${pos.export(a[0], a[1], a.getOrNull(2))} → ${a[1]}")
            }
            "demo" -> demo()
            else -> println("  Неизвестная команда «$cmd». См. help.")
        }
    }

    /** Сквозной сценарий из ТЗ: студенты и критерии → оценки → итог → экспорт. Через тот же диспетчер команд. */
    private fun demo() {
        fun step(cmd: String) { println("pos> $cmd"); execute(cmd) }
        val ivan = pos.addStudent("Иванов Иван")
        val maria = pos.addStudent("Петрова Мария")
        val lab = pos.addCriterion("Лабораторные", 10.0, 2.0)
        val exam = pos.addCriterion("Экзамен", 20.0, 3.0)
        val activity = pos.addCriterion("Активность", 5.0, 1.0)
        println("Заведены студенты и рубрикатор:")
        step("students"); step("criteria")
        println("\nВыставляем оценки:")
        step("rate ${ivan.id} ${lab.id} 9 Отличная защита")
        step("rate ${ivan.id} ${exam.id} 16")
        step("rate ${ivan.id} ${activity.id} 4")
        step("rate ${maria.id} ${lab.id} 7")
        step("rate ${maria.id} ${exam.id} 12 Пересдавать не нужно")
        println("\nПопытки нарушить домен (должны отклоняться):")
        step("rate ${maria.id} ${activity.id} 12")
        step("rate ${maria.id} nosuch 5")
        step("rate ${maria.id} ${activity.id} -1")
        println("\nИсправление и отмена:")
        step("correct ${maria.id} ${lab.id} 8")
        step("cancel ${ivan.id} ${activity.id}")
        println("\nИтог по студенту (по умолчанию политика full):")
        step("grade ${ivan.id}")
        println("\nИтоги всех студентов под разными правилами:")
        step("grades full"); step("grades rated"); step("grades full-rounded")
        println("\nЭкспорт:")
        step("export csv out/grades.csv full-rounded")
        step("export json out/grades.json full-rounded")
    }

    private fun help() = println(
        """
        |  add-student <имя...>                  students            remove-student <id>
        |  add-criterion <название...> <max> <вес>   criteria        remove-criterion <id>
        |  rate|correct <sid> <cid> <балл> [комментарий...]          cancel <sid> <cid>
        |  evaluations <sid>
        |  grade <sid> [политика]               grades [политика]   policies
        |  export <csv|json> <файл> [политика]  demo                exit
        """.trimMargin()
    )

    private fun need(a: List<String>, n: Int, usage: String) { if (a.size < n) throw IllegalArgumentException("Использование: $usage") }
    private fun num(s: String) = s.replace(',', '.').toDoubleOrNull() ?: throw IllegalArgumentException("Не число: «$s»")
    private fun fmt(v: Double) = String.format(Locale.ROOT, "%.2f", v)
    private fun StudentView.line() = "[$id] $name"
    private fun CriterionView.line() = "[$id] $name (max $maxScore, вес $weight)"
    private fun EvaluationView.line() = "$criterionName: ${score.let { fmt(it) }}/${maxScore} (${date})" + (comment?.let { " — $it" } ?: "")
}
