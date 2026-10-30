package com.example.linkgrab

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DownloadRepository.init(this)
        PrivateStore.init(this)
    }
}
