package io.github.xejohv.podclassicxzs.base

interface ScreenView {

    fun enter(): Boolean

    fun enterLongClick(): Boolean

    fun slide(slideVal: Int): Boolean

    companion object {
        const val LAUNCH_MODE_SINGLE = 0b00000001
        const val LAUNCH_MODE_NORMAL = 0b00000000
    }

    fun getTitle(): String?

    fun getLaunchMode(): Int = LAUNCH_MODE_NORMAL

    fun onViewCreate() {}

    fun onViewDelete() {}

    fun onViewAdd() {}

    fun onViewRemove() {}

    fun getObserver(): Observer? {
        return null
    }

    fun onConfigurationChanged() {}

    /**
     * 从别的地方（系统设置、系统无障碍开关、外部 App）回到本应用时回调。
     * 页面里那些“状态来自系统、不是自己改的”行（比如辅助功能是否开启），
     * 要在这里重新读一遍，否则屏幕上一直是进来时那一刻的旧值。
     */
    fun onViewResume() {}
}