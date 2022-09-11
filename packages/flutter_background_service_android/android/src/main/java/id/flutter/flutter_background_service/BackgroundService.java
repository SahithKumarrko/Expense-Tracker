package id.flutter.flutter_background_service;

import static android.os.Build.VERSION.SDK_INT;
import android.app.Notification;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.widget.RemoteViews;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.AlarmManagerCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.UnsatisfiedLinkError;

import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.loader.FlutterLoader;
import io.flutter.plugin.common.JSONMethodCodec;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.view.FlutterCallbackInformation;
import io.flutter.plugin.common.EventChannel;

public class BackgroundService extends Service implements MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
    private static final String TAG = "BackgroundService";
    private static final String SMS_CHANNEL = "com.appinnovations.expense_tracker/sms_helper";
    private FlutterEngine backgroundEngine;
    private MethodChannel methodChannel;
    private EventChannel eventChannel;
    private DartExecutor.DartCallback dartCallback;
    private boolean isManuallyStopped = false;

    String notificationTitle = "Background Service";
    String notificationContent = "Running";
    String notificationMoneyOut = "";
    String notificationMoneyIn = "";
    private static final String LOCK_NAME = BackgroundService.class.getName()
            + ".Lock";
    public static volatile WakeLock lockStatic = null; // notice static

    synchronized public static PowerManager.WakeLock getLock(Context context) {
        if (lockStatic == null) {
            PowerManager mgr = (PowerManager) context
                    .getSystemService(Context.POWER_SERVICE);
            lockStatic = mgr.newWakeLock(PowerManager.FULL_WAKE_LOCK,
                    LOCK_NAME);
            lockStatic.setReferenceCounted(true);
        }
        return (lockStatic);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void enqueue(Context context) {
        Intent intent = new Intent(context, WatchdogReceiver.class);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent pIntent = PendingIntent.getBroadcast(context, 111, intent, flags);
        AlarmManagerCompat.setAndAllowWhileIdle(manager, AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, pIntent);
    }

    public void setAutoStartOnBootMode(boolean value) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putBoolean("auto_start_on_boot", value).apply();
    }

    public static boolean isAutoStartOnBootMode(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getBoolean("auto_start_on_boot", true);
    }

    public void setForegroundServiceMode(boolean value) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putBoolean("is_foreground", value).apply();
    }

    public static boolean isForegroundService(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getBoolean("is_foreground", true);
    }

    public void setManuallyStopped(boolean value) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putBoolean("is_manually_stopped", value).apply();
    }

    public static boolean isManuallyStopped(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getBoolean("is_manually_stopped", false);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        notificationContent = "Preparing";
        updateNotificationInfo();
    }

    @Override
    public void onDestroy() {
        if (!isManuallyStopped) {
            enqueue(this);
        } else {
            setManuallyStopped(true);
        }
        stopForeground(true);
        isRunning.set(false);

        if (backgroundEngine != null) {
            backgroundEngine.getServiceControlSurface().detachFromService();
            backgroundEngine.destroy();
            backgroundEngine = null;
        }

        methodChannel = null;
        eventChannel = null;
        dartCallback = null;
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Expense Tracker";
            String description = "Tracking your expenses!";

            int importance = NotificationManager.IMPORTANCE_MAX;
            NotificationChannel channel = new NotificationChannel("FOREGROUND_DEFAULT", name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    protected void updateNotificationInfo() {
        if (isForegroundService(this)) {

            String packageName = getApplicationContext().getPackageName();
            Intent i = getPackageManager().getLaunchIntentForPackage(packageName);

            int flags = PendingIntent.FLAG_CANCEL_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }

            PendingIntent pi = PendingIntent.getActivity(BackgroundService.this, 99778, i, flags);

            RemoteViews noti = new RemoteViews(getPackageName(),R.layout.expense_tracker_notify);

            noti.setTextViewText(R.id.money_in,notificationMoneyIn);
            noti.setTextViewText(R.id.money_out,notificationMoneyOut);

            // noti.setTextViewCompoundDrawablesRelative(R.id.money_in,R.drawable.savings,0,0,0);
            // noti.setTextViewCompoundDrawablesRelative(R.id.money_out,R.drawable.wallet,0,0,0);

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "FOREGROUND_DEFAULT")
                    .setSmallIcon(R.drawable.ic_bg_service_small)
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationContent)
                    .setCustomContentView(noti)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setSilent(false)
                    .setContentIntent(pi);
            Log.d("Notification","PN :: "+getPackageName());
            startForeground(99778, mBuilder.build());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setManuallyStopped(false);
        enqueue(this);
        runService();

        return START_STICKY;
    }

    AtomicBoolean isRunning = new AtomicBoolean(false);

    private void runService() {
        try {
            Log.d(TAG, "runService");
            if (isRunning.get() || (backgroundEngine != null && !backgroundEngine.getDartExecutor().isExecutingDart()))
                return;

            if (lockStatic == null){
                getLock(getApplicationContext()).acquire(10*60*1000L /*10 minutes*/);
            }

            updateNotificationInfo();

            SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
            long entrypointHandle = pref.getLong("entrypoint_handle", 0);

            FlutterLoader flutterLoader = FlutterInjector.instance().flutterLoader();
            // initialize flutter if it's not initialized yet
            if (!flutterLoader.initialized()) {
                flutterLoader.startInitialization(getApplicationContext());
            }

            flutterLoader.ensureInitializationComplete(getApplicationContext(), null);
            FlutterCallbackInformation callback = FlutterCallbackInformation.lookupCallbackInformation(entrypointHandle);
            if (callback == null) {
                Log.e(TAG, "callback handle not found");
                return;
            }

            isRunning.set(true);
            backgroundEngine = new FlutterEngine(this);
            backgroundEngine.getServiceControlSurface().attachToService(BackgroundService.this, null, isForegroundService(this));

            methodChannel = new MethodChannel(backgroundEngine.getDartExecutor().getBinaryMessenger(), "id.flutter/background_service_android_bg", JSONMethodCodec.INSTANCE);
            methodChannel.setMethodCallHandler(this);

            eventChannel = new EventChannel(backgroundEngine.getDartExecutor().getBinaryMessenger(), SMS_CHANNEL);
            eventChannel.setStreamHandler(this);

            dartCallback = new DartExecutor.DartCallback(getAssets(), flutterLoader.findAppBundlePath(), callback);
            backgroundEngine.getDartExecutor().executeDartCallback(dartCallback);
        } catch (UnsatisfiedLinkError e) {
            notificationContent = "Error " + e.getMessage();
            updateNotificationInfo();

            Log.w(TAG, "UnsatisfiedLinkError: After a reboot this may happen for a short period and it is ok to ignore then!" + e.getMessage());
        }
    }

    public void receiveData(JSONObject data) {
        if (methodChannel != null) {
            try {
                methodChannel.invokeMethod("onReceiveData", data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onListen(Object listener, EventChannel.EventSink eventSink) {
        SmsReceiver.eventSink = eventSink;
    }

    @Override
    public void onCancel(Object listener) {
    
    }


    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        String method = call.method;

        try {
            if (method.equalsIgnoreCase("getHandler")) {
                SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
                long backgroundHandle = pref.getLong("background_handle", 0);
                result.success(backgroundHandle);

                if (lockStatic != null) {
                    lockStatic.release();
                    lockStatic = null;
                }
                return;
            }

            if (method.equalsIgnoreCase("setNotificationInfo")) {
                JSONObject arg = (JSONObject) call.arguments;
                
                notificationTitle = arg.getString("title");
                notificationContent = arg.getString("content");
                notificationMoneyOut = arg.getString("money_out");
                notificationMoneyIn = arg.getString("money_in");
                updateNotificationInfo();
                result.success(true);
                
                return;
            }

            if (method.equalsIgnoreCase("setAutoStartOnBootMode")) {
                JSONObject arg = (JSONObject) call.arguments;
                boolean value = arg.getBoolean("value");
                setAutoStartOnBootMode(value);
                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("setForegroundMode")) {
                JSONObject arg = (JSONObject) call.arguments;
                boolean value = arg.getBoolean("value");
                setForegroundServiceMode(value);
                if (value) {
                    updateNotificationInfo();
                } else {
                    stopForeground(true);
                }

                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("stopService")) {
                isManuallyStopped = true;
                Intent intent = new Intent(this, WatchdogReceiver.class);

                int flags = PendingIntent.FLAG_CANCEL_CURRENT;
                if (SDK_INT >= Build.VERSION_CODES.S) {
                    flags |= PendingIntent.FLAG_MUTABLE;
                }

                PendingIntent pi = PendingIntent.getBroadcast(getApplicationContext(), 111, intent, flags);

                AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
                alarmManager.cancel(pi);
                stopSelf();
                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("sendData")) {
                LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
                Intent intent = new Intent("id.flutter/background_service");
                intent.putExtra("data", call.arguments.toString());
                manager.sendBroadcast(intent);
                result.success(true);
                return;
            }
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }

        result.notImplemented();
    }
}
