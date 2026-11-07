package br.com.idiomas.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "catalog", primaryKeys = ["ownerId", "missionId"])
data class CatalogRow(val ownerId: Long, val missionId: String, val payload: String)
@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog WHERE ownerId = :ownerId ORDER BY missionId") fun observe(ownerId: Long): Flow<List<CatalogRow>>
    @Query("DELETE FROM catalog WHERE ownerId = :ownerId") suspend fun clear(ownerId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(rows: List<CatalogRow>)
    @Transaction suspend fun replace(ownerId: Long, rows: List<CatalogRow>) { clear(ownerId); insert(rows) }
}
@Database(entities = [CatalogRow::class, InstalledPackage::class, PracticeSession::class, PracticeEvent::class, PracticeClock::class, PracticeProgress::class, PracticeResultRequest::class], version = 4, exportSchema = true)
abstract class CatalogDatabase : RoomDatabase() { abstract fun catalogDao(): CatalogDao; abstract fun practiceDao(): PracticeDao }
interface CatalogCache {
    fun observe(ownerId: Long): Flow<List<CatalogItem>>
    suspend fun replace(ownerId: Long, items: List<CatalogItem>)
    suspend fun clear(ownerId: Long)
}
class RoomCatalogCache(private val dao: CatalogDao) : CatalogCache {
    override fun observe(ownerId: Long) = dao.observe(ownerId).map { rows -> rows.map { Json.decodeFromString<CatalogItem>(it.payload) } }
    override suspend fun replace(ownerId: Long, items: List<CatalogItem>) = dao.replace(ownerId, items.map { CatalogRow(ownerId, it.id, Json.encodeToString(it)) })
    override suspend fun clear(ownerId: Long) = dao.clear(ownerId)
}
