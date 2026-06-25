package com.myhealthtracker.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MealImageStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun touch(dir: File, name: String): File =
        File(dir, name).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test
    fun `sweepOrphans deletes files not referenced by any meal`() {
        val dir = tmp.newFolder("meal_images")
        val keep = touch(dir, "a.jpg")
        val orphan = touch(dir, "b.jpg")
        val deleted = MealImageStore.sweepOrphans(dir, setOf(keep.absolutePath))
        assertEquals(1, deleted)
        assertTrue(keep.exists())
        assertFalse(orphan.exists())
    }

    @Test
    fun `sweepOrphans on empty references deletes all`() {
        val dir = tmp.newFolder("meal_images")
        touch(dir, "a.jpg"); touch(dir, "b.jpg")
        assertEquals(2, MealImageStore.sweepOrphans(dir, emptySet()))
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun `sweepOrphans is a no-op when dir missing`() {
        assertEquals(0, MealImageStore.sweepOrphans(File(tmp.root, "nope"), emptySet()))
    }

    @Test
    fun `delete removes the file and tolerates null`() {
        val dir = tmp.newFolder("meal_images")
        val f = touch(dir, "x.jpg")
        MealImageStore.delete(null)
        MealImageStore.delete(f.absolutePath)
        assertFalse(f.exists())
    }
}
