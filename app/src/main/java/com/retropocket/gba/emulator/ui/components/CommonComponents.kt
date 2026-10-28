package com.retropocket.gba.emulator.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.retropocket.gba.emulator.ui.theme.*
import kotlin.math.hypot

val RadiusCard = RoundedCornerShape(16.dp)
val RadiusSmall = RoundedCornerShape(10.dp)
val RadiusPill = RoundedCornerShape(999.dp)

/**
 * Hiệu ứng phát sáng neon dạng ambient — vẽ quầng sáng toả đều quanh phần tử.
 * Tự vẽ bằng radial gradient thay vì shadow hệ thống để không bị lệch hướng và
 * không xung đột với nền trong suốt.
 */
fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 22.dp,
    alpha: Float = 0.45f,
): Modifier = this.drawBehind {
    // Vẽ HÌNH TRÒN chứ không phải drawRect: gradient toả tròn mà tô vào khung chữ nhật thì ở 4
    // góc màu chưa kịp về trong suốt, để lộ đúng một viền vuông quanh phần tử — thấy rõ nhất khi
    // phần tử là nút tròn có nền đục che mất phần giữa.
    // Bán kính lấy theo nửa đường chéo nên quầng sáng phủ hết phần tử rồi mới loang ra ngoài.
    val glowRadius = radius.toPx()
    val outer = hypot(size.width, size.height) / 2f + glowRadius
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = outer,
        ),
        radius = outer,
        center = center,
    )
}

/** Đổ bóng mềm cho card trên nền tối. */
fun Modifier.softShadow(alpha: Float = 0.5f, radius: Dp = 18.dp): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = alpha)),
            startY = size.height * 0.55f,
            endY = size.height + radius.toPx(),
        ),
    )
}

@Composable
/**
 * Co nhẹ phần tử khi nhấn.
 *
 * QUAN TRỌNG: đặt ngay sau các modifier kích thước/vị trí và TRƯỚC mọi modifier có vẽ (clip,
 * background, border, neonGlow...). `scale()` là một graphics layer, chỉ co những gì nằm SAU nó
 * trong chuỗi — đặt ở cuối thì chỉ nội dung bên trong co lại, còn nền/viền/glow đứng yên.
 */
fun Modifier.pressScale(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, label = "press")
    return this
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** Biến thể của [pressScale] có thêm nhấn giữ — dùng cho card game (mở menu hành động). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressScaleCombined(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, label = "press")
    return this
        .scale(scale)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

/**
 * Top bar dùng chung cho MỌI màn hình.
 *
 * Container luôn padding ngang 16.dp; nút back bù quang học bằng offset âm để tiêu đề của mọi màn
 * bắt đầu ở cùng một toạ độ — tránh hiện tượng tiêu đề nhảy ngang khi điều hướng qua lại.
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = TextPrimary,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.offset(x = (-8).dp),
                onClick = onBack,
            )
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TitlePixel,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = Caption, color = TextSecondary, maxLines = 1)
            }
        }
        actions()
    }
}

@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color = TextSecondary,
    enabled: Boolean = true,
    /** Viền tròn quanh nút — để null thì nút trong suốt như mặc định. */
    borderColor: Color? = null,
    /** Quầng sáng neon quanh nút, dùng khi cần nút nổi hẳn khỏi phần còn lại của top bar. */
    glowColor: Color? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            // glow phải đứng TRƯỚC clip: nó vẽ ra ngoài biên nút, bị clip thì mất sạch.
            .then(if (glowColor != null && enabled) Modifier.neonGlow(glowColor, radius = 10.dp, alpha = 0.45f) else Modifier)
            .clip(CircleShape)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else TextTertiary,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/**
 * Chip dạng viên thuốc — gộp 3 biến thể từng vẽ riêng: tab hệ máy, nút hành động phụ trên top bar,
 * badge hệ máy trên card. Chiều cao cố định 36.dp để mọi control cùng hàng luôn thẳng nhau.
 */
@Composable
fun PillChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingDot: Color? = null,
    selected: Boolean = false,
    accent: Color = NeonCyan,
    trailingCount: Int? = null,
    height: Dp = 36.dp,
    /** Chip nhỏ (badge trên card) cần padding hẹp hơn chip đứng độc lập, nếu không sẽ tràn card. */
    horizontalPadding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(RadiusPill)
            .background(if (selected) accent.copy(alpha = 0.18f) else BgSurface)
            .border(1.dp, if (selected) accent else BorderSubtle, RadiusPill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingDot != null) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(leadingDot))
        }
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) TextPrimary else TextSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            label,
            style = LabelPixel,
            color = if (selected) TextPrimary else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingCount != null) {
            Text("$trailingCount", style = LabelPixel, color = TextTertiary)
        }
    }
}

