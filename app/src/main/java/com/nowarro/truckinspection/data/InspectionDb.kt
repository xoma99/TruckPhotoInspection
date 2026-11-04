package com.nowarro.truckinspection.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "inspections")
data class Inspection(@PrimaryKey(autoGenerate = true) val id: Long = 0, val plate: String, val employee: String, val vehicle: String, val createdAt: Long = System.currentTimeMillis(), val completed: Boolean = false)

@Entity(tableName = "training_samples")
data class TrainingSample(
 @PrimaryKey(autoGenerate = true) val id: Long = 0,
 val taskId: String,
 val filePath: String,
 val sourceInspectionId: Long,
 val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "inspection_photos", primaryKeys = ["inspectionId", "taskId"])
data class InspectionPhoto(val inspectionId: Long, val taskId: String, val filePath: String, val capturedAt: Long = System.currentTimeMillis(), val manuallyApproved: Boolean = false, val qualityPassed: Boolean = true, val qualityScore: Int = 100, val qualityMessage: String = "Проверка не выполнялась", val detectedTaskId: String = "", val angleConfidence: Int = 0, val angleReliable: Boolean = false, val angleManuallyApproved: Boolean = false)

@Dao interface InspectionDao {
 @Insert suspend fun addTrainingSample(sample: TrainingSample): Long
 @Query("SELECT * FROM training_samples ORDER BY taskId, createdAt") suspend fun getTrainingSamples(): List<TrainingSample>
 @Query("SELECT COUNT(*) FROM training_samples") suspend fun countTrainingSamples(): Int
 @Insert suspend fun insertInspection(item: Inspection): Long
 @Update suspend fun updateInspection(item: Inspection)
 @Query("SELECT * FROM inspections ORDER BY createdAt DESC") suspend fun getInspections(): List<Inspection>
 @Query("SELECT * FROM inspections WHERE id = :id LIMIT 1") suspend fun getInspection(id: Long): Inspection?
 @Query("DELETE FROM inspections WHERE id = :id") suspend fun deleteInspection(id: Long)
 @Query("DELETE FROM inspection_photos WHERE inspectionId = :id") suspend fun deleteInspectionPhotos(id: Long)
 @Query("DELETE FROM inspection_photos WHERE inspectionId = :inspectionId AND taskId = :taskId") suspend fun deletePhoto(inspectionId: Long, taskId: String)
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePhoto(photo: InspectionPhoto)
 @Update suspend fun updatePhoto(photo: InspectionPhoto)
 @Query("SELECT * FROM inspection_photos WHERE inspectionId = :id") suspend fun getPhotos(id: Long): List<InspectionPhoto>
}

@Database(entities = [Inspection::class, InspectionPhoto::class, TrainingSample::class], version = 4, exportSchema = false)
abstract class InspectionDb : RoomDatabase() { abstract fun dao(): InspectionDao; companion object { @Volatile private var INSTANCE: InspectionDb? = null; fun get(context: Context) = INSTANCE ?: synchronized(this) { INSTANCE ?: Room.databaseBuilder(context.applicationContext, InspectionDb::class.java, "truck_inspections.db").fallbackToDestructiveMigration().build().also { INSTANCE = it } } } }
