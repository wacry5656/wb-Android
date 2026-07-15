package com.wrongbook.app.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest

class ImageFileStoreTest {
    @Test
    fun `remote bytes repair an existing corrupt hash-named file`() {
        val dir = Files.createTempDirectory("wrongbook-image-test").toFile()
        try {
            val goodBytes = "valid-image-bytes".toByteArray()
            val expectedHash = sha256(goodBytes)
            val file = dir.resolve("image_img-${expectedHash.take(32)}_${expectedHash.take(20)}.jpg")
            file.writeBytes("corrupt".toByteArray())

            assertTrue(ImageFileStore.repairFileIfNeeded(file, goodBytes, expectedHash))
            assertArrayEquals(goodBytes, file.readBytes())
            assertFalse(ImageFileStore.repairFileIfNeeded(file, goodBytes, expectedHash))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `orphan cleanup respects retained paths and one hour cutoff`() {
        val dir = Files.createTempDirectory("wrongbook-orphan-test").toFile()
        try {
            val oldOrphan = dir.resolve("old.jpg").apply {
                writeText("old")
                setLastModified(1_000L)
            }
            val recentPending = dir.resolve("pending.jpg").apply {
                writeText("pending")
                setLastModified(9_000L)
            }
            val retained = dir.resolve("retained.jpg").apply {
                writeText("retained")
                setLastModified(1_000L)
            }

            assertTrue(ImageFileStore.shouldDeleteOrphan(oldOrphan, setOf(retained.absolutePath), 5_000L))
            assertFalse(ImageFileStore.shouldDeleteOrphan(recentPending, setOf(retained.absolutePath), 5_000L))
            assertFalse(ImageFileStore.shouldDeleteOrphan(retained, setOf(retained.absolutePath), 5_000L))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
