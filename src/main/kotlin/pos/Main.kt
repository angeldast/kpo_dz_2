package pos

import pos.application.*
import pos.domain.*
import pos.infrastructure.*
import java.io.PrintStream
import java.time.Clock

/** Composition root: единственное место, где порты связываются с реализациями. */
fun buildContainer(): DiContainer = DiContainer().apply {
    // Инфраструктура (адаптеры) регистрируется под типами портов
    singleton<StudentRepository> { InMemoryStudentRepository() }
    singleton<RubricRepository> { InMemoryRubricRepository() }
    singleton<EvaluationRepository> { InMemoryEvaluationRepository() }
    singleton<Clock> { Clock.systemDefaultZone() }

    // Домен
    singleton { EvaluationFactory(get()) }
    singleton { FinalGradeCalculator() }

    // Приложение
    singleton { StudentService(get(), get()) }
    singleton { RubricService(get(), get()) }
    singleton { EvaluationService(get(), get(), get(), get(), get()) }
    singleton {
        GradeService(
            students = get(), rubrics = get(), evaluations = get(), calculator = get(),
            policies = listOf(
                FullRubricPolicy(),
                RatedOnlyPolicy(),
                RoundingPolicyDecorator(FullRubricPolicy()),
                RoundingPolicyDecorator(RatedOnlyPolicy()),
            ),
            exporters = listOf(CsvGradeExporter(), JsonGradeExporter()),
            defaultPolicyKey = "full",
        )
    }
    singleton { PosFacade(get(), get(), get(), get()) }
    singleton { ConsoleUi(get()) }
}

fun main(args: Array<String>) {
    System.setOut(PrintStream(System.out, true, "UTF-8"))
    val ui = buildContainer().get<ConsoleUi>()
    if (args.firstOrNull() == "--demo") ui.execute("demo") else ui.run()
}
