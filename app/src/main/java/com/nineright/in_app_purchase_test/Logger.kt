package com.nineright.in_app_purchase_test

import android.text.method.ScrollingMovementMethod
import android.widget.TextView

class Logger(textView: TextView) {
    private val view: TextView = textView

    init {
        this.view.setMovementMethod(ScrollingMovementMethod())
    }

    fun log(text: String) {
        this.view.append("[log] $text\n")
    }

    fun err(text: String) {
        this.view.append("[err] $text\n")
    }
}