package br.com.idiomas

import android.content.Context
import androidx.work.*
import br.com.idiomas.data.ApiFailure
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class PracticeSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as IdiomasApplication
        if(app.repository.session.value?.account?.id != inputData.getLong("owner",-1)) return Result.failure()
        return try { if(app.practice.sync()) Result.success() else Result.retry() }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(failure: ApiFailure) { if(failure.status == 401 || failure.status == 403) Result.failure() else Result.retry() }
        catch(_: Exception) { Result.retry() }
    }
    companion object {
        fun enqueue(context: Context, owner: Long) {
            val request = OneTimeWorkRequestBuilder<PracticeSyncWorker>().setInputData(workDataOf("owner" to owner))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("practice-sync-$owner",ExistingWorkPolicy.APPEND_OR_REPLACE,request)
        }
    }
}
