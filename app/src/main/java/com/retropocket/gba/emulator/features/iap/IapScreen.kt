package com.retropocket.gba.emulator.features.iap

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retropocket.gba.emulator.LogScreenView
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.core.AppCapability
import com.retropocket.gba.emulator.core.AppConfig
import com.retropocket.gba.emulator.features.common.PremiumState
import com.retropocket.gba.emulator.ui.components.IconCircleButton
import com.retropocket.gba.emulator.ui.components.LoopingVideo
import com.retropocket.gba.emulator.ui.components.RadiusCard
import com.retropocket.gba.emulator.ui.components.neonGlow
import com.retropocket.gba.emulator.ui.components.pressScale
import com.retropocket.gba.emulator.ui.components.rememberVideoAspectRatio
import com.retropocket.gba.emulator.ui.components.shimmerSweep
import com.retropocket.gba.emulator.ui.theme.*

/** Phần nội dung trùm lên đáy video hero. */
private val CONTENT_OVERLAP = 56.dp

/**
 * Chiều cao tối thiểu của video khi màn hình quá chật.
 *
 * Thà xén video còn hơn để nội dung bán hàng tràn khỏi màn: giá và nút mua phải luôn thấy được.
 */
private val MIN_VIDEO_HEIGHT = 120.dp

/**
 * Paywall.
 *
 * Bố cục: video hero → tiêu đề → 3 lợi ích → nút mua gói ĐANG CHỌN → 2 gói còn lại → dòng pháp lý.
 *
 * Vì sao một nút mua duy nhất thay vì mỗi thẻ là một nút: chạm nhầm ở màn thanh toán là lỗi đắt
 * giá, nên chạm thẻ chỉ ĐỔI lựa chọn, muốn mua phải chạm nút. Nút luôn phản chiếu gói đang chọn.
 *
 * Toàn bộ hệ quả của việc mua nằm ở [IapController]: tắt quảng cáo, bật [PremiumState], mở khoá
 * mọi game và bỏ giới hạn sức chứa. Màn này chỉ gọi `vm.buy()` rồi tự đóng khi premium bật lên.
 */
@Composable
fun IapScreen(
    onClose: () -> Unit = {},
    /** Mua xong: đi thẳng vào app, khác với [onClose] chỉ lùi lại một nấc. */
    onPurchased: () -> Unit = onClose,
) {
    LogScreenView("iap_view")

    val vm: IapViewModel = viewModel()
    val activity = LocalActivity.current
    val context = LocalContext.current
    val isPremium by PremiumState.isPremium.collectAsStateWithLifecycle()
    val prices by vm.prices.collectAsStateWithLifecycle()

    // Gói tuần rẻ nhất nên chọn sẵn: rào cản thấp nhất để bắt đầu.
    var selected by remember { mutableStateOf(Iap.weeklyId) }

    LaunchedEffect(Unit) { vm.loadPrices() }

    // Chỉ coi là "vừa mua" khi cờ premium LẬT từ false sang true trong lúc đang ở màn này.
    // Nếu vào màn mà đã premium sẵn (mở lại từ deep link, hoặc gói khôi phục về giữa chừng) thì
    // chỉ lùi lại, không quăng user ra khỏi màn họ đang đứng.
    val wasPremiumOnEntry = remember { isPremium }
    LaunchedEffect(isPremium) {
        if (!isPremium) return@LaunchedEffect
        if (wasPremiumOnEntry) onClose() else onPurchased()
    }

    val openUrl: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgRoot),
    ) {
        PaywallLayout(
            video = { IntroVideo() },
            content = {
                Column(
                    Modifier
                        .background(
                            // Lớp phủ mờ dần: trong suốt ở mép trên (phần đè lên đáy video) rồi
                            // đặc lại thành nền app trước khi chạm dòng chữ đầu tiên.
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.10f to BgRoot,
                            ),
                        )
                        .padding(top = CONTENT_OVERLAP)
                        .padding(horizontal = 20.dp)
                        .navigationBarsPadding(),
                ) {
                    Text(
                        stringResource(R.string.iap_title),
                        style = DisplayPixel,
                        color = NeonAmber,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .neonGlow(NeonAmber, radius = 26.dp, alpha = 0.20f),
                    )

                    Spacer(Modifier.height(16.dp))
                    BenefitRow(stringResource(R.string.iap_benefit_features))
                    BenefitDivider()
                    BenefitRow(
                        stringResource(R.string.iap_benefit_space, AppCapability.FREE_UNLOCK_SLOTS),
                    )
                    BenefitDivider()
                    BenefitRow(stringResource(R.string.iap_benefit_no_ads))

                    Spacer(Modifier.height(18.dp))
                    BuyButton(
                        selected = selected,
                        price = prices[selected].orEmpty(),
                        onClick = { activity?.let { vm.buy(it, selected) } },
                    )

                    Spacer(Modifier.height(12.dp))
                    PlanRow(
                        title = stringResource(R.string.iap_plan_lifetime),
                        subtitle = stringResource(R.string.iap_plan_lifetime_note),
                        price = prices[Iap.lifetimeId].orEmpty(),
                        isSelected = selected == Iap.lifetimeId,
                        // Chỉ gói mua đứt mới có vệt sáng: nhiều thứ cùng nhấp nháy thì không
                        // còn gì nổi bật nữa.
                        highlight = true,
                        badge = stringResource(R.string.iap_best_value),
                        onClick = { selected = Iap.lifetimeId },
                    )

                    Spacer(Modifier.height(10.dp))
                    PlanRow(
                        title = stringResource(R.string.iap_plan_monthly),
                        subtitle = stringResource(R.string.iap_plan_monthly_note),
                        price = prices[Iap.monthlyId].orEmpty(),
                        isSelected = selected == Iap.monthlyId,
                        highlight = false,
                        badge = null,
                        onClick = { selected = Iap.monthlyId },
                    )

                    Spacer(Modifier.height(14.dp))
                    LegalFooter(
                        onPrivacy = { openUrl(AppConfig.PRIVACY_POLICY_URL) },
                        onTerms = { openUrl(AppConfig.TERMS_OF_SERVICE_URL) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            },
        )

        IconCircleButton(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.close),
            tint = TextSecondary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp),
            onClick = onClose,
        )
    }
}

