package com.retropocket.gba.emulator.corefeatures.emulator

import android.view.KeyEvent
import com.retropocket.gba.emulator.data.domain.PadLayout
import com.retropocket.gba.emulator.ui.theme.BgSurfaceHigh
import com.retropocket.gba.emulator.ui.theme.BorderStrong
import com.retropocket.gba.emulator.ui.theme.NeonCyan
import com.retropocket.gba.emulator.ui.theme.TextPrimary
import com.swordfish.radialgamepad.library.config.ButtonConfig
import com.swordfish.radialgamepad.library.config.CrossConfig
import com.swordfish.radialgamepad.library.config.PrimaryDialConfig
import com.swordfish.radialgamepad.library.config.RadialGamePadConfig
import com.swordfish.radialgamepad.library.config.RadialGamePadTheme
import com.swordfish.radialgamepad.library.config.SecondaryDialConfig
import com.swordfish.radialgamepad.library.haptics.HapticConfig
import androidx.compose.ui.graphics.toArgb

/**
 * Layout gamepad ảo cho từng nhóm phần cứng.
 *
 * RadialGamePad không có sẵn preset theo hệ máy — thư viện chỉ cung cấp "khối lego"
 * (Cross / PrimaryButtons / SingleButton / DoubleButton), còn việc đặt nút nào ở vị trí nào là
 * do app tự định nghĩa. Toạ độ dùng hệ "mặt đồng hồ": vòng ngoài chia thành [sockets] ô,
 * index bắt đầu từ vị trí 3 giờ và tăng dần NGƯỢC chiều kim đồng hồ.
 */
object TouchLayouts {

    private const val SOCKETS = 12

    /** Id riêng cho nút mở menu trong game — không trùng keycode nào của libretro. */
    const val MENU_BUTTON_ID = 1_000_001

    fun theme(): RadialGamePadTheme = RadialGamePadTheme(
        normalColor = BgSurfaceHigh.copy(alpha = 0.92f).toArgb(),
        pressedColor = NeonCyan.copy(alpha = 0.65f).toArgb(),
        simulatedColor = NeonCyan.copy(alpha = 0.4f).toArgb(),
        textColor = TextPrimary.toArgb(),
        backgroundColor = BgSurfaceHigh.copy(alpha = 0.35f).toArgb(),
        normalStrokeColor = BorderStrong.toArgb(),
        backgroundStrokeColor = BorderStrong.copy(alpha = 0.5f).toArgb(),
        lightColor = NeonCyan.copy(alpha = 0.25f).toArgb(),
        strokeWidthDp = 1f,
        // RadialGamePad 2.0.0 đã bỏ tham số customTypeface (bản 1.x còn có) — nhãn nút dùng font
        // mặc định của thư viện, không ép Press Start 2P vào đây được.
    )

    fun left(layout: PadLayout, theme: RadialGamePadTheme, haptic: HapticConfig) = when (layout) {
        PadLayout.N64 -> n64Left(theme, haptic)
        PadLayout.PSX -> psxLeft(theme, haptic)
        else -> standardLeft(layout, theme, haptic)
    }

    fun right(layout: PadLayout, theme: RadialGamePadTheme, haptic: HapticConfig) = when (layout) {
        PadLayout.ONE_BUTTON -> oneButtonRight(theme, haptic)
        PadLayout.TWO_BUTTONS, PadLayout.GBA -> twoButtonsRight(layout, theme, haptic)
        PadLayout.FOUR_BUTTONS_SHOULDER -> fourButtonsRight(theme, haptic)
        PadLayout.SIX_BUTTONS -> sixButtonsRight(theme, haptic)
        PadLayout.PSX -> psxRight(theme, haptic)
        PadLayout.N64 -> n64Right(theme, haptic)
    }

    // ---------- LEFT ----------

    /** D-pad + Select, thêm nút L cho hệ máy có nút vai. */
    private fun standardLeft(
        layout: PadLayout,
        theme: RadialGamePadTheme,
        haptic: HapticConfig,
    ): RadialGamePadConfig {
        val secondary = mutableListOf<SecondaryDialConfig>(
            SecondaryDialConfig.SingleButton(2, 1f, 0f, button(KeyEvent.KEYCODE_BUTTON_SELECT, "SEL")),
            SecondaryDialConfig.Empty(8, 1, 1f, 0f),
        )
        if (layout == PadLayout.FOUR_BUTTONS_SHOULDER || layout == PadLayout.GBA) {
            secondary += SecondaryDialConfig.DoubleButton(3, 0f, button(KeyEvent.KEYCODE_BUTTON_L1, "L"))
        }
        return RadialGamePadConfig(
            sockets = SOCKETS,
            primaryDial = PrimaryDialConfig.Cross(CrossConfig(id = 0)),
            secondaryDials = secondary,
            haptic = haptic,
            theme = theme,
        )
    }

