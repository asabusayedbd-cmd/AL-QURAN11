package com.example

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.core.di.AppContainer
import com.example.core.localization.BidiUtils

class NoorApplication : Application(), ImageLoaderFactory {

    private var cachedImageLoader: ImageLoader? = null

    override fun onCreate() {
        super.onCreate()
        AppContainer.getInstance(this).warmUpAsync()
    }

    override fun newImageLoader(): ImageLoader {
        cachedImageLoader?.let { return it }
        val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice == true
        return ImageLoader.Builder(this)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowRgb565(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isLowRam) 0.08 else 0.14)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (isLowRam) 12L * 1024 * 1024 else 24L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
            .also { cachedImageLoader = it }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            cachedImageLoader?.memoryCache?.clear()
            BidiUtils.trimCaches()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        cachedImageLoader?.memoryCache?.clear()
        BidiUtils.trimCaches()
    }
}
