package io.github.xejohv.podclassicxzs.view

import android.annotation.SuppressLint
import android.content.Context
import io.github.xejohv.podclassicxzs.base.ScreenView
import io.github.xejohv.podclassicxzs.widget.RecyclerListView

@SuppressLint("ViewConstructor")
open class ItemListView(
    context: Context,
    itemList: ArrayList<Item>,
    private val title: String,
    defaultListener: OnItemClickListener?,
    max_size: Int,
    sorted: Boolean = false
) : RecyclerListView(context, max_size), ScreenView {

    constructor(
        context: Context,
        itemList: ArrayList<Item>,
        TITLE: String,
        defaultListener: OnItemClickListener?
    ) : this(context, itemList, TITLE, defaultListener, DEFAULT_MAX_SIZE)

    init {
        defaultOnItemClickListener = defaultListener
        this.itemList = itemList
        this.sorted = sorted
    }

    override fun enter(): Boolean {
        return onItemClick()
    }

    override fun enterLongClick(): Boolean {
        return onItemLongClick()
    }

    override fun slide(slideVal: Int): Boolean {
        return onSlide(slideVal)
    }

    override fun getTitle(): String {
        return title
    }

    override fun onViewResume() {
        // 重绑一次：Item.liveRightText 会在这时重新取系统状态，
        // 于是“从系统设置里改完回来”看到的就是新值，不用退出桌面再进。
        post { refreshList() }
    }
}