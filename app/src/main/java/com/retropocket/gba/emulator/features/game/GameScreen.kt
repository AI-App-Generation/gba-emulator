package com.retropocket.gba.emulator.features.game

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.ads.AdsPlacementHolder
import com.retropocket.gba.emulator.core.SettingManager
import com.retropocket.gba.emulator.corefeatures.emulator.CoreCatalog
import com.retropocket.gba.emulator.corefeatures.emulator.EmulatorSession
import com.retropocket.gba.emulator.corefeatures.emulator.TouchLayouts
import com.retropocket.gba.emulator.data.database.entity.GameEntity
import com.retropocket.gba.emulator.data.database.repo.GameRepo
import com.retropocket.gba.emulator.data.domain.ScreenRatio
import com.retropocket.gba.emulator.data.domain.SystemId
import com.retropocket.gba.emulator.ui.components.*
import com.retropocket.gba.emulator.ui.components.ads.AdBanner
import com.retropocket.gba.emulator.ui.theme.*
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.radialgamepad.library.RadialGamePad
import com.swordfish.radialgamepad.library.event.Event
import com.swordfish.radialgamepad.library.haptics.HapticConfig

@Composable
fun GameScreen(
    gameId: String,
    onExit: () -> Unit,
) {
    val games by GameRepo.games.collectAsStateWithLifecycle()
    val game = remember(games, gameId) { games.firstOrNull { it.id == gameId } }

    if (game == null || !CoreCatalog.isAvailable(game.system)) {
        GameErrorState(
            message = if (game == null) "Game not found" else "Core not available for this system",
            onExit = onExit,
        )
        return
    }

    GameContent(game = game, onExit = onExit)
}

