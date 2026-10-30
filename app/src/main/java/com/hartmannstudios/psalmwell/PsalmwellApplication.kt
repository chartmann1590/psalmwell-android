package com.hartmannstudios.psalmwell

import android.app.Application
import com.hartmannstudios.psalmwell.data.FirebaseGate
import com.hartmannstudios.psalmwell.reminder.ReminderScheduler

class PsalmwellApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseGate.init(this)
        container = AppContainer(this)
        ReminderScheduler.createChannel(this)
    }
}
