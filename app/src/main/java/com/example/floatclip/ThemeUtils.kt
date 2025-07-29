package com.example.floatclip

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

@ColorInt
fun Context.getColorFromAttr(@AttrRes attrColor: Int): Int {
    val typedValue = TypedValue()
    val theme = theme
    theme.resolveAttribute(attrColor, typedValue, true)
    return if (typedValue.resourceId != 0) getColor(typedValue.resourceId) else typedValue.data
}
