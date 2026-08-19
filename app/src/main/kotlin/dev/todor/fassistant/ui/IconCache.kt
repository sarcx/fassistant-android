package dev.todor.fassistant.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

/**
 * Reading an icon touches the APK, which is far too slow to do while a list scrolls. Fixed-size
 * cache and one background thread, because a phone with 300 apps installed would otherwise hold
 * 300 bitmaps for a screen that shows eight.
 */
class IconCache(private val ctx: Context) {

    private val cache = LruCache<String, Drawable>(60)
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    fun load(pkg: String, into: ImageView) {
        into.tag = pkg
        val cached = cache.get(pkg)
        if (cached != null) {
            into.setImageDrawable(cached)
            return
        }
        into.setImageDrawable(null)
        executor.execute {
            val icon = runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return@execute
            cache.put(pkg, icon)
            handler.post { if (into.tag == pkg) into.setImageDrawable(icon) }
        }
    }
}
