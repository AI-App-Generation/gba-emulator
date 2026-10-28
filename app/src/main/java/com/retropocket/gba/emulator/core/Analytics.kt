package com.retropocket.gba.emulator.core

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.retropocket.gba.emulator.BuildConfig

/**
 * Bắn event Firebase từ ngoài Compose.
 *
 * Tồn tại song song với `LogScreenView` vì hai loại event có vòng đời khác hẳn nhau:
 *  - `LogScreenView` là `@Composable`, khoá theo tên nên xoay máy hay recompose không bắn trùng —
 *    đúng cho event dạng "đã xem màn X".
 *  - [Analytics.log] là hàm thường, bắn đúng một lần tại nơi gọi — đúng cho event dạng hành động
 *    (`..._next`, `..._continue`), và gọi được từ `onClick`, ViewModel hay listener.
 *
 * Dùng application context lấy một lần lúc khởi động, để nơi gọi không phải tự xoay xở tìm context.
 */
object Analytics {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun log(eventName: String) = log(eventName, null)

    fun log(eventName: String, params: Bundle?) {
        // Bản debug không bắn, giống LogScreenView — tránh làm bẩn số liệu bằng lượt test.
        if (BuildConfig.DEBUG) return
        if (!::appContext.isInitialized) return
        FirebaseAnalytics.getInstance(appContext).logEvent(eventName, params)
    }

    fun log(eventName: String, vararg params: Pair<String, Any>) {
        log(
            eventName,
            Bundle().apply {
                params.forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putDouble(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> putString(key, value.toString())
                    }
                }
            },
        )
    }
}
