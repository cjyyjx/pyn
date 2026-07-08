package com.pyn.editor

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class ObservableEditText : AppCompatEditText {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    var onScrollChangedListener: ((Int, Int, Int, Int) -> Unit)? = null

    override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
        onScrollChangedListener?.invoke(horiz, vert, oldHoriz, oldVert)
    }
}
