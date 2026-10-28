package com.retropocket.gba.emulator.features.uninstall

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.retropocket.gba.emulator.FadeSlideUp
import com.retropocket.gba.emulator.MainActivity
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.ads.AdsPlacementHolder
import com.retropocket.gba.emulator.ads.AdsPolicy
import com.retropocket.gba.emulator.ui.theme.GameEmulatorTheme

/**
 * Đăng ký shortcut động "Uninstall" vào menu giữ icon ở launcher.
 *
 * Android KHÔNG cho app biết lúc nào nó sắp bị gỡ, nên không thể chặn đúng thời điểm đó. Cách
 * duy nhất còn lại là tự đặt một lối vào ngay cạnh nút gỡ của hệ thống: user giữ icon app sẽ
 * thấy mục "Uninstall" này. Gọi lại mỗi lần mở app đều an toàn (push đè shortcut cũ).
 */
fun createUninstallShortcut(context: Context) {
    runCatching {
        val intent = Intent(context, UninstallActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            // Thiếu 2 cờ này thì màn gỡ chồng lên chính task của app: thoát khỏi nó là rơi
            // thẳng vào app đang mở phía dưới, luồng giữ chân mất hết ý nghĩa.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val shortcut = ShortcutInfoCompat.Builder(context, "shortcut_uninstall")
            .setShortLabel(context.getString(R.string.uninstall_shortcut_label))
            .setLongLabel(context.getString(R.string.uninstall_shortcut_label))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_trash))
            .setIntent(intent)
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
}

private object UninstallRoutes {
    const val NOTICE = "uninstall_notice"
    const val WARNING = "uninstall_warning"
}

/**
 * MỘT Activity duy nhất cho cả luồng gỡ; 2 bước bên trong là 2 route của NavHost, dùng đúng bộ
 * transition [FadeSlideUp] mà app dùng cho các màn phụ.
 */
class UninstallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GameEmulatorTheme {
                UninstallFlow(onFinish = { finish() })
            }
        }
    }
}

@Composable
private fun UninstallFlow(onFinish: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val nav = rememberNavController()

    /** Người dùng đổi ý: đưa thẳng về app, KHÔNG chen thêm quảng cáo nào nữa. */
    fun keepApp() {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
            )
        }
        onFinish()
    }

    /**
     * inter_delete_app nằm CHEN GIỮA hai bước: bấm "tiếp tục gỡ" ở màn Notice thì xem quảng cáo
     * rồi mới sang màn cảnh báo. Không load được quảng cáo thì vẫn đi tiếp, không chặn đường.
     */
    fun continueToWarning() {
        if (activity == null) {
            nav.navigate(UninstallRoutes.WARNING)
            return
        }
        AdsPolicy.showInterAd(
            activity = activity,
            adPlacement = AdsPlacementHolder.INTER_DELETE_APP,
            force = true,
            onContinue = { nav.navigate(UninstallRoutes.WARNING) },
        )
    }

    /** Bàn giao cho hộp thoại gỡ của hệ thống; máy nào chặn thì lui về trang thông tin ứng dụng. */
    fun uninstallAnyway() {
        val uri = Uri.fromParts("package", context.packageName, null)
        val delete = Intent(Intent.ACTION_DELETE, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(delete)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        onFinish()
    }

    NavHost(
        navController = nav,
        startDestination = UninstallRoutes.NOTICE,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        composable(UninstallRoutes.NOTICE) {
            UninstallNoticeScreen(
                onKeepApp = ::keepApp,
                onContinue = ::continueToWarning,
            )
        }
        composable(
            UninstallRoutes.WARNING,
            enterTransition = { FadeSlideUp.first },
            exitTransition = { FadeSlideUp.second },
        ) {
            UninstallWarningScreen(
                onKeepApp = ::keepApp,
                onUninstallAnyway = ::uninstallAnyway,
            )
        }
    }
}