    private fun psxLeft(theme: RadialGamePadTheme, haptic: HapticConfig) = RadialGamePadConfig(
        sockets = SOCKETS,
        primaryDial = PrimaryDialConfig.Cross(CrossConfig(id = 0)),
        secondaryDials = listOf(
            SecondaryDialConfig.SingleButton(2, 1f, 0f, button(KeyEvent.KEYCODE_BUTTON_SELECT, "SEL")),
            SecondaryDialConfig.DoubleButton(3, 0f, button(KeyEvent.KEYCODE_BUTTON_L1, "L1")),
            SecondaryDialConfig.DoubleButton(5, 0f, button(KeyEvent.KEYCODE_BUTTON_L2, "L2")),
            SecondaryDialConfig.Stick(
                index = 9,
                spread = 2,
                scale = 1.1f,
                distance = 0f,
                id = MOTION_SOURCE_ANALOG_LEFT_ID,
                contentDescription = "Left analog stick",
            ),
        ),
        haptic = haptic,
        theme = theme,
    )

    /** N64: analog stick là điều khiển chính, D-pad phụ bên cạnh. */
    private fun n64Left(theme: RadialGamePadTheme, haptic: HapticConfig) = RadialGamePadConfig(
        sockets = SOCKETS,
        primaryDial = PrimaryDialConfig.Stick(
            id = MOTION_SOURCE_ANALOG_LEFT_ID,
            contentDescription = "Analog stick",
        ),
        secondaryDials = listOf(
            SecondaryDialConfig.Cross(8, 2, 1f, 0f, CrossConfig(id = 0)),
            SecondaryDialConfig.DoubleButton(3, 0f, button(KeyEvent.KEYCODE_BUTTON_L1, "L")),
            SecondaryDialConfig.SingleButton(2, 1f, 0f, button(KeyEvent.KEYCODE_BUTTON_THUMBL, "Z")),
        ),
        haptic = haptic,
        theme = theme,
    )

    // ---------- RIGHT ----------

    private fun oneButtonRight(theme: RadialGamePadTheme, haptic: HapticConfig) = RadialGamePadConfig(
        sockets = SOCKETS,
        primaryDial = PrimaryDialConfig.PrimaryButtons(
            dials = listOf(button(KeyEvent.KEYCODE_BUTTON_B, "FIRE")),
        ),
        secondaryDials = listOf(
            SecondaryDialConfig.SingleButton(4, 1f, 0f, button(KeyEvent.KEYCODE_BUTTON_START, "ST")),
            menuDial(),
        ),
        haptic = haptic,
        theme = theme,
    )

    private fun twoButtonsRight(
        layout: PadLayout,
        theme: RadialGamePadTheme,
        haptic: HapticConfig,
    ): RadialGamePadConfig {
        val secondary = mutableListOf<SecondaryDialConfig>(
            SecondaryDialConfig.SingleButton(4, 1f, 0f, button(KeyEvent.KEYCODE_BUTTON_START, "ST")),
            menuDial(),
        )
        if (layout == PadLayout.GBA) {
            secondary += SecondaryDialConfig.DoubleButton(2, 0f, button(KeyEvent.KEYCODE_BUTTON_R1, "R"))
        }
        return RadialGamePadConfig(
            sockets = SOCKETS,
            primaryDial = PrimaryDialConfig.PrimaryButtons(
                dials = listOf(
                    button(KeyEvent.KEYCODE_BUTTON_A, "A"),
                    button(KeyEvent.KEYCODE_BUTTON_B, "B"),
                ),
                rotationInDegrees = 30f,
            ),
            secondaryDials = secondary,
            haptic = haptic,
            theme = theme,
        )
    }

    private fun fourButtonsRight(theme: RadialGamePadTheme, haptic: HapticConfig) = RadialGamePadConfig(
        sockets = SOCKETS,
        primaryDial = PrimaryDialConfig.PrimaryButtons(
            dials = listOf(
                button(KeyEvent.KEYCODE_BUTTON_A, "A"),
                button(KeyEvent.KEYCODE_BUTTON_X, "X"),
                button(KeyEvent.KEYCODE_BUTTON_Y, "Y"),
                button(KeyEvent.KEYCODE_BUTTON_B, "B"),
            ),
        ),
        secondaryDials = listOf(
            SecondaryDialConfig.DoubleButton(2, 0f, button(KeyEvent.KEYCODE_BUTTON_R1, "R")),
            SecondaryDialConfig.SingleButton(4, 1f, 0f, button(KeyEvent.KEYCODE_BUTTON_START, "ST")),
            menuDial(),
        ),
        haptic = haptic,
        theme = theme,
    )

