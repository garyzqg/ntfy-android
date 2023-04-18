package io.heckel.ntfy.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import io.heckel.ntfy.app.Application
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.msg.NotificationDispatcher
import io.heckel.ntfy.service.SubscriberService
import io.heckel.ntfy.service.SubscriberServiceManager
import io.heckel.ntfy.ui.MainActivity
import io.heckel.ntfy.work.DeleteWorker
import io.heckel.ntfy.work.PollWorker
import java.util.concurrent.TimeUnit

/**
 * @author : zhangqinggong
 * date    : 2023/2/22 10:14
 * desc    : 消息推送组件工具类
 */
private var workManager: WorkManager? = null // Context-dependent
private var dispatcher: NotificationDispatcher? = null // Context-dependent
private lateinit var repository: Repository

/**
 * 订阅
 */
fun subscribe(context: Context){
    workManager = WorkManager.getInstance(context)
    repository = (context.applicationContext as Application).repository;
    dispatcher = NotificationDispatcher(context,repository)

    // Create notification channels right away, so we can configure them immediately after installing the app
    dispatcher?.init()

    // Subscribe to control Firebase channel (so we can re-start the foreground service if it dies)
//    messenger.subscribe(ApiService.CONTROL_TOPIC)
    //手动启动一次前台组件
    SubscriberServiceManager.refresh(context)

    // Background things
    //轮训拉取缓存消息
    schedulePeriodicPollWorker()

    //服务重启
    schedulePeriodicServiceRestartWorker()
    //删除任务
    schedulePeriodicDeleteWorker()

    // Permissions
    maybeRequestNotificationPermission(context)
}

/**
 * 用户鉴权
 */
fun Auth(){

}
private fun schedulePeriodicPollWorker() {
    val workerVersion = repository.getPollWorkerVersion()
    val workPolicy = if (workerVersion == PollWorker.VERSION) {//取versioncode
        Log.d(MainActivity.TAG, "Poll worker version matches: choosing KEEP as existing work policy")
        ExistingPeriodicWorkPolicy.KEEP
    } else {
        Log.d(MainActivity.TAG, "Poll worker version DOES NOT MATCH: choosing REPLACE as existing work policy")
        repository.setPollWorkerVersion(PollWorker.VERSION)
        ExistingPeriodicWorkPolicy.REPLACE
    }

    /**
     * 工作约束 约束可确保将工作延迟到满足最佳条件时运行
     * 详见 https://blog.csdn.net/Mr_Tony/article/details/125600406?spm=1001.2014.3001.5502
     */
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)//只在网络连接情况下执行work
        .build()

    /**
     * OneTimeWorkRequest.Builder用于构建单次运行的后台任务请求
     * PeriodicWorkRequest.Builder用于构建周期运行的后台任务请求，且运行间隔不能短于15 mins
     * 每个工作请求都可以设置一个唯一标识符，该标识符可用于在以后标识该工作，以便取消工作或观察其进度
     * 可以向单个工作请求添加多个标记。这些标记在内部以一组字符串的形式进行存储
     */

    val work = PeriodicWorkRequestBuilder<PollWorker>(MainActivity.POLL_WORKER_INTERVAL_MINUTES, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .addTag(PollWorker.TAG)
        .addTag(PollWorker.WORK_NAME_PERIODIC_ALL)
        .build()
    Log.d(MainActivity.TAG, "Poll worker: Scheduling period work every ${MainActivity.POLL_WORKER_INTERVAL_MINUTES} minutes")
    /**
     *WorkManager.enqueueUniqueWork()（用于一次性工作）
     *WorkManager.enqueueUniquePeriodicWork()（用于定期工作）
     *existingWorkPolicy - 此 enum 可告知 WorkManager：如果已有使用该名称且尚未完成的唯一工作链，应执行什么操作
     * REPLACE：用新工作替换现有工作。此选项将取消现有工作
     * KEEP：保留现有工作，并忽略新工作。
     */
    workManager!!.enqueueUniquePeriodicWork(PollWorker.WORK_NAME_PERIODIC_ALL, workPolicy, work)
}

/**
 * 后台任务-服务重启,3小时一次
 */
private fun schedulePeriodicServiceRestartWorker() {
    val workerVersion = repository.getAutoRestartWorkerVersion()
    val workPolicy = if (workerVersion == SubscriberService.SERVICE_START_WORKER_VERSION) {
        Log.d(MainActivity.TAG, "ServiceStartWorker version matches: choosing KEEP as existing work policy")
        ExistingPeriodicWorkPolicy.KEEP
    } else {
        Log.d(MainActivity.TAG, "ServiceStartWorker version DOES NOT MATCH: choosing REPLACE as existing work policy")
        repository.setAutoRestartWorkerVersion(SubscriberService.SERVICE_START_WORKER_VERSION)
        ExistingPeriodicWorkPolicy.REPLACE
    }
    val work = PeriodicWorkRequestBuilder<SubscriberServiceManager.ServiceStartWorker>(MainActivity.SERVICE_START_WORKER_INTERVAL_MINUTES, TimeUnit.MINUTES)
        .addTag(SubscriberService.TAG)
        .addTag(SubscriberService.SERVICE_START_WORKER_WORK_NAME_PERIODIC)
        .build()
    Log.d(MainActivity.TAG, "ServiceStartWorker: Scheduling period work every ${MainActivity.SERVICE_START_WORKER_INTERVAL_MINUTES} minutes")
    workManager?.enqueueUniquePeriodicWork(SubscriberService.SERVICE_START_WORKER_WORK_NAME_PERIODIC, workPolicy, work)
}

private fun schedulePeriodicDeleteWorker() {
    val workerVersion = repository.getDeleteWorkerVersion()
    val workPolicy = if (workerVersion == DeleteWorker.VERSION) {
        Log.d(MainActivity.TAG, "Delete worker version matches: choosing KEEP as existing work policy")
        ExistingPeriodicWorkPolicy.KEEP
    } else {
        Log.d(MainActivity.TAG, "Delete worker version DOES NOT MATCH: choosing REPLACE as existing work policy")
        repository.setDeleteWorkerVersion(DeleteWorker.VERSION)
        ExistingPeriodicWorkPolicy.REPLACE
    }
    val work = PeriodicWorkRequestBuilder<DeleteWorker>(MainActivity.DELETE_WORKER_INTERVAL_MINUTES, TimeUnit.MINUTES)
        .addTag(DeleteWorker.TAG)
        .addTag(DeleteWorker.WORK_NAME_PERIODIC_ALL)
        .build()
    Log.d(MainActivity.TAG, "Delete worker: Scheduling period work every ${MainActivity.DELETE_WORKER_INTERVAL_MINUTES} minutes")
    workManager!!.enqueueUniquePeriodicWork(DeleteWorker.WORK_NAME_PERIODIC_ALL, workPolicy, work)
}


private fun maybeRequestNotificationPermission(context: Context) {
    // Android 13 (SDK 33) requires that we ask for permission to post notifications
    // https://developer.android.com/develop/ui/views/notifications/notification-permission

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED) {
        ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
    }
}