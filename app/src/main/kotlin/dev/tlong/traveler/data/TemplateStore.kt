package dev.tlong.traveler.data

import dev.tlong.traveler.domain.ChecklistTemplate
import dev.tlong.traveler.model.TripJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The traveler's reusable checklists. A trip gets a copy of a template's items, never a link to it. */
class TemplateStore(private val dao: TemplateDao, private val clock: () -> Long = System::currentTimeMillis) {

    val templates: Flow<List<ChecklistTemplate>> = dao.observe().map { rows -> rows.mapNotNull(::decode) }

    suspend fun all(): List<ChecklistTemplate> = dao.all().mapNotNull(::decode)

    suspend fun save(t: ChecklistTemplate) =
        dao.upsert(TemplateRow(t.id, t.name, TripJson.compact.encodeToString(ChecklistTemplate.serializer(), t), clock()))

    suspend fun delete(id: String) = dao.delete(id)

    /** Adds [starters] when there are no templates yet; returns whether it did. */
    suspend fun seedIfEmpty(starters: List<ChecklistTemplate>): Boolean {
        if (dao.all().isNotEmpty()) return false
        starters.forEach { save(it) }
        return true
    }

    private fun decode(row: TemplateRow): ChecklistTemplate? =
        runCatching { TripJson.reader.decodeFromString(ChecklistTemplate.serializer(), row.json) }.getOrNull()
}