/**
 * Bố cục paywall: video dán mép trên, nội dung trùm lên đáy video, tất cả vừa đúng một màn.
 *
 * Phải tự đo thay vì đặt chiều cao cố định cho video: clip hero là hình vuông nên ở bề rộng máy
 * phổ thông nó đã chiếm gần nửa màn, cộng thêm nội dung là chắc chắn tràn. Ở đây đo nội dung
 * trước rồi giao đúng phần còn thừa cho video, nên không máy nào phải cuộn mà video vẫn lớn hết
 * mức có thể.
 */
@Composable
private fun PaywallLayout(
    video: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val ratio = rememberVideoAspectRatio(R.raw.intro_iap)

    Layout(
        content = {
            video()
            content()
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val overlap = CONTENT_OVERLAP.roundToPx()

        val contentPlaceable = measurables[1].measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
        )

        // Chiều cao tự nhiên của video là trần: phóng to hơn thế chỉ làm mờ hình.
        val natural = (width / ratio).toInt()
        val leftover = constraints.maxHeight - contentPlaceable.height + overlap
        val videoHeight = leftover.coerceIn(MIN_VIDEO_HEIGHT.roundToPx(), natural)

        val videoPlaceable = measurables[0].measure(Constraints.fixed(width, videoHeight))

        val totalHeight = maxOf(
            constraints.minHeight,
            videoHeight - overlap + contentPlaceable.height,
        )
        layout(width, totalHeight) {
            videoPlaceable.place(0, 0)
            contentPlaceable.place(0, videoHeight - overlap)
        }
    }
}

/**
 * Video mở đầu — rộng hết màn, giữ nguyên tỉ lệ gốc, thừa bao nhiêu thì xén đều trên dưới.
 *
 * Xén đối xứng chứ không bóp méo: một clip bị kéo giãn trông rẻ tiền hơn nhiều so với một clip
 * mất vài phần trăm ở hai mép. Nền đen phía sau để khoảnh khắc trước khung hình đầu tiên không
 * loé lên một mảng sáng.
 */
@Composable
private fun IntroVideo() {
    val ratio = rememberVideoAspectRatio(R.raw.intro_iap)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        LoopingVideo(
            R.raw.intro_iap,
            // requiredHeight bỏ qua ràng buộc của khung cha, nhờ đó video giữ đúng tỉ lệ và phần
            // vượt ra bị clipToBounds xén, thay vì bị VideoView thu nhỏ rồi chèn viền đen.
            Modifier
                .fillMaxWidth()
                .requiredHeight(maxWidth / ratio),
        )
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Bolt,
            contentDescription = null,
            tint = NeonCyan,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(text, style = BodyMedium, color = TextPrimary)
    }
}

@Composable
private fun BenefitDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                // Nhạt dần về hai đầu để đường kẻ không cắt ngang thẳng đuột qua cả khối.
                Brush.horizontalGradient(listOf(BorderSubtle, BorderStrong, BorderSubtle)),
            ),
    )
}

