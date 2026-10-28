package com.retropocket.gba.emulator.ads

/**
 * Tên placement quảng cáo — PHẢI trùng từng ký tự với key trên Firebase Remote Config, vì mỗi tên
 * ở đây chính là nơi tra ra ad unit id. Sai một chữ là vị trí đó im lặng không có quảng cáo.
 *
 * Bảng này theo đúng danh sách vận hành quảng cáo đặt ra. Tên placement giữ nguyên theo bảng gốc
 * (đặt theo màn của app mẫu), phần chú thích ghi rõ nó tương ứng với màn nào trong app này.
 */
object AdsPlacementHolder {

    // ----- Placement chung, dùng khi một màn chưa có vị trí riêng -----
    const val NATIVE = "native"
    const val INTER = "inter"
    const val BANNER = "banner"
    const val REWARD = "reward"
    const val APP_OPEN = "app_open"

    // ----- Splash -----
    /** Banner dưới đáy màn Splash. */
    const val BANNER_SPLASH = "banner_splash"

    /** Inter chen trước khi rời Splash sang màn chọn ngôn ngữ. */
    const val INTER_SPLASH = "inter_splash"

    /** Như trên nhưng chỉ dùng từ lần MỞ APP THỨ 2 trở đi. */
    const val INTER_SPLASH_2 = "inter_splash_2"

    // ----- Language -----
    /** Native ở màn ngôn ngữ, lần đầu cài app. */
    const val NATIVE_LANGUAGE_1_1 = "native_language_1_1"

    /** Native ở màn ngôn ngữ, từ lần mở app thứ 2 trở đi. */
    const val NATIVE_LANGUAGE_1_2 = "native_language_1_2"

    /** Native nạp lại khi màn ngôn ngữ đi qua onPause rồi onResume. */
    const val NATIVE_LANGUAGE_RELOAD = "native_language_reload"

    // ----- Chọn thể loại game (GameGenre) -----
    /** Hiện ngay khi mở màn chọn thể loại. */
    const val NATIVE_BEFORE_SELECT_STYLE = "native_before_select_style"

    /** Hiện khi user bấm vào một mục bất kỳ trong màn chọn thể loại. */
    const val NATIVE_SELECT_STYLE = "native_select_style"

    /** Native full-screen khi bấm Next; load fail thì đi thẳng sang màn kế tiếp. */
    const val NATIVE_FULL_SELECT = "native_full_select"

    // ----- Onboarding -----
    /** Native ở trang 1. */
    const val NATIVE_OB_1 = "native_ob_1"

    /** Native full-screen ở trang full số 1. */
    const val NATIVE_FULL_1 = "native_full_1"

    /** Native full-screen ở trang full số 2. */
    const val NATIVE_FULL_2 = "native_full_2"

    /** Native ở trang full số 3. */
    const val NATIVE_OB_3 = "native_ob_3"

    /** Native nạp lại khi bất kỳ trang onboarding nào qua onPause rồi onResume. */
    const val NATIVE_RELOAD_OB = "native_reload_ob"

    // ----- Trong app -----
    /** Inter khi quay về Home (từ màn theme hoặc từ màn chơi game). */
    const val INTER_HOME = "inter_home"

    /** Native chèn xen kẽ trong danh sách game của tab thư viện. */
    const val NATIVE_MY_GAME = "native_my_game"

    /** Reward khi bấm chơi game, kèm dialog hỏi "xem 1 quảng cáo để chơi". */
    const val REWARD_PLAY = "reward_play"

    /** Reward dành cho phần theme. */
    const val REWARD_THEME = "reward_theme"

    /** Inter thay thế khi không show được [REWARD_PLAY]. */
    const val INTER_PLAY = "inter_play"

    /** Native ở màn chi tiết theme. */
    const val NATIVE_DETAIL_THEME = "native_detail_theme"

    /** Native ở màn theme của tôi. */
    const val NATIVE_MY_THEME = "native_my_theme"

    /** Native ở bước áp dụng theme. */
    const val NATIVE_APPLY_THEME = "native_apply_theme"

    /** Native ở màn Welcome back khi user quay lại app. */
    const val NATIVE_WELCOMEBACK = "native_welcomeback"

    // ----- Gỡ app -----
    /** Native ở màn gỡ cài đặt. */
    const val NATIVE_DELETE_APP_1 = "native_delete_app_1"

    /** Native ở màn cảnh báo trước khi gỡ. */
    const val NATIVE_DELETE_APP_2 = "native_delete_app_2"

    /** Inter chen trước khi mở màn gỡ cài đặt. */
    const val INTER_DELETE_APP = "inter_delete_app"

    // ----- Tải & chơi game -----
    /** Native trong dialog đang tải game ở tab Store. */
    const val NATIVE_DOWNLOAD = "native_download"

    /** Banner ở khung chính. KHÔNG hiện khi đang ở tab Store. */
    const val BANNER_HOME = "banner_home"

    /** Banner trong màn chơi game. */
    const val BANNER_PLAY = "banner_play"

    /** Native trong dialog thoát: thoát khỏi game đang chơi, và thoát app. */
    const val NATIVE_EXIT = "native_exit"
}
