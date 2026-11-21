package pos.infrastructure

import pos.domain.*

class InMemoryStudentRepository : StudentRepository {
    private val data = linkedMapOf<StudentId, Student>()
    override fun save(student: Student) { data[student.id] = student }
    override fun findById(id: StudentId) = data[id]
    override fun findAll() = data.values.toList()
    override fun delete(id: StudentId) { data.remove(id) }
}

class InMemoryRubricRepository : RubricRepository {
    private var rubric = Rubric()
    override fun get() = rubric
    override fun save(rubric: Rubric) { this.rubric = rubric }
}

class InMemoryEvaluationRepository : EvaluationRepository {
    private val data = linkedMapOf<Pair<StudentId, CriterionId>, Evaluation>()
    override fun save(evaluation: Evaluation) { data[evaluation.studentId to evaluation.criterionId] = evaluation }
    override fun find(studentId: StudentId, criterionId: CriterionId) = data[studentId to criterionId]
    override fun findByStudent(studentId: StudentId) = data.values.filter { it.studentId == studentId }
    override fun findAll() = data.values.toList()
    override fun delete(studentId: StudentId, criterionId: CriterionId) { data.remove(studentId to criterionId) }
    override fun deleteByStudent(studentId: StudentId) { data.keys.removeAll { it.first == studentId } }
    override fun deleteByCriterion(criterionId: CriterionId) { data.keys.removeAll { it.second == criterionId } }
}
