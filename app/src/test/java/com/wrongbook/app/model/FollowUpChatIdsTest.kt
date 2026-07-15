package com.wrongbook.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FollowUpChatIdsTest {

    @Test
    fun `legacy id matches the cross platform UTF-8 vector`() {
        val id = FollowUpChatIds.legacyId(
            questionId = "question-跨端-01",
            role = "用户",
            content = "这道题为什么要先移项？",
            createdAt = 1_712_345_678_901L,
            sourceIndex = 3
        )

        assertEquals(
            "legacy-followup-9419bc3bb888bc3cb57ddc49dc74bb8e6db72e5d4819bf5500efbbcf383b68fc",
            id
        )
    }
}
