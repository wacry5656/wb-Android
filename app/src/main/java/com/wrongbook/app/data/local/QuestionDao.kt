package com.wrongbook.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {

    @Query("SELECT * FROM questions WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun getAllActive(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions ORDER BY updatedAt DESC")
    suspend fun getAllRawOnce(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE deleted = 0 AND category = :category ORDER BY updatedAt DESC")
    fun getByCategory(category: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE deleted = 0 AND title LIKE '%' || :keyword || '%' ORDER BY updatedAt DESC")
    fun searchByTitle(keyword: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE id = :id AND deleted = 0")
    suspend fun getById(id: String): QuestionEntity?

    @Query("SELECT * FROM questions WHERE id = :id AND deleted = 0")
    fun getByIdFlow(id: String): Flow<QuestionEntity?>

    /** 底层原始读取，不过滤 deleted，仅供内部逻辑使用 */
    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getRawById(id: String): QuestionEntity?

    @Query("SELECT * FROM questions WHERE id = :id")
    fun getRawByIdFlow(id: String): Flow<QuestionEntity?>

    @Query("SELECT * FROM questions WHERE deleted = 0 AND nextReviewAt <= :timestamp ORDER BY nextReviewAt ASC")
    fun getDueForReview(timestamp: Long): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE deleted = 0 AND nextReviewAt <= :timestamp ORDER BY nextReviewAt ASC")
    suspend fun getDueForReviewOnce(timestamp: Long): List<QuestionEntity>

    @Query("SELECT COUNT(*) FROM questions WHERE deleted = 0 AND nextReviewAt <= :timestamp")
    fun getDueForReviewCount(timestamp: Long): Flow<Int>

    @Query("SELECT * FROM questions WHERE deleted = 0 ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentlyAdded(limit: Int): Flow<List<QuestionEntity>>

    @Query("SELECT DISTINCT category FROM questions WHERE deleted = 0 AND category != '' ORDER BY category")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: QuestionEntity)

    @Update
    suspend fun update(entity: QuestionEntity)

    @Query("UPDATE questions SET deleted = 1, deletedAt = :deletedAt, updatedAt = :updatedAt, syncStatus = :syncStatus WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long, syncStatus: String)
}
