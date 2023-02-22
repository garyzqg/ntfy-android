package io.heckel.ntfy.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.*
import io.heckel.ntfy.app.Application
import io.heckel.ntfy.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * This class only manages the SubscriberService, i.e. it starts or stops it.
 * It's used in multiple activities.
 *
 * We are starting the service via a worker and not directly because since Android 7
 * (but officially since Lollipop!), any process called by a BroadcastReceiver
 * (only manifest-declared receiver) is run at low priority and hence eventually
 * killed by Android.
 */
class SubscriberServiceManager(private val context: Context) {
    fun refresh() {
        Log.d(TAG, "Enqueuing work to refresh subscriber service")
        val workManager = WorkManager.getInstance(context)
        val startServiceRequest = OneTimeWorkRequest.Builder(ServiceStartWorker::class.java).build()
        //一次性后台任务 不重复
        workManager.enqueueUniqueWork(WORK_NAME_ONCE, ExistingWorkPolicy.KEEP, startServiceRequest) // Unique avoids races!
    }

    fun restart() {
        Intent(context, SubscriberService::class.java).also { intent ->
            context.stopService(intent) // Service will auto-restart
        }
    }

    /**
     * Starts or stops the foreground service by figuring out how many instant delivery subscriptions
     * exist. If there's > 0, then we need a foreground service.
     */
    class ServiceStartWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val id = this.id
            if (context.applicationContext !is Application) {
                Log.d(TAG, "ServiceStartWorker: Failed, no application found (work ID: ${id})")
                return Result.failure()
            }
            withContext(Dispatchers.IO) {
                val app = context.applicationContext as Application
                val subscriptionIdsWithInstantStatus = app.repository.getSubscriptionIdsWithInstantStatus() //set{(id,instant),(id2,instant2)}
                //filter() 筛选集合内满足条件的数据 传入的函数必须返回值是boolean (instant) 此句为筛选instant等于true的通知
                val instantSubscriptions = subscriptionIdsWithInstantStatus.toList().filter { (_, instant) -> instant }.size
                val action = if (instantSubscriptions > 0) SubscriberService.Action.START else SubscriberService.Action.STOP
                val serviceState = SubscriberService.readServiceState(context)
                if (serviceState == SubscriberService.ServiceState.STOPPED && action == SubscriberService.Action.STOP) {
                    //如果没有订阅任何主题 则直接结束协程 不启动任何服务
                    return@withContext Result.success()
                }
                Log.d(TAG, "ServiceStartWorker: Starting foreground service with action $action (work ID: ${id})")
                /**
                 *                        T作为it传入  作为this传入
                 * 返回block最后一行结果    let         run、with
                 * 返回T本身               also        apply
                 */
                Intent(context, SubscriberService::class.java).also {
                    it.action = action.name
                    //8.0 以后不希望后台应用运行后台服务，除非特殊条件 一旦通过startForegroundService() 启动前台服务，必须在service 中有startForeground() 配套，不然会出现ANR 或者crash
                    ContextCompat.startForegroundService(context, it)
                }
            }
            return Result.success()
        }
    }

    //静态
    companion object {
        const val TAG = "NtfySubscriberMgr"
        const val WORK_NAME_ONCE = "ServiceStartWorkerOnce"

        fun refresh(context: Context) {
            val manager = SubscriberServiceManager(context)
            manager.refresh()
        }
    }
}
