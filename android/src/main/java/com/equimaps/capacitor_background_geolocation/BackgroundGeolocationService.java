package com.equimaps.capacitor_background_geolocation;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.getcapacitor.Logger;
import com.getcapacitor.android.BuildConfig;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.HashSet;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

// A bound and started service that is promoted to a foreground service when
// location updates have been requested and the main activity is stopped.
//
// When an activity is bound to this service, frequent location updates are
// permitted. When the activity is removed from the foreground, the service
// promotes itself to a foreground service, and location updates continue. When
// the activity comes back to the foreground, the foreground service stops, and
// the notification associated with that service is removed.
public class BackgroundGeolocationService extends Service {
    static final String ACTION_BROADCAST = (
            BackgroundGeolocationService.class.getPackage().getName() + ".broadcast"
    );
    private static final String TAG = BackgroundGeolocationService.class.getSimpleName();
    private final IBinder binder = new LocalBinder();

    // Must be unique for this application.
    private static final int NOTIFICATION_ID = 28351;

    //NEW LOCATION CODE
    private LocationRequest mLocationRequest;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    private Handler mServiceHandler;

    private class Watcher {
        public String id;
        public FusedLocationProviderClient client;
        public LocationRequest locationRequest;
        public LocationCallback locationCallback;
        public Notification backgroundNotification;
    }

