package com.apexvision.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.apexvision.app.model.Job

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs_table")
    suspend fun getAllJobs(): List<Job>

    @Query("SELECT * FROM jobs_table WHERE id = :jobId")
    suspend fun getJobById(jobId: Int): Job?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(jobs: List<Job>)

    @Update
    suspend fun updateJob(job: Job)
}