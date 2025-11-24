package com.kilodeltaapps.karooflightradar

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import io.hammerhead.karooext.KarooSystemService

@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {

    @Provides
    @ServiceScoped
    fun provideKarooSystem(@ApplicationContext context: Context): KarooSystemService {
        return KarooSystemService(context)
    }
}