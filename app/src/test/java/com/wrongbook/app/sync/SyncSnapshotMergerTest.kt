package com.wrongbook.app.sync

import com.wrongbook.app.model.Question
import com.wrongbook.app.model.FollowUpChat
import com.wrongbook.app.model.FollowUpChatIds
import com.wrongbook.app.model.ImageRef
import com.wrongbook.app.model.ReviewEvent
import com.wrongbook.app.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSnapshotMergerTest {
    @Test
    fun `valid empty snapshot removes unchanged local collection`() {
        val base = listOf(question("q1"))
        val outcome = SyncSnapshotMerger.merge(base, base, emptyList())
        assertTrue(outcome.questions.isEmpty())
        assertEquals(1, outcome.removedLocalRecords)
    }

    @Test
    fun `record absent from complete snapshot cannot be revived by concurrent ordinary edit`() {
        val base = question("q1", title = "旧副本")
        val current = base.copy(
            title = "离线设备刚编辑",
            contentUpdatedAt = 300,
            updatedAt = 300,
            syncStatus = SyncStatus.MODIFIED
        )

        val outcome = SyncSnapshotMerger.merge(listOf(base), listOf(current), emptyList())
        assertTrue(outcome.questions.isEmpty())
        assertEquals(0, outcome.replayedLocalChanges)
    }

    @Test
    fun `record absent from complete snapshot can return only through explicit restore`() {
        val base = question("q1", title = "保留的完整内容").copy(
            deleted = true,
            deletedAt = 500,
            updatedAt = 500
        )
        val current = base.copy(
            deleted = false,
            restoredAt = 600,
            updatedAt = 600,
            syncStatus = SyncStatus.MODIFIED
        )

        val outcome = SyncSnapshotMerger.merge(listOf(base), listOf(current), emptyList())
        assertEquals("保留的完整内容", outcome.questions.single().title)
        assertEquals(1, outcome.replayedLocalChanges)
    }

    @Test
    fun `local edit during sync is replayed over independent remote notes`() {
        val base = question("q1", title = "旧标题", notes = "旧笔记")
        val current = base.copy(
            title = "本地新标题",
            contentUpdatedAt = 200,
            updatedAt = 200,
            syncStatus = SyncStatus.MODIFIED
        )
        val remote = base.copy(
            notes = "远端新笔记",
            notesUpdatedAt = 300,
            updatedAt = 300,
            syncStatus = SyncStatus.SYNCED
        )

        val outcome = SyncSnapshotMerger.merge(listOf(base), listOf(current), listOf(remote))
        val merged = outcome.questions.single()
        assertEquals("本地新标题", merged.title)
        assertEquals("远端新笔记", merged.notes)
        assertEquals(SyncStatus.MODIFIED, merged.syncStatus)
        assertEquals(1, outcome.replayedLocalChanges)
    }

    @Test
    fun `remote tombstone wins over ordinary concurrent activity`() {
        val base = question("q1")
        val current = base.copy(title = "同步期间编辑", updatedAt = 400, contentUpdatedAt = 400)
        val remote = base.copy(deleted = true, deletedAt = 500, updatedAt = 500)

        val merged = SyncSnapshotMerger.merge(listOf(base), listOf(current), listOf(remote))
            .questions.single()
        assertTrue(merged.deleted)
        assertEquals(500L, merged.deletedAt)
    }

    @Test
    fun `explicit restore newer than deletion is preserved`() {
        val base = question("q1").copy(deleted = true, deletedAt = 500, updatedAt = 500)
        val current = base.copy(
            deleted = false,
            restoredAt = 600,
            updatedAt = 600,
            syncStatus = SyncStatus.MODIFIED
        )
        val remote = base.copy(syncStatus = SyncStatus.SYNCED)

        val merged = SyncSnapshotMerger.merge(listOf(base), listOf(current), listOf(remote))
            .questions.single()
        assertFalse(merged.deleted)
        assertEquals(600L, merged.restoredAt)
        assertEquals(SyncStatus.MODIFIED, merged.syncStatus)
    }

    @Test
    fun `explicit restore replays full local payload over compact remote tombstone`() {
        val image = ImageRef(id = "img-local", uri = "content://local/image")
        val base = question("q1", title = "完整题目", notes = "重要笔记").copy(
            questionText = "不能丢失的题干",
            imageRefs = listOf(image),
            deleted = true,
            deletedAt = 500,
            updatedAt = 500
        )
        val current = base.copy(
            deleted = false,
            restoredAt = 600,
            updatedAt = 600,
            syncStatus = SyncStatus.MODIFIED
        )
        val compactRemote = question("q1", title = "已删除题目").copy(
            questionText = "",
            notes = "",
            imageRefs = emptyList(),
            deleted = true,
            deletedAt = 500,
            updatedAt = 500,
            syncStatus = SyncStatus.SYNCED
        )

        val merged = SyncSnapshotMerger.merge(
            listOf(base),
            listOf(current),
            listOf(compactRemote)
        ).questions.single()

        assertFalse(merged.deleted)
        assertEquals("完整题目", merged.title)
        assertEquals("不能丢失的题干", merged.questionText)
        assertEquals("重要笔记", merged.notes)
        assertEquals(listOf(image), merged.imageRefs)
    }

    @Test
    fun `remote and local review events are unioned instead of max count`() {
        val base = question("q1")
        val remoteEvent = review("remote", 200)
        val localEvent = review("local", 210)
        val current = base.copy(
            reviewEvents = listOf(localEvent),
            reviewCount = 1,
            reviewUpdatedAt = 210,
            updatedAt = 210,
            syncStatus = SyncStatus.MODIFIED
        )
        val remote = base.copy(
            reviewEvents = listOf(remoteEvent),
            reviewCount = 1,
            reviewUpdatedAt = 200,
            updatedAt = 200,
            syncStatus = SyncStatus.SYNCED
        )

        val merged = SyncSnapshotMerger.merge(listOf(base), listOf(current), listOf(remote))
            .questions.single()
        assertEquals(setOf("local", "remote"), merged.reviewEvents.map { it.id }.toSet())
        assertEquals(2, merged.reviewCount)
    }

    @Test
    fun `postpone newer than all merged review events keeps its next review time`() {
        val first = review("first", 100)
        val base = question("q1").copy(
            reviewEvents = listOf(first),
            reviewCount = 1,
            reviewUpdatedAt = 100,
            nextReviewAt = 200
        )
        val current = base.copy(
            reviewUpdatedAt = 400,
            nextReviewAt = 900,
            updatedAt = 400,
            syncStatus = SyncStatus.MODIFIED
        )
        val remote = base.copy(
            reviewEvents = listOf(first, review("remote", 300)),
            reviewCount = 2,
            reviewUpdatedAt = 300,
            updatedAt = 300,
            syncStatus = SyncStatus.SYNCED
        )

        val merged = SyncSnapshotMerger.merge(listOf(base), listOf(current), listOf(remote))
            .questions.single()
        assertEquals(2, merged.reviewCount)
        assertEquals(900L, merged.nextReviewAt)
        assertEquals(400L, merged.reviewUpdatedAt)
    }

    @Test
    fun `stable legacy follow up is not duplicated while replaying a local edit`() {
        val legacyFollowUp = FollowUpChat(
            id = FollowUpChatIds.legacyId(
                questionId = "q1",
                role = "user",
                content = "Legacy message",
                createdAt = 120L,
                sourceIndex = 0
            ),
            role = "user",
            content = "Legacy message",
            createdAt = 120L
        )
        val base = question("q1").copy(followUpChats = listOf(legacyFollowUp))
        val current = base.copy(
            title = "Local title edit",
            contentUpdatedAt = 200L,
            updatedAt = 200L,
            syncStatus = SyncStatus.MODIFIED
        )
        val remote = base.copy(
            updatedAt = 150L,
            syncStatus = SyncStatus.SYNCED
        )

        val merged = SyncSnapshotMerger.merge(listOf(base), listOf(current), listOf(remote))
            .questions.single()

        assertEquals(listOf(legacyFollowUp), merged.followUpChats)
    }

    private fun question(
        id: String,
        title: String = "题目",
        notes: String = ""
    ) = Question(
        id = id,
        title = title,
        notes = notes,
        createdAt = 100,
        updatedAt = 100,
        contentUpdatedAt = 100
    )

    private fun review(id: String, at: Long) = ReviewEvent(
        id = id,
        kind = ReviewEvent.KIND_REVIEW,
        reviewedAt = at,
        quality = 2
    )
}
