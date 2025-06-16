// RoutineRecordDao.kt
package com.example.myapp.model

import androidx.lifecycle.LiveData
import androidx.room.*
import java.time.LocalDate

// 루틴 기록을 관리하는 DAO
@Dao
interface RoutineRecordDao {
    @Query("SELECT * FROM routine_records WHERE routineId = :routineId ORDER BY date DESC, id DESC")
    fun getRecordsForRoutine(routineId: Int): LiveData<List<RoutineRecordEntity>>

    @Query("SELECT * FROM routine_records WHERE date = :date")
    fun getRecordsByDate(date: LocalDate): LiveData<List<RoutineRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RoutineRecordEntity): Long

    @Update
    suspend fun updateRecord(record: RoutineRecordEntity)

    @Delete
    suspend fun deleteRecord(record: RoutineRecordEntity)

    /** 특정 날짜에 수행된 루틴 조회 */
    @Query("SELECT * FROM routine_records WHERE routineId = :routineId AND date = :todayDate LIMIT 1")
    suspend fun getTodayRecord(routineId: Int, todayDate: LocalDate): RoutineRecordEntity?

    @Query("SELECT COUNT(*) FROM routine_records WHERE routineId = :routineId AND date = :todayDate")
    suspend fun getRecordCountForToday(routineId: Int, todayDate: LocalDate): Int
}
