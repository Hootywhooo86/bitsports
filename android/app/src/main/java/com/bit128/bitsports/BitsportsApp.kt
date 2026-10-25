package com.bit128.bitsports

import android.app.Application
import com.bit128.bitsports.ui.TeamInk

/** Runs first in every bitsports process: the app, widgets, alarms and the game watcher. */
class BitsportsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TeamInk.init(this)
    }
}
