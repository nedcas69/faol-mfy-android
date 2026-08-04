package uz.bekobod.faolmfy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PositionDao {

    @Insert
    suspend fun insert(p: PositionEntity): Long

    @Query("SELECT * FROM positions WHERE synced = 0 ORDER BY ts LIMIT :limit")
    suspend fun pending(limit: Int): List<PositionEntity>

    @Query("UPDATE positions SET synced = 1 WHERE clientSeq IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM positions WHERE synced = 0")
    fun pendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM positions WHERE synced = 0")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM positions WHERE ts >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT MAX(ts) FROM positions")
    suspend fun lastTs(): Long?

    @Query("SELECT * FROM positions WHERE ts = :ts LIMIT 1")
    suspend fun lastPoint(ts: Long): PositionEntity?

    /** Sinxronlangan va 48 soatdan eski yozuvlar o'chiriladi (TZ F5.7). */
    @Query("DELETE FROM positions WHERE synced = 1 AND ts < :before")
    suspend fun purgeSynced(before: Long): Int

    /** Xavfsizlik chegarasi: 14 kundan eski yuborilmagan yozuvlar ham o'chadi. */
    @Query("DELETE FROM positions WHERE ts < :before")
    suspend fun purgeAll(before: Long): Int
}

@Dao
interface DeviceEventDao {

    @Insert
    suspend fun insert(e: DeviceEventEntity): Long

    @Query("SELECT * FROM device_events WHERE synced = 0 ORDER BY ts LIMIT :limit")
    suspend fun pending(limit: Int): List<DeviceEventEntity>

    @Query("UPDATE device_events SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM device_events WHERE synced = 1 AND ts < :before")
    suspend fun purge(before: Long): Int
}

@Dao
interface PhotoQueueDao {

    @Insert
    suspend fun insert(p: PhotoQueueEntity): Long

    @Update
    suspend fun update(p: PhotoQueueEntity)

    @Query("SELECT * FROM photo_queue WHERE uploaded = 0 AND attempts < 20 ORDER BY takenAt LIMIT :limit")
    suspend fun pending(limit: Int = 10): List<PhotoQueueEntity>

    @Query("SELECT * FROM photo_queue WHERE takenAt >= :from AND takenAt <= :to ORDER BY takenAt")
    suspend fun inRange(from: Long, to: Long): List<PhotoQueueEntity>

    @Query("SELECT * FROM photo_queue WHERE takenAt >= :from AND takenAt <= :to ORDER BY takenAt")
    fun inRangeFlow(from: Long, to: Long): Flow<List<PhotoQueueEntity>>

    @Query("SELECT COUNT(*) FROM photo_queue WHERE uploaded = 0")
    fun pendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM photo_queue WHERE uploaded = 1 AND takenAt < :before")
    suspend fun uploadedBefore(before: Long): List<PhotoQueueEntity>

    @Query("DELETE FROM photo_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface NoteQueueDao {

    @Upsert
    suspend fun upsert(n: NoteQueueEntity)

    @Query("SELECT * FROM note_queue WHERE synced = 0 LIMIT :limit")
    suspend fun pending(limit: Int = 50): List<NoteQueueEntity>

    @Query("UPDATE note_queue SET synced = 1 WHERE anchorTs IN (:anchors)")
    suspend fun markSynced(anchors: List<Long>)

    @Query("SELECT * FROM note_queue WHERE anchorTs = :anchor")
    suspend fun byAnchor(anchor: Long): NoteQueueEntity?

    @Query("SELECT COUNT(*) FROM note_queue WHERE synced = 0")
    fun pendingCountFlow(): Flow<Int>

    @Query("DELETE FROM note_queue WHERE synced = 1 AND updatedAt < :before")
    suspend fun purge(before: Long)
}
