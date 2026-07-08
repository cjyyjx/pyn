package com.pyn.editor

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class ObservableEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyle) {

    var onScrollChangedListener: ((Int, Int, Int, Int) -> Unit)? = null

    override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
        onScrollChangedListener?.invoke(horiz, vert, oldHoriz, oldVert)
    }
}
