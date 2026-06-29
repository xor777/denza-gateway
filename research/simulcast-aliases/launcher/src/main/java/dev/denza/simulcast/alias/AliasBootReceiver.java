package dev.denza.simulcast.alias;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AliasBootReceiver extends BroadcastReceiver {
    private static final String TAG = "DenzaSimulcastBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "receiver action=" + (intent == null ? null : intent.getAction()));
        AliasSourceService.start(context);
    }
}
