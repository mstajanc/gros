package io.github.mstajanc.gros.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.mstajanc.gros.MainActivity
import io.github.mstajanc.gros.R

/**
 * Widget s jedným tlačidlom +, ktoré otvorí obrazovku Nová transakcia (G9). Klasický
 * AppWidgetProvider s RemoteViews; obsah sa nemení, takže sa nič neaktualizuje periodicky.
 */
class AddTransactionWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_add).apply {
            setOnClickPendingIntent(R.id.widget_root, newTransactionIntent(context))
            setOnClickPendingIntent(R.id.widget_add_button, newTransactionIntent(context))
        }
        widgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    private fun newTransactionIntent(context: Context): PendingIntent {
        // Activity je singleTop: pri bežiacej appke príde intent do onNewIntent.
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_NEW_TRANSACTION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
