package br.com.idiomas

import android.app.Application
import androidx.room.Room
import br.com.idiomas.data.*

class IdiomasApplication : Application() {
    private val api by lazy { HttpLearningApi(BuildConfig.API_BASE_URL) }
    private val database by lazy { Room.databaseBuilder(this,CatalogDatabase::class.java,"catalog.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build() }
    val repository by lazy { CatalogRepository(api,RoomCatalogCache(database.catalogDao()),EncryptedSessionVault(this)) }
    val practice by lazy { PracticeRepository(this,database,repository,api).also { repo -> repo.scheduleSync = { owner -> PracticeSyncWorker.enqueue(this,owner) } } }
    override fun onCreate() {
        super.onCreate()
        repository.session.value?.let { PracticeSyncWorker.enqueue(this,it.account.id) }
    }
}
