package com.retropocket.gba.emulator.ui.components

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.VideoView
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/** Đường dẫn tới file trong `res/raw` dưới dạng Uri mà VideoView đọc được. */
private fun rawUri(packageName: String, @RawRes res: Int): Uri =
    Uri.parse("android.resource://$packageName/$res")

/**
 * Tỉ lệ khung hình (rộng / cao) của một video trong `res/raw`.
 *
 * Đọc từ metadata thay vì hardcode để đổi file media không phải sửa code. Đọc một lần rồi nhớ
 * lại — thao tác này chạm vào file nên không được lặp mỗi lần recompose.
 */
@Composable
fun rememberVideoAspectRatio(@RawRes res: Int, fallback: Float = 1f): Float {
    val context = LocalContext.current
    return remember(res) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            // Không dùng use{} vì MediaMetadataRetriever chỉ là Closeable từ API 29, còn app này
            // chạy từ API 24.
            try {
                retriever.setDataSource(context, rawUri(context.packageName, res))
                val w = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toFloatOrNull()
                val h = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toFloatOrNull()
                if (w != null && h != null && h > 0f) w / h else fallback
            } finally {
                retriever.release()
            }
        }.getOrDefault(fallback)
    }
}

/**
 * Video trang trí chạy lặp, không tiếng, không thanh điều khiển.
 *
 * Dùng [VideoView] thay vì kéo thêm Media3/ExoPlayer: đây chỉ là một clip ngắn nằm sẵn trong app,
 * không cần streaming, buffering hay DRM — thêm cả một thư viện player cho việc này là thừa.
 *
 * Người gọi tự đặt kích thước; muốn không méo hình thì cho khung đúng tỉ lệ lấy từ
 * [rememberVideoAspectRatio], vì VideoView luôn giữ nguyên tỉ lệ và chèn viền đen phần thừa.
 */
@Composable
fun LoopingVideo(@RawRes res: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = remember(res) {
        VideoView(context).apply {
            setVideoURI(rawUri(context.packageName, res))
            setOnPreparedListener { player ->
                player.isLooping = true
                // Video trang trí mà phát ra tiếng thì vừa giật mình vừa đè lên nhạc user đang nghe.
                player.setVolume(0f, 0f)
                start()
            }
        }
    }

    AndroidView(factory = { view }, modifier = modifier)

    DisposableEffect(view) {
        onDispose { view.stopPlayback() }
    }
}
