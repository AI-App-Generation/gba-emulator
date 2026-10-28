package com.retropocket.gba.emulator.features.common

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.ads.AdsPlacementHolder
import com.retropocket.gba.emulator.ads.AdsPolicy
import com.retropocket.gba.emulator.core.AppCapability
import com.retropocket.gba.emulator.corefeatures.library.UnlockStore
import com.retropocket.gba.emulator.data.database.entity.GameEntity
import com.retropocket.gba.emulator.ui.components.*
import com.retropocket.gba.emulator.ui.theme.*

/**
 * Cổng chặn "phải sở hữu mới chơi được", dựng theo đúng khuôn `ItemUnlockGateState` của app mẫu.
 *
 * Game đã sở hữu thì chạy hành động ngay; game chưa sở hữu thì giữ hành động lại, hiện dialog mời
 * xem quảng cáo thưởng, xem xong mới chạy tiếp. Nhờ vậy màn gọi chỉ viết một lần phần "sau khi mở
 * khoá thì làm gì", không phải rải logic ở từng chỗ.
 */
class PlayGateState {

    var pendingGame: GameEntity? by mutableStateOf(null)
        private set

    /**
     * Hết slot mở khoá — dialog đổi hẳn nội dung, không mời xem quảng cáo thưởng nữa.
     *
     * Để user xem hết một quảng cáo rồi mới báo "không mở được đâu" là cách chắc chắn nhất để
     * mất thiện cảm.
     */
    var isSlotsFull: Boolean by mutableStateOf(false)
        private set

    /** Đang chờ quảng cáo trả kết quả — khoá nút để user không bấm chồng. */
    var isWorking: Boolean by mutableStateOf(false)
        private set

    private var pendingAction: (() -> Unit)? = null

    fun request(game: GameEntity, action: () -> Unit) {
        // User bấm được vào item nghĩa là phiên quảng cáo trước đã kết thúc, dù kết thúc kiểu gì.
        // Không tin vào callback của SDK ở đây: nếu nó nuốt mất một nhánh (đóng sớm chẳng hạn),
        // isWorking kẹt true và MỌI control trong dialog chết cứng cho tới khi khởi động lại app.
        isWorking = false

        if (UnlockStore.isUnlocked(game)) {
            action()
            return
        }
        pendingGame = game
        pendingAction = action
        isSlotsFull = !AppCapability.canUnlockMore(UnlockStore.usedSlots())
    }

    fun dismiss() {
        if (isWorking) return
        pendingGame = null
        pendingAction = null
        isSlotsFull = false
    }

    /** User huỷ giữa chừng: mở lại dialog ở đúng game đó, không mở khoá, không tốn slot. */
    private fun abort(game: GameEntity) {
        isWorking = false
        pendingGame = game
    }

    private fun complete(game: GameEntity) {
        // unlock() có thể từ chối nếu slot vừa đầy giữa chừng. Khi đó đừng cho chạy hành động —
        // chơi được một lần rồi lần sau lại bị chặn thì user tưởng app lúc được lúc không. Phải
        // dựng lại pendingGame vì grant() đã xoá nó đi để ẩn dialog lúc chờ quảng cáo.
        if (!UnlockStore.unlock(game.id)) {
            isWorking = false
            pendingGame = game
            isSlotsFull = true
            return
        }
        val action = pendingAction
        isWorking = false
        pendingGame = null
        pendingAction = null
        isSlotsFull = false
        action?.invoke()
    }

    /**
     * Chuỗi rơi: reward → inter_play → mở khoá luôn.
     *
     * Bước cuối là bắt buộc: hết fill quảng cáo hoặc remote config tắt placement thì vẫn phải cho
     * chơi, không được để user kẹt trước một game không bao giờ mở được.
     */
    fun grant(activity: Activity) {
        val game = pendingGame ?: return
        if (isWorking) return
        isWorking = true
        // Ẩn dialog NGAY. Dialog vẽ bằng Popup nên nằm ở cửa sổ khác, đè lên cả màn chờ quảng
        // cáo của BaseActivity — để nguyên thì user thấy dialog đứng im như app treo. Hành động
        // vẫn nằm trong pendingAction nên mở khoá xong vẫn chạy tiếp.
        pendingGame = null

        val restore = game
        AdsPolicy.showRewardAd(
            activity = activity,
            adPlacement = AdsPlacementHolder.REWARD_PLAY,
            onRewardGranted = { complete(restore) },
            // Không hiện nổi quảng cáo là lỗi của ta, không được để user kẹt trước một game
            // không bao giờ mở được — rơi xuống inter rồi vẫn mở khoá.
            onAdFailed = {
                AdsPolicy.showInterAd(
                    activity = activity,
                    adPlacement = AdsPlacementHolder.INTER_PLAY,
                    force = true,
                    onContinue = { complete(restore) },
                )
            },
            // User tự đóng giữa chừng thì trả mọi thứ về như cũ và hiện lại dialog để họ thử
            // lại. Tuyệt đối KHÔNG rơi xuống inter ở nhánh này: mở lên đóng ngay mà vẫn có game
            // thì chẳng ai ngồi xem hết quảng cáo nữa.
            onDismissedWithoutReward = { abort(restore) },
        )
    }
}

@Composable
fun rememberPlayGate(): PlayGateState = remember { PlayGateState() }

/** Gọi MỘT lần ở gốc toàn màn hình của khung chính, không lồng trong thẻ hay sheet nhỏ. */
@Composable
fun PlayGateHost(gate: PlayGateState, activity: Activity?, onOpenPremium: () -> Unit = {}) {
    val game = gate.pendingGame ?: return

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = gate::dismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(ScrimHeavy)
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RadiusCard)
                    .background(BgSurface)
                    .border(1.dp, BorderSubtle, RadiusCard)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .neonGlow(NeonAmber, radius = 14.dp, alpha = 0.35f)
                        .clip(CircleShape)
                        .background(NeonAmber.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (gate.isSlotsFull) Icons.Filled.WorkspacePremium else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(
                        if (gate.isSlotsFull) R.string.slots_full_title else R.string.unlock_title,
                    ),
                    style = TitleMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (gate.isSlotsFull) {
                        stringResource(R.string.slots_full_desc, AppCapability.FREE_UNLOCK_SLOTS)
                    } else {
                        stringResource(R.string.unlock_desc, game.title)
                    },
                    style = BodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))
                if (gate.isSlotsFull) {
                    // Hết slot thì quảng cáo thưởng vô nghĩa — chỉ còn một đường là mua gói.
                    PrimaryPixelButton(
                        text = stringResource(R.string.upgrade_cta),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            gate.dismiss()
                            onOpenPremium()
                        },
                    )
                    Spacer(Modifier.height(2.dp))
                } else {
                    PrimaryPixelButton(
                        text = stringResource(R.string.unlock_watch),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !gate.isWorking && activity != null,
                        onClick = { activity?.let(gate::grant) },
                    )
                    Spacer(Modifier.height(10.dp))
                    // Đúng thời điểm nhất để mời mua: user vừa bị chặn trước một game.
                    Text(
                        stringResource(R.string.unlock_all_cta),
                        style = LabelPixel,
                        color = NeonAmber,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RadiusPill)
                            .pressScale(enabled = !gate.isWorking) {
                                gate.dismiss()
                                onOpenPremium()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.unlock_cancel),
                        style = LabelPixel,
                        color = TextTertiary,
                        modifier = Modifier
                            .clip(RadiusPill)
                            .pressScale(enabled = !gate.isWorking, onClick = gate::dismiss)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
