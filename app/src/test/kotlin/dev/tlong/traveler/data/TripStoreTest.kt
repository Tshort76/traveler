package dev.tlong.traveler.data

import androidx.test.core.app.ApplicationProvider
import dev.tlong.traveler.Fixtures
import dev.tlong.traveler.domain.CheckItem
import dev.tlong.traveler.domain.Checklist
import dev.tlong.traveler.domain.ChecklistTemplate
import dev.tlong.traveler.domain.WorkBlock
import dev.tlong.traveler.domain.WorkPlan
import dev.tlong.traveler.domain.Checklists
import dev.tlong.traveler.domain.Edits
import dev.tlong.traveler.domain.Export
import dev.tlong.traveler.domain.Merge
import dev.tlong.traveler.domain.normalized
import dev.tlong.traveler.model.TripJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TripStoreTest {
    private val db = TravelerDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private var now = 1_000L
    private val store = TripStore(db.trips()) { now }
    private val router = ImportRouter(store)

    private val checklist = Checklist(listOf(CheckItem("e", CheckItem.Kind.TODO, "Get an eSIM", "Weeks before", done = true), CheckItem("p", CheckItem.Kind.PACK, "Plug adapter")))

    @After fun close() = db.close()

    @Test
    fun `starter templates are added only when there are none`() = runBlocking {
        val templates = TemplateStore(db.templates())
        assertTrue(templates.seedIfEmpty(Checklists.starters))
        assertEquals(listOf("Every trip", "International"), templates.all().map { it.name })
        templates.delete("starter-every-trip")
        assertFalse(templates.seedIfEmpty(Checklists.starters))
        assertEquals(listOf("International"), templates.all().map { it.name })
    }

    @Test
    fun `opening the same file twice makes no duplicate`() = runBlocking {
        val text = Fixtures.text("iguazu-short.trip.json")
        val first = router.route(text, "a") as PendingImport.NewTrip
        store.importNew(first.trip)
        assertTrue(router.route(text, "again") is PendingImport.AlreadyImported)
    }

    @Test
    fun `reopening an imported file still reports what the assistant should fix`() = runBlocking {
        val text = Fixtures.text("minimal.trip.json")
        store.importNew((router.route(text, "a") as PendingImport.NewTrip).trip)
        val again = router.route(text, "again") as PendingImport.AlreadyImported
        assertEquals(1, again.check.forAssistant.size)
    }

    @Test
    fun `an edit survives reopening`() = runBlocking {
        store.importNew(Fixtures.iguazu)
        val stored = store.load("argentina-2026-11")!!
        store.saveLocal(Edits.setDayNote(stored.local, "2026-11-09", "My Monday"))
        val reopened = TripStore(db.trips()).load("argentina-2026-11")!!
        assertEquals("My Monday", reopened.local.days.first { it.date == "2026-11-09" }.note)
        assertEquals(Fixtures.iguazu.normalized(), reopened.base)
    }

    @Test
    fun `a revision import keeps a snapshot that restores the prior trip`() = runBlocking {
        store.importNew(Fixtures.iguazu)
        val edited = Edits.setDayNote(store.load("argentina-2026-11")!!.local, "2026-11-09", "Mine")
        store.saveLocal(edited)
        val pending = router.route(Fixtures.text("iguazu-short.r2.trip.json"), "r2") as PendingImport.Revision
        now = 2_000
        store.applyRevision(pending.plan.incoming, Merge.apply(pending.plan))
        assertEquals(2, store.load("argentina-2026-11")!!.row.baseRevision)

        val snap = store.snapshots("argentina-2026-11").first().single()
        store.restoreSnapshot(snap.id)
        val restored = store.load("argentina-2026-11")!!
        assertEquals(1, restored.row.baseRevision)
        assertEquals(edited, restored.local)
        assertEquals(2, store.snapshots("argentina-2026-11").first().size)
    }

    @Test
    fun `deleted trips are recoverable until purged`() = runBlocking {
        store.importNew(Fixtures.iguazu)
        store.moveToDeleted("argentina-2026-11")
        assertNotNull(store.summaries.first().single().deletedAt)
        store.undelete("argentina-2026-11")
        assertNull(store.summaries.first().single().deletedAt)
        store.moveToDeleted("argentina-2026-11")
        now += TripStore.DELETE_AFTER_MS - 1
        store.purgeExpired()
        assertEquals(1, store.summaries.first().size)
        now += 2
        store.purgeExpired()
        assertTrue(store.summaries.first().isEmpty())
    }

    @Test
    fun `my notes and checklist stay out of the trip file and survive a revision`() = runBlocking {
        store.importNew(Fixtures.iguazu)
        store.setNotes("argentina-2026-11", "- Bring the adapter")
        store.setChecklist("argentina-2026-11", checklist)
        val pending = ImportRouter(store).route(Fixtures.text("iguazu-short.r2.trip.json"), "r2") as PendingImport.Revision
        store.applyRevision(pending.plan.incoming, Merge.apply(pending.plan))
        val stored = store.load("argentina-2026-11")!!
        assertEquals("- Bring the adapter", stored.row.notes)
        assertEquals(checklist, TripStore.checklistOf(stored.row))
        val exported = TripJson.encode(Export.tripFile(stored.base, stored.local))
        assertFalse("Bring the adapter" in exported || "Get an eSIM" in exported)
    }

    @Test
    fun `a backup restores trips with their edits and merge base`() = runBlocking {
        store.importNew(Fixtures.iguazu)
        val (withCustom, id) = Edits.addCustom(store.load("argentina-2026-11")!!.local, "iguazu", "Laundry")
        store.saveLocal(withCustom)
        store.setNotes("argentina-2026-11", "- Bring the adapter")
        store.setChecklist("argentina-2026-11", checklist)
        val work = WorkPlan(mapOf("2026-11-10" to listOf(WorkBlock("15:00", "17:00"))))
        store.setWork("argentina-2026-11", work)
        val template = ChecklistTemplate("t1", "International", checklist.items)
        val backup = store.backupText("2026-10-03T00:00:00Z", listOf(template))

        val other = TravelerDatabase.inMemory(ApplicationProvider.getApplicationContext())
        val fresh = TripStore(other.trips())
        val pending = ImportRouter(fresh).route(backup, "backup") as PendingImport.Backup
        fresh.restore(pending.file.trips)
        val restored = fresh.load("argentina-2026-11")!!
        assertNotNull(restored.local.activities.firstOrNull { it.id == id })
        assertEquals(Fixtures.iguazu.normalized(), restored.base)
        assertEquals("- Bring the adapter", restored.row.notes)
        assertEquals(checklist, TripStore.checklistOf(restored.row))
        assertEquals(work, TripStore.workOf(restored.row))
        assertEquals(listOf(template), pending.file.templates)
        other.close()
    }

    @Test
    fun `save state reaches saved only after the write`() = runBlocking {
        store.importNew(Fixtures.iguazu)
        val session = TripSession(store.load("argentina-2026-11")!!, store, CoroutineScope(Dispatchers.IO))
        session.edit("note") { Edits.setDayNote(it, "2026-11-10", "x") }
        assertEquals(SaveState.Saving, session.saveState.value)
        session.saveState.first { it == SaveState.Saved }
        assertEquals("x", store.load("argentina-2026-11")!!.local.days.first { it.date == "2026-11-10" }.note)
        assertEquals("note", session.undo())
        assertNull(session.trip.value.days.first { it.date == "2026-11-10" }.userEdited.firstOrNull())
    }

    @Test
    fun `exporting clears the unexported flag until the next edit`() = runBlocking {
        store.importNew(Fixtures.iguazu)
        val session = TripSession(store.load("argentina-2026-11")!!, store, CoroutineScope(Dispatchers.IO))
        session.markExported()
        assertFalse(store.load("argentina-2026-11")!!.hasUnexportedChanges)
        session.edit("e") { Edits.setDayNote(it, "2026-11-10", "y") }
        session.saveState.first { it == SaveState.Saved }
        assertTrue(store.load("argentina-2026-11")!!.hasUnexportedChanges)
    }
}
