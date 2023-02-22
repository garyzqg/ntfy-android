package io.heckel.ntfy.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import io.heckel.ntfy.R
import io.heckel.ntfy.util.initNotificationModule

/**
 * @author : zhangqinggong
 * date    : 2023/2/21 18:14
 * desc    : 推送组件测试页面
 */
class TestActivity :AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        val btnSubscribe: AppCompatButton = findViewById(R.id.btn_subscribe)
        btnSubscribe.setOnClickListener{
//            Toast.makeText(this,"测试",Toast.LENGTH_LONG).show()
//            subscribe()
            initNotificationModule(this)
        }
    }
}