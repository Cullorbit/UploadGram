package com.example.photouploaderapp.configs

import android.graphics.Typeface

data class SyncOption(
    val title: String,
    var isSelected: Boolean,
    var typeface: Typeface? = null
)