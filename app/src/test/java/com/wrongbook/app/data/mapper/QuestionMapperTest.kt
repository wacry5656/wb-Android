package com.wrongbook.app.data.mapper

import com.wrongbook.app.model.FollowUpChatIds
import com.wrongbook.app.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionMapperTest {

    @Test
    fun `legacy follow ups without ids are stable across repeated reads`() {
        val entity = QuestionMapper.domainToEntity(
            Question(
                id = "question-legacy",
                title = "Legacy question",
                createdAt = 100L,
                updatedAt = 200L
            )
        ).copy(
            followUpChats = """[
                {"role":"user","content":"First message","createdAt":1234},
                {"role":"assistant","content":"Second message"}
            ]""".trimIndent()
        )

        val firstRead = QuestionMapper.entityToDomain(entity).followUpChats
        val secondRead = QuestionMapper.entityToDomain(entity).followUpChats

        assertEquals(firstRead, secondRead)
        assertEquals(2, firstRead.size)
        assertNotEquals(firstRead[0].id, firstRead[1].id)
        assertTrue(firstRead.all { it.id.startsWith("legacy-followup-") })
        assertEquals(100L, firstRead[1].createdAt)
        assertEquals(
            FollowUpChatIds.legacyId(
                questionId = "question-legacy",
                role = "user",
                content = "First message",
                createdAt = 1234L,
                sourceIndex = 0
            ),
            firstRead[0].id
        )
    }
}
