package pos.domain

/** Порты хранения. Домен знает только эти интерфейсы; реализации живут в infrastructure. */
interface StudentRepository {
    fun save(student: Student)
    fun findById(id: StudentId): Student?
    fun findAll(): List<Student>
    fun delete(id: StudentId)
}

/** Рубрикатор один на курс — репозиторий хранит этот единственный агрегат. */
interface RubricRepository {
    fun get(): Rubric
    fun save(rubric: Rubric)
}

interface EvaluationRepository {
    fun save(evaluation: Evaluation)
    fun find(studentId: StudentId, criterionId: CriterionId): Evaluation?
    fun findByStudent(studentId: StudentId): List<Evaluation>
    fun findAll(): List<Evaluation>
    fun delete(studentId: StudentId, criterionId: CriterionId)
    fun deleteByStudent(studentId: StudentId)
    fun deleteByCriterion(criterionId: CriterionId)
}
