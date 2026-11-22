package com.forgefolio.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ForgeFolioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
