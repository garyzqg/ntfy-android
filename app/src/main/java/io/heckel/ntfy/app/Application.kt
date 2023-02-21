package io.heckel.ntfy.app

import android.app.Application
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.util.Log

class Application : Application() {
    //by lazy(lamda)用于延迟加载属性，并且只是在第一次使用这个属性的时候才会执行Lamda以初始化对象，后续直接使用不会再调用lamda，by lazy是可以节省性能的
    val repository by lazy {
        val repository = Repository.getInstance(applicationContext)
        if (repository.getRecordLogs()) {
            Log.setRecord(true)
        }
        repository
    }
}
