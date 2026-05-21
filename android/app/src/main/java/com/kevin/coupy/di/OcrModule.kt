package com.kevin.coupy.di

import com.kevin.coupy.data.ocr.OcrService
import com.kevin.coupy.data.ocr.RealOcrService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * OCR service binding。
 *
 * Phase 2：綁定到 [RealOcrService]，透過 Cloudflare Worker → Gemini Vision 做真實 OCR。
 * 要切換回 stub（測試 UI 不打網路）可以改綁 [com.kevin.coupy.data.ocr.StubOcrService]。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {

    @Binds
    @Singleton
    abstract fun bindOcrService(impl: RealOcrService): OcrService
}
