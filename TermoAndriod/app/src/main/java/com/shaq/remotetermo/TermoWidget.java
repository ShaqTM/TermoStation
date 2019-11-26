package com.shaq.remotetermo;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import static android.content.Context.ALARM_SERVICE;
import static com.shaq.remotetermo.TermoService.ACTION_UPDATE_DATA;

/**
 * Implementation of App widget functionality.
 */
public class TermoWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final int N = appWidgetIds.length;
        for (int i = 0; i < N; i++) {

            RemoteViews views = new RemoteViews(context.getPackageName(),R.layout.termo_widget);

            Intent intent1 = new Intent(context, TermoService.class);
            intent1.setPackage("com.shaq.remotetermo");
            intent1.setAction(TermoService.ACTION_UPDATE_DATA);
            PendingIntent pendingIntent1 = PendingIntent.getService(context,appWidgetIds[i],intent1,0);
            views.setOnClickPendingIntent(R.id.inHum,pendingIntent1);
            views.setOnClickPendingIntent(R.id.inTemp,pendingIntent1);
            views.setOnClickPendingIntent(R.id.outTemp,pendingIntent1);
            views.setOnClickPendingIntent(R.id.device,pendingIntent1);
            views.setOnClickPendingIntent(R.id.l1,pendingIntent1);
            views.setOnClickPendingIntent(R.id.l0,pendingIntent1);
            views.setOnClickPendingIntent(R.id.l2,pendingIntent1);
            views.setOnClickPendingIntent(R.id.l3,pendingIntent1);
            views.setOnClickPendingIntent(R.id.l4,pendingIntent1);
            views.setOnClickPendingIntent(R.id.l5,pendingIntent1);
            views.setOnClickPendingIntent(R.id.l6,pendingIntent1);
            views.setOnClickPendingIntent(R.id.l7,pendingIntent1);
            views.setOnClickPendingIntent(R.id.l8,pendingIntent1);
            views.setOnClickPendingIntent(R.id.l9,pendingIntent1);
            views.setOnClickPendingIntent(R.id.textView2,pendingIntent1);
            views.setOnClickPendingIntent(R.id.textView3,pendingIntent1);
            views.setOnClickPendingIntent(R.id.textView,pendingIntent1);
            views.setOnClickPendingIntent(R.id.textView5,pendingIntent1);
            views.setOnClickPendingIntent(R.id.textView4,pendingIntent1);
            views.setOnClickPendingIntent(R.id.textView6,pendingIntent1);

            views.setOnClickPendingIntent(R.id.refreshTime,pendingIntent1);

            Intent intent2 = new Intent(context,MainActivity.class);
            PendingIntent pendingIntent2 =PendingIntent.getActivity(context,0,intent2,0);
            views.setOnClickPendingIntent(R.id.imageView,pendingIntent2);


            appWidgetManager.updateAppWidget(appWidgetIds[i], views);
        }
        Intent i  = new Intent(TermoService.ACTION_UPDATE_PREFS);
        i.setPackage("com.shaq.remotetermo");
        context.startService(i);

    }

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
        Intent i  = new Intent(TermoService.ACTION_UPDATE_PREFS);
        i.setPackage("com.shaq.remotetermo");
        context.startService(i);
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
        Intent i  = new Intent(TermoService.ACTION_UPDATE_PREFS);
        context.stopService(i);
    }

    @Override
    public void onReceive(Context context, Intent in) {
        super.onReceive(context, in);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.termo_widget);
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context.getPackageName(),TermoWidget.class.getName());
        if (in.getAction()!=null&&in.getAction().equals(TermoService.ACTION_UPDATE_WIDGET)) {
            Bundle data = in.getBundleExtra("data");
            views.setTextViewText(R.id.inTemp,Double.toString(data.getDouble("inTemp"))+" "+"\u00B0"+"C");
            views.setTextViewText(R.id.inHum,Double.toString(data.getDouble("inHum"))+" %");
            views.setTextViewText(R.id.outTemp,Double.toString(data.getDouble("outTemp"))+" "+"\u00B0"+"C");
            views.setTextViewText(R.id.device,data.getString("device"));
            views.setTextViewText(R.id.refreshTime,data.getString("refreshTime"));
            views.setTextColor(R.id.refreshTime,Color.WHITE);
            Double pres = data.getDouble("inPres",0);
            if (pres==0){
                views.setTextViewText(R.id.pres, "n/a");}
            else {
                views.setTextViewText(R.id.pres, Double.toString(pres) + " hPa");
            }
        }
        if (in.getAction()!=null&&in.getAction().equals(TermoService.ACTION_NO_CONNECTION)) {
            Bundle data = in.getBundleExtra("data");
            views.setTextViewText(R.id.device,data.getString("device"));
            views.setTextViewText(R.id.refreshTime,data.getString("refreshTime"));
            views.setTextColor(R.id.refreshTime,Color.YELLOW);
        }
        else if (in.getAction()!=null&&in.getAction().equals(TermoService.ACTION_UPDATE_PREFS)) {
            Intent i  = new Intent(TermoService.ACTION_UPDATE_PREFS);
            i.setPackage("com.shaq.remotetermo");
            context.startService(i);
        }



        Intent intent1 = new Intent(context, TermoService.class);
        intent1.setPackage("com.shaq.remotetermo");
        intent1.setAction(TermoService.ACTION_UPDATE_DATA);
        PendingIntent pendingIntent1 = PendingIntent.getService(context,0,intent1,0);
        views.setOnClickPendingIntent(R.id.inHum,pendingIntent1);
        views.setOnClickPendingIntent(R.id.inTemp,pendingIntent1);
        views.setOnClickPendingIntent(R.id.outTemp,pendingIntent1);
        views.setOnClickPendingIntent(R.id.device,pendingIntent1);
        views.setOnClickPendingIntent(R.id.refreshTime,pendingIntent1);
        views.setOnClickPendingIntent(R.id.l1,pendingIntent1);
        views.setOnClickPendingIntent(R.id.l0,pendingIntent1);
        views.setOnClickPendingIntent(R.id.l2,pendingIntent1);
        views.setOnClickPendingIntent(R.id.l3,pendingIntent1);
        views.setOnClickPendingIntent(R.id.l4,pendingIntent1);
        views.setOnClickPendingIntent(R.id.l5,pendingIntent1);
        views.setOnClickPendingIntent(R.id.l6,pendingIntent1);
        views.setOnClickPendingIntent(R.id.l7,pendingIntent1);
        views.setOnClickPendingIntent(R.id.l8,pendingIntent1);
        views.setOnClickPendingIntent(R.id.l9,pendingIntent1);
        views.setOnClickPendingIntent(R.id.textView2,pendingIntent1);
        views.setOnClickPendingIntent(R.id.textView3,pendingIntent1);
        views.setOnClickPendingIntent(R.id.textView,pendingIntent1);
        views.setOnClickPendingIntent(R.id.textView5,pendingIntent1);
        views.setOnClickPendingIntent(R.id.textView4,pendingIntent1);
        views.setOnClickPendingIntent(R.id.textView6,pendingIntent1);

        Intent intent2 = new Intent(context,MainActivity.class);
        PendingIntent pendingIntent2 =PendingIntent.getActivity(context,0,intent2,0);
        views.setOnClickPendingIntent(R.id.imageView,pendingIntent2);

        appWidgetManager.updateAppWidget(componentName, views);



    }


}

