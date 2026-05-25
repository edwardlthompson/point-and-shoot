package dev.pointandshoot

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.util.Log

/**
 * Sprint **IP.1** — home-screen widget for one-tap preview launch (complements Quick Settings tiles).
 */
class PnsCameraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.pns_widget_camera)
            val launch =
                Intent(Intent.ACTION_VIEW, Uri.parse("pointandshoot://preview")).apply {
                    setClass(context, MainActivity::class.java)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            val pending =
                PendingIntent.getActivity(
                    context,
                    0,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            views.setOnClickPendingIntent(R.id.pns_widget_root, pending)
            appWidgetManager.updateAppWidget(id, views)
        }
        Log.i(PlatformIntegration.TAG, "widget onUpdate count=${appWidgetIds.size}")
    }

    companion object {
        fun isRegistered(context: Context): Boolean {
            val mgr = AppWidgetManager.getInstance(context)
            val ids =
                mgr.getAppWidgetIds(ComponentName(context, PnsCameraWidgetProvider::class.java))
            return ids.isNotEmpty()
        }
    }
}
