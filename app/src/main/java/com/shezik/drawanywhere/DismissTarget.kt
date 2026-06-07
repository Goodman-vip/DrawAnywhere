package com.shezik.drawanywhere

sealed class DismissTarget {
    object Hidden : DismissTarget()
    data class Visible(val overlapping: Boolean) : DismissTarget()
}
