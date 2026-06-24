package com.kevin.coupy.di

import com.kevin.coupy.data.photo.CouponPhotoStore
import com.kevin.coupy.data.photo.PhotoArchiver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 把 [PhotoArchiver] 綁到真實實作 [CouponPhotoStore]，
 * 讓 [com.kevin.coupy.data.backup.BackupRepository] 可以只依賴介面、便於測試。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PhotoModule {

    @Binds
    @Singleton
    abstract fun bindPhotoArchiver(impl: CouponPhotoStore): PhotoArchiver
}
