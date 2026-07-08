package com.streamvault.app.cast

import android.content.Context
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): com.google.android.gms.cast.framework.CastOptions {
        return com.google.android.gms.cast.framework.CastOptions.Builder().build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }
}