/**
 * Hàng danh sách dùng chung — gộp 5 biến thể từng vẽ riêng (ngôn ngữ, file zip, cài đặt, menu,
 * license). Khác nhau chỉ ở slot đầu và slot đuôi.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = NeonCyanDim,
    leading: (@Composable () -> Unit)? = null,
    height: Dp = 56.dp,
    selected: Boolean = false,
    accent: Color = NeonCyan,
    titleColor: Color = TextPrimary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RadiusCard)
            .background(if (selected) BgSurfaceHigh else BgSurface)
            .border(1.dp, if (selected) accent else BorderSubtle, RadiusCard)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> leading()
            icon != null -> Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = if (leading != null || icon != null) 12.dp else 0.dp),
        ) {
            Text(title, style = TitleMedium, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = Caption, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

@Composable
fun PrimaryPixelButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glow: Boolean = true,
    height: Dp = 52.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(height)
            .pressScale(enabled, onClick)
            .then(if (glow && enabled) Modifier.neonGlow(NeonCyan, alpha = 0.35f) else Modifier)
            .clip(RadiusPill)
            .background(if (enabled) NeonCyan else BorderStrong),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TitlePixel,
            color = if (enabled) BgRoot else TextTertiary,
            modifier = Modifier.padding(horizontal = 24.dp),
            maxLines = 1,
            // Font pixel rất rộng: nhãn dịch sang tiếng Pháp/Nga dễ vượt bề ngang nút, thà có dấu
            // "…" còn hơn bị cắt cụt giữa chữ.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SecondaryPixelButton(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = NeonCyan,
    height: Dp = 48.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(height)
            .pressScale(true, onClick)
            .clip(RadiusPill)
            .border(1.dp, accent, RadiusPill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TitlePixel,
            color = accent,
            modifier = Modifier.padding(horizontal = 20.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(BgSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = TextTertiary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = TitleMedium, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(description, style = BodySmall, color = TextSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryPixelButton(actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun LinearProgressTrack(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = NeonCyan,
    trackColor: Color = BorderSubtle,
    height: Dp = 6.dp,
) {
    Box(
        modifier
            .height(height)
            .clip(RadiusPill)
            .background(trackColor),
    ) {
        val fraction = progress?.coerceIn(0f, 1f)
        if (fraction != null) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxSize()
                    .clip(RadiusPill)
                    .background(color),
            )
        } else {
            // Không xác định tiến trình: hiện dải sáng chạy qua lại.
            val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "loading")
            val offset by transition.animateFloat(
                initialValue = -0.4f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(1100),
                ),
                label = "sweep",
            )
            Canvas(Modifier.fillMaxSize()) {
                val barWidth = size.width * 0.35f
                drawRect(
                    color = color,
                    topLeft = Offset(offset * size.width, 0f),
                    size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                )
            }
        }
    }
}

@Composable
fun CircularProgressTrack(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = NeonCyan,
    strokeWidth: Dp = 3.dp,
) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "rotate",
    )
    Canvas(modifier.size(size)) {
        drawArc(
            color = color.copy(alpha = 0.2f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth.toPx()),
        )
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 90f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                strokeWidth.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
    }
}

@Composable
fun WarningBanner(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NeonAmber,
    icon: ImageVector = Icons.Filled.Warning,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RadiusCard)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.5f), RadiusCard)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = BodySmall, color = TextPrimary)
    }
}

/** Gạch neon + tiêu đề — dùng cho tiêu đề section trong Home, Settings, Licenses. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    accent: Color = NeonCyan,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RadiusPill)
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = TitlePixel, color = TextPrimary, modifier = Modifier.weight(1f))
        if (trailingText != null) Text(trailingText, style = LabelPixel, color = TextTertiary)
    }
}

/** Khoảng trống cuối danh sách cuộn để nội dung không bị nav bar che. */
@Composable
fun ScrollTailSpacer() = Spacer(Modifier.height(240.dp))

@Composable
fun IconPainterButton(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color = TextSecondary,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter, contentDescription, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}
