package top.easytier.miuix.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import top.easytier.miuix.data.repository.NetworkRepository
import top.easytier.miuix.data.repository.RealNetworkRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindNetworkRepository(impl: RealNetworkRepository): NetworkRepository
}
