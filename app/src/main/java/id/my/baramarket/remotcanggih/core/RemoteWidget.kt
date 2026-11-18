package id.my.baramarket.remotcanggih.core

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import id.my.baramarket.remotcanggih.MainActivity
import id.my.baramarket.remotcanggih.R
import id.my.baramarket.remotcanggih.atv.Keys

/** Widget layar utama: power, volume, bisukan, putar/jeda, home. */
class RemoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val v = RemoteViews(context.packageName, R.layout.widget_remote)
            v.setOnClickPendingIntent(R.id.w_power, RemoteService.keyIntent(context, Keys.POWER))
            v.setOnClickPendingIntent(R.id.w_mute, RemoteService.keyIntent(context, Keys.MUTE))
            v.setOnClickPendingIntent(R.id.w_home, RemoteService.keyIntent(context, Keys.HOME))
            v.setOnClickPendingIntent(R.id.w_voldown, RemoteService.keyIntent(context, Keys.VOL_DOWN))
            v.setOnClickPendingIntent(R.id.w_play, RemoteService.keyIntent(context, Keys.PLAY_PAUSE))
            v.setOnClickPendingIntent(R.id.w_volup, RemoteService.keyIntent(context, Keys.VOL_UP))
            val open = PendingIntent.getActivity(
                context, 100, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.w_title, open)
            manager.updateAppWidget(id, v)
        }
    }
}