    private HashSet<Watcher> watchers = new HashSet<Watcher>();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }


    private void getNotification(boolean startForeground) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the channel for the notification
            NotificationChannel mChannel =
                    new NotificationChannel(BackgroundGeolocationService.class.getPackage().getName(), "Background Tracking", NotificationManager.IMPORTANCE_DEFAULT);
            // Set the Notification Channel for the Notification Manager.
            mNotificationManager.createNotificationChannel(mChannel);
        }
        Intent intent = new Intent(this, BackgroundGeolocationService.class);
        try {


            // The PendingIntent that leads to a call to onStartCommand() in this service.
            PendingIntent servicePendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                servicePendingIntent = PendingIntent.getService(BackgroundGeolocationService.this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            } else {
                servicePendingIntent = PendingIntent.getService(BackgroundGeolocationService.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
            // The PendingIntent to launch activity.
            Intent intentActivity = getPackageManager().getLaunchIntentForPackage(
                    getPackageName()
            );

            PendingIntent activityPendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activityPendingIntent = PendingIntent.getActivity(BackgroundGeolocationService.this, 0, intentActivity, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            } else {
                activityPendingIntent = PendingIntent.getActivity(BackgroundGeolocationService.this, 0, intentActivity, PendingIntent.FLAG_UPDATE_CURRENT);
            }
            @SuppressLint("InlinedApi") NotificationCompat.Builder builder = new NotificationCompat.Builder(BackgroundGeolocationService.this, BackgroundGeolocationService.class.getPackage().getName())
                    .setContentIntent(activityPendingIntent)
                    .setContentText("Using your location")
                    .setContentTitle("Using your location")
                    .setOngoing(true)
                    .setPriority(NotificationManager.IMPORTANCE_HIGH)
                    .setWhen(System.currentTimeMillis());
            // Set the Channel ID for Android O.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(BackgroundGeolocationService.class.getPackage().getName()); // Channel ID
            }
            if (startForeground) {
                try {
                    startForeground(NOTIFICATION_ID, builder.build());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                mNotificationManager.notify(NOTIFICATION_ID, builder.build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRebind(Intent intent) {
        stopForeground(true);
        super.onRebind(intent);
    }

    // Some devices allow a foreground service to outlive the application's main
    // activity, leading to nasty crashes as reported in issue #59. If we learn
    // that the application has been killed, all watchers are stopped and the
    // service is terminated immediately.
    @Override
    public boolean onUnbind(Intent intent) {
        getNotification(true);
//        for (Watcher watcher : watchers) {
//            watcher.client.removeLocationUpdates(watcher.locationCallback);
//        }
//        watchers = new HashSet<Watcher>();
//        stopSelf();
        return true;
    }

    Notification getNotification() {
        for (Watcher watcher : watchers) {
            if (watcher.backgroundNotification != null) {
                return watcher.backgroundNotification;
            }
        }
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    Log.d("BACK_LOCATION", location.getLatitude() + ":" + location.getLongitude());
                }
                Intent intent = new Intent(ACTION_BROADCAST);
                intent.putExtra("location", location);
                intent.putExtra("id", watcherId);
                LocalBroadcastManager.getInstance(
                        getApplicationContext()
                ).sendBroadcast(intent);
                getNotification(true);
            }
        };
        mLocationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(10000)
                .build();

        Intent serviceIntent = new Intent(getApplicationContext(), BackgroundGeolocationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mServiceHandler = new Handler(handlerThread.getLooper());
    }

    public void requestLocationUpdates() {
        try {
            startService(new Intent(getApplicationContext(), BackgroundGeolocationService.class));
            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback, Looper.myLooper());
        } catch (SecurityException unlikely) {
            unlikely.printStackTrace();
        }
    }

    public void removeLocationUpdates() {
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            // stopSelf();
        } catch (SecurityException unlikely) {
            unlikely.printStackTrace();
        }
    }

    void onActivityStopped() {
        getNotification(true);
//            Notification notification = getNotification();
//            if (notification != null) {
//                try {
//                    // Android 12 has a bug
//                    // (https://issuetracker.google.com/issues/229000935)
//                    // whereby it mistakenly thinks the app is in the
//                    // foreground at this point, even though it is not. This
//                    // causes a ForegroundServiceStartNotAllowedException to be
//                    // raised, crashing the app unless we suppress it here.
//                    // See issue #86.
//                    startForeground(NOTIFICATION_ID, notification);
//                } catch (Exception exception) {
//                    Logger.error("Failed to start service", exception);
//                }
//            }
    }

    public void addWatcher(
            final String id,
            Notification backgroundNotification,
            float distanceFilter
    ) {
        watcherId = id;
        Watcher watcher = new Watcher();
        watcher.id = id;
        watcher.client = mFusedLocationClient;
        watcher.locationRequest = mLocationRequest;
        watcher.locationCallback = mLocationCallback;
        watcher.backgroundNotification = backgroundNotification;
        watchers.add(watcher);

        // According to Android Studio, this method can throw a Security Exception if
        // permissions are not yet granted. Rather than check the permissions, which is fiddly,
        // we simply ignore the exception.
//            try {
//                watcher.client.requestLocationUpdates(
//                        watcher.locationRequest,
//                        watcher.locationCallback,
//                        null
//                );
//            } catch (SecurityException ignore) {}
        requestLocationUpdates();
    }

    private String watcherId = "";

    // Handles requests from the activity.
    public class LocalBinder extends Binder {

        public BackgroundGeolocationService getService() {
            return BackgroundGeolocationService.this;
        }


        void removeWatcher(String id) {
            removeLocationUpdates();
//            for (Watcher watcher : watchers) {
//                if (watcher.id.equals(id)) {
//                    watcher.client.removeLocationUpdates(watcher.locationCallback);
//                    watchers.remove(watcher);
//                    if (getNotification() == null) {
//                        stopForeground(true);
//                    }
//                    return;
//                }
//            }
        }

        void onPermissionsGranted() {
            requestLocationUpdates();
            // If permissions were granted while the app was in the background, for example in
            // the Settings app, the watchers need restarting.
//            for (Watcher watcher : watchers) {
//                watcher.client.removeLocationUpdates(watcher.locationCallback);
//                watcher.client.requestLocationUpdates(
//                        watcher.locationRequest,
//                        watcher.locationCallback,
//                        null
//                );
//            }
        }

        void onActivityStarted() {
            stopForeground(true);
        }


        void stopService() {
//            BackgroundGeolocationService.this.stopSelf();
        }
    }
}