@Composable
private fun GameContent(game: GameEntity, onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val hapticEnabled by SettingManager.hapticEnabled.collectAsStateWithLifecycle()

    var retroView by remember { mutableStateOf<GLRetroView?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var fatalError by remember { mutableStateOf<String?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var restoredState by remember(game.id) { mutableStateOf(false) }

    val padTheme = remember { TouchLayouts.theme() }
    val hapticConfig = remember(hapticEnabled) {
        if (hapticEnabled) HapticConfig.PRESS else HapticConfig.OFF
    }

    LaunchedEffect(game.id) { GameRepo.markPlayed(game.id) }

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(1500)
            toast = null
        }
    }

    // Lưu trạng thái BẮT BUỘC phải chạy khi GLRetroView còn sống.
    //
    // serializeState() đẩy việc sang luồng emulation rồi chặn luồng gọi bằng CountDownLatch KHÔNG
    // có timeout. Nếu gọi lúc view đang/đã bị huỷ (onDispose, AndroidView.onRelease) thì luồng GL
    // đã thoát, latch không bao giờ mở, main thread treo vĩnh viễn -> ANR "Input dispatching timed
    // out". Vì vậy mọi đường rời màn đều phải đi qua hàm này.
    val saveAndExit: () -> Unit = {
        retroView?.let { EmulatorSession.persist(game, it) }
        onExit()
    }

    // Lưu định kỳ để tiến trình vẫn còn nếu Android đưa app xuống nền rồi kill process.
    LaunchedEffect(retroView, isReady, game.id) {
        val view = retroView ?: return@LaunchedEffect
        if (!isReady) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(30_000)
            EmulatorSession.persist(game, view)
        }
    }

    // ON_PAUSE xảy ra khi GLRetroView vẫn còn sống, đây là thời điểm cuối an toàn để serialize.
    DisposableEffect(lifecycleOwner, retroView, isReady, game.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && isReady) {
                retroView?.let { EmulatorSession.persist(game, it) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        when {
            showExitConfirm -> showExitConfirm = false
            showMenu -> showMenu = false
            // Màn lỗi thì không hỏi lại, cũng chẳng có tiến trình nào để giữ.
            fatalError != null -> onExit()
            else -> showExitConfirm = true
        }
    }

    // Chờ frame đầu tiên render xong mới bỏ màn loading và chọn loại tay cầm cho core.
    LaunchedEffect(retroView) {
        val view = retroView ?: return@LaunchedEffect
        view.getGLRetroEvents().collect { event ->
            if (event is GLRetroView.GLRetroEvents.FrameRendered && !isReady) {
                isReady = true
                EmulatorSession.applyPreferredController(view, game.system.id)
                if (!restoredState && EmulatorSession.hasState(game)) {
                    EmulatorSession.loadState(game, view)
                }
                restoredState = true
            }
        }
    }

    // Core hỏng / ROM sai định dạng / GL không tương thích: nếu không lắng nghe luồng lỗi này thì
    // màn "LOADING…" sẽ quay mãi mãi và user chỉ còn cách tắt app.
    LaunchedEffect(retroView) {
        val view = retroView ?: return@LaunchedEffect
        view.getGLRetroErrors().collect { code ->
            fatalError = EmulatorSession.describeError(code)
        }
    }

    // Chốt chặn cuối: một số core thất bại im lặng, không phát cả frame lẫn lỗi.
    LaunchedEffect(retroView, isReady) {
        if (retroView == null || isReady) return@LaunchedEffect
        kotlinx.coroutines.delay(25_000)
        if (!isReady) {
            fatalError = "This game took too long to start. The ROM may be unsupported or damaged."
        }
    }

    // 3:2 cho hệ máy một màn hình, 2:3 cho NDS (core ghép 2 màn xếp dọc thành một ảnh).
    val screenModifier = Modifier.aspectRatio(game.system.ratio.value)

    // banner_play cắt bớt không gian THẬT ở đáy: khung game và 2 pad co lại phía trên nó,
    // không để banner đè lên vùng bấm.
    Column(Modifier.fillMaxSize().background(Color.Black)) {
    Box(Modifier.fillMaxWidth().weight(1f)) {

        // Khung game và 2 pad đều chỉ có MỘT vị trí gọi duy nhất, dùng chung cho cả dọc lẫn ngang
        // — chỉ Modifier thay đổi. Nếu tách thành 2 nhánh if/else thì mỗi lần xoay máy Compose sẽ
        // huỷ node cũ và dựng GLRetroView mới, vừa mất tiến trình vừa kéo theo bài toán lưu state
        // lúc view đang bị huỷ (chính là nguyên nhân gây ANR trước đây).
        Box(
            Modifier
                .fillMaxSize()
                .then(if (isLandscape) Modifier else Modifier.padding(bottom = PAD_STRIP_HEIGHT)),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = if (isLandscape) {
                    screenModifier.fillMaxHeight()
                } else {
                    screenModifier.fillMaxWidth()
                },
                factory = { ctx ->
                    GLRetroView(ctx, EmulatorSession.buildData(ctx, game)).also { view ->
                        lifecycleOwner.lifecycle.addObserver(view)
                        retroView = view
                    }
                },
                onRelease = { view ->
                    // Phải gỡ observer: view cũ vẫn nhận sự kiện vòng đời sau khi bị huỷ sẽ rò
                    // luồng GL và có thể làm crash lần dựng lại tiếp theo.
                    lifecycleOwner.lifecycle.removeObserver(view)
                    // Gỡ observer xong thì ON_DESTROY của GLRetroView KHÔNG BAO GIỜ tới nữa, nên
                    // phải tự gọi. Bỏ qua bước này thì core libretro không hề được unload:
                    // SRAM không được flush (mất bản lưu trong game) và lần mở game kế tiếp lại
                    // nạp chồng lên core cũ còn nằm trong bộ nhớ.
                    // An toàn khi gọi ở đây: onDestroy() chạy thẳng LibretroDroid.destroy() trên
                    // luồng gọi, không đẩy sang luồng emulation nên không treo như serializeState.
                    runCatching { view.onDestroy() }
                    // KHÔNG serialize state ở đây — xem ghi chú tại saveAndExit.
                    if (retroView === view) {
                        retroView = null
                        isReady = false
                    }
                },
            )
        }

        GamePadView(
            config = TouchLayouts.left(game.system.padLayout, padTheme, hapticConfig),
            onEvent = { handlePadEvent(it, retroView) { showMenu = true } },
            modifier = Modifier
                .align(if (isLandscape) Alignment.CenterStart else Alignment.BottomStart)
                .then(padModifier(isLandscape)),
        )
        GamePadView(
            config = TouchLayouts.right(game.system.padLayout, padTheme, hapticConfig),
            onEvent = { handlePadEvent(it, retroView) { showMenu = true } },
            modifier = Modifier
                .align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomEnd)
                .then(padModifier(isLandscape)),
        )

        if (!isReady && fatalError == null) {
            Box(Modifier.fillMaxSize().background(BgRoot), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressTrack(size = 32.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "LOADING ${game.system.shortName}…",
                        style = LabelPixel,
                        color = TextSecondary,
                    )
                }
            }
        }

        fatalError?.let { message ->
            // Vẽ đè toàn màn: không thể chơi tiếp nên không để user loay hoay với khung game hỏng.
            Box(Modifier.fillMaxSize().background(BgRoot)) {
                GameErrorState(message = message, onExit = onExit)
            }
        }

        if (fatalError == null) {
            IconCircleButton(
                icon = Icons.Filled.Menu,
                contentDescription = "Game menu",
                tint = TextPrimary,
                size = 40.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(BgSurface.copy(alpha = 0.65f)),
                onClick = { showMenu = true },
            )
        }

        toast?.let { message ->
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .clip(RadiusPill)
                    .background(BgSurfaceHigh)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(message, style = BodySmall, color = TextPrimary)
            }
        }

        if (showMenu) {
            GameMenuPopup(
                hasState = EmulatorSession.hasState(game),
                onDismiss = { showMenu = false },
                onSave = {
                    val ok = retroView?.let { EmulatorSession.persist(game, it) } == true
                    toast = if (ok) "State saved" else "Cannot save state"
                    showMenu = false
                },
                onLoad = {
                    val ok = retroView?.let { EmulatorSession.loadState(game, it) } == true
                    toast = if (ok) "State loaded" else "No saved state"
                    showMenu = false
                },
                onReset = {
                    retroView?.reset()
                    toast = "Game reset"
                    showMenu = false
                },
                onExit = {
                    showMenu = false
                    showExitConfirm = true
                },
            )
        }
    }

        AdBanner(placement = AdsPlacementHolder.BANNER_PLAY)
    }

    if (showExitConfirm) {
        ExitConfirmDialog(
            title = stringResource(R.string.exit_game_title),
            description = stringResource(R.string.exit_game_desc),
            confirmLabel = stringResource(R.string.exit_game_confirm),
            onConfirm = {
                showExitConfirm = false
                saveAndExit()
            },
            onDismiss = { showExitConfirm = false },
        )
    }
}

