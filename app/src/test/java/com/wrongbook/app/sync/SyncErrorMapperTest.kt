package com.wrongbook.app.sync

import org.junit.Assert.assertTrue
import org.junit.Test

class SyncErrorMapperTest {
    @Test
    fun `authentication errors are explicit`() {
        assertTrue(SyncErrorMapper.message(401, "").contains("SYNC_TOKEN"))
        assertTrue(SyncErrorMapper.message(403, "").contains("无权限"))
    }

    @Test
    fun `payload limit reports server maximum`() {
        val message = SyncErrorMapper.message(413, "{\"maxRequestBytes\":52428800}")
        assertTrue(message.contains("50.0 MB"))
        assertTrue(message.contains("压缩"))
    }

    @Test
    fun `other server errors retain status and short body`() {
        val message = SyncErrorMapper.message(500, "SYNC_FAILED")
        assertTrue(message.contains("500"))
        assertTrue(message.contains("SYNC_FAILED"))
    }
}
