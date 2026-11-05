package br.com.idiomas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "installed_packages", primaryKeys = ["ownerId","hash"])
data class InstalledPackage(val ownerId: Long, val hash: String, val packageId: String, val version: Int, val title: String, val preview: Boolean, val directory: String)
@Entity(tableName = "practice_sessions", primaryKeys = ["ownerId","id"])
data class PracticeSession(val ownerId: Long, val id: String, val packageHash: String, val cursor: Int, val draft: String, val answeredIndex: Int, val recordingPath: String?, val completed: Boolean, val createdAt: Long)
@Entity(tableName = "practice_events", primaryKeys = ["ownerId","id"])
data class PracticeEvent(val ownerId: Long, val id: String, val sessionId: String, val sequence: Long, val kind: String, val payload: String, val syncState: String = "pending", val remoteResult: String? = null, val issue: String? = null)
@Entity(tableName = "practice_clocks")
data class PracticeClock(@PrimaryKey val ownerId: Long, val sequence: Long)
@Dao interface PracticeDao {
    @Query("SELECT * FROM installed_packages WHERE ownerId=:owner ORDER BY title") fun installed(owner: Long): Flow<List<InstalledPackage>>
    @Query("SELECT * FROM installed_packages WHERE ownerId=:owner AND hash=:hash") suspend fun packageByHash(owner: Long, hash: String): InstalledPackage?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun install(value: InstalledPackage)
    @Query("SELECT * FROM practice_sessions WHERE ownerId=:owner AND id=:id") suspend fun session(owner: Long, id: String): PracticeSession?
    @Query("SELECT * FROM practice_sessions WHERE ownerId=:owner AND packageHash=:hash AND completed=0 ORDER BY createdAt DESC LIMIT 1") suspend fun unfinished(owner: Long, hash: String): PracticeSession?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveSession(value: PracticeSession)
    @Query("SELECT * FROM practice_events WHERE ownerId=:owner AND sessionId=:session ORDER BY sequence") suspend fun events(owner: Long, session: String): List<PracticeEvent>
    @Query("SELECT * FROM practice_events WHERE ownerId=:owner AND syncState='pending' ORDER BY sequence LIMIT 50") suspend fun pending(owner: Long): List<PracticeEvent>
    @Insert suspend fun addEvent(event: PracticeEvent)
    @Query("SELECT sequence FROM practice_clocks WHERE ownerId=:owner") suspend fun clock(owner: Long): Long?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveClock(clock: PracticeClock)
    @Query("UPDATE practice_events SET syncState=:state, issue=:issue WHERE ownerId=:owner AND id=:id") suspend fun receipt(owner: Long, id: String, state: String, issue: String?)
    @Query("UPDATE practice_events SET remoteResult=:result WHERE ownerId=:owner AND id=:id") suspend fun assessment(owner: Long, id: String, result: String)
    @Query("DELETE FROM practice_events WHERE ownerId=:owner") suspend fun deleteEvents(owner: Long)
    @Query("DELETE FROM practice_sessions WHERE ownerId=:owner") suspend fun deleteSessions(owner: Long)
    @Query("DELETE FROM installed_packages WHERE ownerId=:owner") suspend fun deletePackages(owner: Long)
    @Query("SELECT COUNT(*) FROM practice_events WHERE ownerId=:owner AND syncState='pending'") fun pendingCount(owner: Long): Flow<Int>
}
val MIGRATION_1_2 = object : Migration(1,2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS installed_packages (ownerId INTEGER NOT NULL, hash TEXT NOT NULL, packageId TEXT NOT NULL, version INTEGER NOT NULL, title TEXT NOT NULL, preview INTEGER NOT NULL, directory TEXT NOT NULL, PRIMARY KEY(ownerId,hash))")
        db.execSQL("CREATE TABLE IF NOT EXISTS practice_sessions (ownerId INTEGER NOT NULL, id TEXT NOT NULL, packageHash TEXT NOT NULL, cursor INTEGER NOT NULL, draft TEXT NOT NULL, answeredIndex INTEGER NOT NULL, recordingPath TEXT, completed INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(ownerId,id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS practice_events (ownerId INTEGER NOT NULL, id TEXT NOT NULL, sessionId TEXT NOT NULL, sequence INTEGER NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL, syncState TEXT NOT NULL, remoteResult TEXT, issue TEXT, PRIMARY KEY(ownerId,id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS practice_clocks (ownerId INTEGER NOT NULL, sequence INTEGER NOT NULL, PRIMARY KEY(ownerId))")
    }
}
