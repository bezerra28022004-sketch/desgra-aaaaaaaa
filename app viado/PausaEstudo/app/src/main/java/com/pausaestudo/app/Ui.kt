package com.pausaestudo.app

import android.content.Context
import android.view.ViewGroup

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Converte dp em pixels. */
fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