/** Chiều cao dải chứa 2 pad khi để dọc — dùng chung cho cả padding của khung game. */
private val PAD_STRIP_HEIGHT = 220.dp

/** Kích thước pad theo hướng màn hình. Tách riêng để 2 pad dùng đúng một chiến lược. */
private fun padModifier(isLandscape: Boolean): Modifier =
    if (isLandscape) {
        Modifier.fillMaxHeight().fillMaxWidth(0.32f).alpha(0.85f)
    } else {
        Modifier.height(PAD_STRIP_HEIGHT).fillMaxWidth(0.5f)
    }

@Composable
private fun GamePadView(
    config: com.swordfish.radialgamepad.library.config.RadialGamePadConfig,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pad by remember { mutableStateOf<RadialGamePad?>(null) }

    LaunchedEffect(pad) {
        pad?.events()?.collect(onEvent)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx -> RadialGamePad(config, 8f, ctx).also { pad = it } },
    )
}

/**
 * Chuyển sự kiện từ gamepad ảo sang core.
 *
 * Pad chỉ báo THỜI ĐIỂM chuyển trạng thái (nhấn/nhả); việc giữ trạng thái giữa 2 lần đó do
 * GLRetroView lo, nên ở đây chỉ cần chuyển tiếp thẳng.
 */
private fun handlePadEvent(event: Event, retroView: GLRetroView?, onMenu: () -> Unit) {
    val view = retroView ?: return
    when (event) {
        is Event.Button -> {
            if (event.id == TouchLayouts.MENU_BUTTON_ID) {
                if (event.action == KeyEvent.ACTION_UP) onMenu()
                return
            }
            view.sendKeyEvent(event.action, event.id)
        }

        is Event.Direction -> view.sendMotionEvent(event.id, event.xAxis, event.yAxis)
        else -> Unit
    }
}

@Composable
private fun GameMenuPopup(
    hasState: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .width(220.dp)
                .clip(RadiusCard)
                .background(BgSurface)
                .border(1.dp, BorderSubtle, RadiusCard)
                .padding(vertical = 8.dp),
        ) {
            MenuRow(Icons.Filled.Save, "Save state", onClick = onSave)
            MenuRow(Icons.Filled.Upload, "Load state", enabled = hasState, onClick = onLoad)
            MenuRow(Icons.Filled.RestartAlt, "Reset game", onClick = onReset)
            MenuRow(Icons.Filled.Close, "Exit", tint = NeonRed, onClick = onExit)
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    tint: Color = TextSecondary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pressScale(enabled, onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            null,
            tint = if (enabled) tint else TextTertiary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            label,
            style = TitleMedium,
            color = if (enabled) TextPrimary else TextTertiary,
        )
    }
}

@Composable
private fun GameErrorState(message: String, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(BgRoot), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message,
                style = TitleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryPixelButton("BACK", onClick = onExit)
        }
    }
}