    private fun sixButtonsRight(theme: RadialGamePadTheme, haptic: HapticConfig) = RadialGamePadConfig(
        sockets = SOCKETS,
        primaryDial = PrimaryDialConfig.PrimaryButtons(
            dials = listOf(
                button(KeyEvent.KEYCODE_BUTTON_B, "C"),
                button(KeyEvent.KEYCODE_BUTTON_A, "B"),
                button(KeyEvent.KEYCODE_BUTTON_Y, "A"),
                button(KeyEvent.KEYCODE_BUTTON_L1, "X"),
                button(KeyEvent.KEYCODE_BUTTON_X, "Y"),
                button(KeyEvent.KEYCODE_BUTTON_R1, "Z"),
            ),
            rotationInDegrees = 15f,
        ),
        secondaryDials = listOf(
            SecondaryDialConfig.SingleButton(4, 1f, 0f, button(KeyEvent.KEYCODE_BUTTON_START, "ST")),
            menuDial(),
        ),
        haptic = haptic,
        theme = theme,
    )

    private fun psxRight(theme: RadialGamePadTheme, haptic: HapticConfig) = RadialGamePadConfig(
        sockets = SOCKETS,
        primaryDial = PrimaryDialConfig.PrimaryButtons(
            dials = listOf(
                button(KeyEvent.KEYCODE_BUTTON_A, "○"),
                button(KeyEvent.KEYCODE_BUTTON_X, "△"),
                button(KeyEvent.KEYCODE_BUTTON_Y, "□"),
                button(KeyEvent.KEYCODE_BUTTON_B, "✕"),
            ),
        ),
        secondaryDials = listOf(
            SecondaryDialConfig.DoubleButton(2, 0f, button(KeyEvent.KEYCODE_BUTTON_R1, "R1")),
            SecondaryDialConfig.DoubleButton(4, 0f, button(KeyEvent.KEYCODE_BUTTON_R2, "R2")),
            SecondaryDialConfig.SingleButton(6, 1f, 0f, button(KeyEvent.KEYCODE_BUTTON_START, "ST")),
            SecondaryDialConfig.Stick(
                index = 8,
                spread = 2,
                scale = 1.1f,
                distance = 0f,
                id = MOTION_SOURCE_ANALOG_RIGHT_ID,
                contentDescription = "Right analog stick",
            ),
            menuDial(),
        ),
        haptic = haptic,
        theme = theme,
    )

    private fun n64Right(theme: RadialGamePadTheme, haptic: HapticConfig) = RadialGamePadConfig(
        sockets = SOCKETS,
        primaryDial = PrimaryDialConfig.PrimaryButtons(
            dials = listOf(
                button(KeyEvent.KEYCODE_BUTTON_A, "A"),
                button(KeyEvent.KEYCODE_BUTTON_B, "B"),
            ),
            rotationInDegrees = 30f,
        ),
        secondaryDials = listOf(
            SecondaryDialConfig.DoubleButton(2, 0f, button(KeyEvent.KEYCODE_BUTTON_R1, "R")),
            SecondaryDialConfig.SingleButton(4, 1f, 0f, button(KeyEvent.KEYCODE_BUTTON_START, "ST")),
            SecondaryDialConfig.Stick(
                index = 8,
                spread = 2,
                scale = 1f,
                distance = 0f,
                id = MOTION_SOURCE_ANALOG_RIGHT_ID,
                contentDescription = "C buttons",
            ),
            menuDial(),
        ),
        haptic = haptic,
        theme = theme,
    )

    // ---------- helper ----------

    private fun button(id: Int, label: String) = ButtonConfig(id = id, label = label)

    /** Nút mở menu trong game, luôn đặt ở vị trí 10 giờ trên pad phải. */
    private fun menuDial() = SecondaryDialConfig.SingleButton(
        10, 0.8f, 0f,
        ButtonConfig(id = MENU_BUTTON_ID, label = "≡", supportsButtons = true),
    )

    /**
     * Id analog stick phải khớp hằng số MOTION_SOURCE_* của GLRetroView để event bắn thẳng
     * vào core không cần bảng ánh xạ trung gian.
     */
    const val MOTION_SOURCE_ANALOG_LEFT_ID = 1
    const val MOTION_SOURCE_ANALOG_RIGHT_ID = 2
}