/** Góc vát thay cho bo tròn — dáng phím máy thùng, hợp với phần còn lại của app hơn viên thuốc. */
private val CtaShape = CutCornerShape(16.dp)

/**
 * Nút mua — nội dung đổi theo gói đang chọn.
 *
 * Số ngày dùng thử đọc từ Google Play. Khi Play chưa trả lời thì tạm dùng con số đã cấu hình sẵn
 * ([Iap.EXPECTED_TRIAL_DAYS]) để nút không nhấp nháy đổi nhãn lúc mới mở; Play trả lời là "không
 * có dùng thử" thì nhãn mới đổi hẳn sang dạng thường.
 */
@Composable
private fun BuyButton(selected: String, price: String, onClick: () -> Unit) {
    val trialDays = if (selected == Iap.weeklyId) {
        IapController.trialDays(selected) ?: Iap.EXPECTED_TRIAL_DAYS
    } else {
        0
    }
    val label = if (trialDays > 0) {
        stringResource(R.string.iap_cta_trial, trialDays)
    } else {
        stringResource(R.string.iap_cta_continue)
    }
    val note = when {
        price.isBlank() -> null
        trialDays > 0 -> stringResource(R.string.iap_cta_then_weekly, price)
        selected == Iap.weeklyId -> stringResource(R.string.iap_price_weekly, price)
        selected == Iap.monthlyId -> stringResource(R.string.iap_price_monthly, price)
        else -> stringResource(R.string.iap_price_lifetime, price)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(74.dp)
            .pressScale(onClick = onClick)
            .neonGlow(NeonCyan, radius = 24.dp, alpha = 0.55f)
            .clip(CtaShape)
            .background(
                // Sáng ở trên, sẫm ở dưới: cho cảm giác phím lồi chứ không phải mảng màu phẳng.
                Brush.verticalGradient(listOf(CtaHighlight, NeonCyan, NeonCyanDim)),
            )
            // Vệt sáng quét qua để nút "sống", nhịp nhanh hơn thẻ gói vì đây mới là đích chính.
            .shimmerSweep(alpha = 0.30f, widthFraction = 0.12f, periodMillis = 1700, pauseMillis = 1500)
            .border(1.dp, CtaHighlight, CtaShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 18.dp),
        ) {
            Text(
                label,
                style = TitlePixel,
                color = BgRoot,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (note != null) {
                Spacer(Modifier.height(5.dp))
                Text(note, style = BodySmall, color = BgRoot.copy(alpha = 0.66f))
            }
        }
    }
}

/** Huy hiệu "đáng mua nhất" dán vào mép trên thẻ, nên chỉ bo 2 góc dưới. */
private val BadgeShape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)

@Composable
private fun PlanRow(
    title: String,
    subtitle: String,
    price: String,
    isSelected: Boolean,
    highlight: Boolean,
    badge: String?,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            // Thẻ có huy hiệu cao hơn một chút để huy hiệu dán ở mép trên không chạm vào chữ.
            .height(if (badge != null) 92.dp else 84.dp)
            // pressScale bọc CẢ huy hiệu: để nó ở ngoài thì lúc nhấn, thẻ co lại còn huy hiệu
            // đứng im, trông như hai mảnh rời nhau.
            .pressScale(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .clip(RadiusCard)
                .background(if (isSelected) BgSurfaceHigh else BgSurface)
                .then(if (highlight) Modifier.shimmerSweep() else Modifier)
                // Viền luôn có mặt, chỉ đổi dày/màu — chuyển từ "không viền" sang "có viền" sẽ
                // làm nội dung thẻ xê dịch một pixel mỗi lần đổi lựa chọn.
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) NeonCyan else BorderSubtle,
                    shape = RadiusCard,
                )
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = TitlePixel, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = BodySmall, color = TextSecondary)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                price.ifBlank { "—" },
                style = TitleLarge,
                color = if (isSelected) NeonCyan else TextPrimary,
            )
        }

        if (badge != null) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .clip(BadgeShape)
                    .background(NeonAmber)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = BgRoot,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(badge, style = Caption, color = BgRoot, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LegalFooter(onPrivacy: () -> Unit, onTerms: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.iap_legal),
            style = Caption,
            color = TextTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegalLink(stringResource(R.string.iap_privacy), onPrivacy)
            Text("•", style = Caption, color = TextTertiary)
            LegalLink(stringResource(R.string.iap_terms), onTerms)
        }
    }
}

@Composable
private fun LegalLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = Caption,
        color = TextSecondary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(CircleShape)
            .pressScale(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
