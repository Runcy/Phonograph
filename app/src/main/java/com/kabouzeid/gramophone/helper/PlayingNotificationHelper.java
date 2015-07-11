package com.kabouzeid.gramophone.helper;

/**
 * @author Karim Abou Zeid (kabouzeid)
 */

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v7.graphics.Palette;
import android.view.View;
import android.widget.RemoteViews;

import com.kabouzeid.gramophone.R;
import com.kabouzeid.gramophone.model.Song;
import com.kabouzeid.gramophone.service.MusicService;
import com.kabouzeid.gramophone.ui.activities.MainActivity;
import com.kabouzeid.gramophone.util.ColorUtil;
import com.kabouzeid.gramophone.util.MusicUtil;
import com.kabouzeid.gramophone.util.PreferenceUtil;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import com.nostra13.universalimageloader.core.assist.ViewScaleType;
import com.nostra13.universalimageloader.core.imageaware.ImageAware;
import com.nostra13.universalimageloader.core.imageaware.NonViewAware;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;
import com.nostra13.universalimageloader.core.process.BitmapProcessor;

public class PlayingNotificationHelper {

    public static final String TAG = PlayingNotificationHelper.class.getSimpleName();
    public static final int NOTIFICATION_ID = 1337;
    public static final String ACTION_NOTIFICATION_COLOR_PREFERENCE_CHANGED = "com.kabouzeid.gramophone.NOTIFICATION_COLOR_PREFERENCE_CHANGED";
    public static final String EXTRA_NOTIFICATION_COLORED = "com.kabouzeid.gramophone.EXTRA_NOTIFICATION_COLORED";

    @NonNull
    private final MusicService service;

    @NonNull
    private final NotificationManager notificationManager;
    @Nullable
    private Notification notification;

    private RemoteViews notificationLayout;
    private RemoteViews notificationLayoutExpanded;

    private Song currentSong;
    private boolean isPlaying;

    private boolean isDark;
    private boolean isColored;
    private boolean isReceiverRegistered;
    private boolean isNotificationShown;

    private ImageAware notificationImageAware;

    @NonNull
    final IntentFilter intentFilter;

    public PlayingNotificationHelper(@NonNull final MusicService service) {
        this.service = service;
        notificationManager = (NotificationManager) service
                .getSystemService(Context.NOTIFICATION_SERVICE);

        intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_NOTIFICATION_COLOR_PREFERENCE_CHANGED);

        int bigNotificationImageSize = service.getResources().getDimensionPixelSize(R.dimen.notification_big_image_size);
        notificationImageAware = new NonViewAware(new ImageSize(bigNotificationImageSize, bigNotificationImageSize), ViewScaleType.CROP);
    }

    @NonNull
    private BroadcastReceiver notificationColorPreferenceChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (intent.getAction().equals(ACTION_NOTIFICATION_COLOR_PREFERENCE_CHANGED)) {
                boolean isColored = intent.getBooleanExtra(EXTRA_NOTIFICATION_COLORED, false);
                if (isNotificationShown && PlayingNotificationHelper.this.isColored != isColored) {
                    updateNotification(isColored);
                }
            }
        }
    };

    public void updateNotification() {
        updateNotification(PreferenceUtil.getInstance(service).coloredNotification());
    }

    private void updateNotification(final boolean isColored) {
        Song song = service.getCurrentSong();
        if (song.id == -1) {
            service.stopForeground(true);
            return;
        }
        this.isColored = isColored;
        currentSong = song;
        this.isPlaying = service.isPlaying();
        if (!isReceiverRegistered)
            service.registerReceiver(notificationColorPreferenceChangedReceiver, intentFilter);
        isReceiverRegistered = true;
        isNotificationShown = true;

        notificationLayout = new RemoteViews(service.getPackageName(), R.layout.notification);
        notificationLayoutExpanded = new RemoteViews(service.getPackageName(), R.layout.notification_big);

        notification = new NotificationCompat.Builder(service)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(getOpenMusicControllerPendingIntent())
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContent(notificationLayout)
                .build();

        notification.bigContentView = notificationLayoutExpanded;

        setUpCollapsedLayout();
        setUpExpandedLayout();
        loadAlbumArt();
        setUpPlaybackActions();
        setUpExpandedPlaybackActions();

        service.startForeground(NOTIFICATION_ID, notification);
    }

    private PendingIntent getOpenMusicControllerPendingIntent() {
        return PendingIntent.getActivity(service, 0, new Intent(service, MainActivity.class), 0);
    }

    private void setUpExpandedPlaybackActions() {
        notificationLayoutExpanded.setOnClickPendingIntent(R.id.action_play_pause,
                retrievePlaybackActions(1));

        notificationLayoutExpanded.setOnClickPendingIntent(R.id.action_next,
                retrievePlaybackActions(2));

        notificationLayoutExpanded.setOnClickPendingIntent(R.id.action_prev,
                retrievePlaybackActions(3));

        notificationLayoutExpanded.setOnClickPendingIntent(R.id.action_quit,
                retrievePlaybackActions(4));

        notificationLayoutExpanded.setImageViewResource(R.id.action_play_pause, getPlayPauseRes());
    }

    private void setUpPlaybackActions() {
        notificationLayout.setOnClickPendingIntent(R.id.action_play_pause,
                retrievePlaybackActions(1));

        notificationLayout.setOnClickPendingIntent(R.id.action_next,
                retrievePlaybackActions(2));

        notificationLayout.setOnClickPendingIntent(R.id.action_prev,
                retrievePlaybackActions(3));

        notificationLayout.setImageViewResource(R.id.action_play_pause, getPlayPauseRes());
    }

    private PendingIntent retrievePlaybackActions(final int which) {
        Intent action;
        PendingIntent pendingIntent;
        final ComponentName serviceName = new ComponentName(service, MusicService.class);
        switch (which) {
            case 1:
                action = new Intent(MusicService.ACTION_TOGGLE_PLAYBACK);
                action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(service, 1, action, 0);
                return pendingIntent;
            case 2:
                action = new Intent(MusicService.ACTION_SKIP);
                action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(service, 2, action, 0);
                return pendingIntent;
            case 3:
                action = new Intent(MusicService.ACTION_REWIND);
                action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(service, 3, action, 0);
                return pendingIntent;
            case 4:
                action = new Intent(MusicService.ACTION_QUIT);
                action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(service, 4, action, 0);
                return pendingIntent;
            default:
                break;
        }
        return null;
    }

    private void setUpCollapsedLayout() {
        if (currentSong != null) {
            notificationLayout.setTextViewText(R.id.title, currentSong.title);
            notificationLayout.setTextViewText(R.id.text, currentSong.artistName);
            notificationLayout.setTextViewText(R.id.text2, currentSong.albumName);
        }
    }

    private void setUpExpandedLayout() {
        if (currentSong != null) {
            notificationLayoutExpanded.setTextViewText(R.id.title, currentSong.title);
            notificationLayoutExpanded.setTextViewText(R.id.text, currentSong.artistName);
            notificationLayoutExpanded.setTextViewText(R.id.text2, currentSong.albumName);
        }
    }

    private void loadAlbumArt() {
        ImageLoader.getInstance().cancelDisplayTask(notificationImageAware);
        ImageLoader.getInstance().displayImage(
                MusicUtil.getSongImageLoaderString(currentSong),
                notificationImageAware,
                new DisplayImageOptions.Builder()
                        .postProcessor(new BitmapProcessor() {
                            @Override
                            public Bitmap process(Bitmap bitmap) {
                                setAlbumArt(bitmap);
                                return bitmap;
                            }
                        }).build(),
                new SimpleImageLoadingListener() {
                    @Override
                    public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                        if (loadedImage == null) {
                            onLoadingFailed(imageUri, view, null);
                        }
                    }

                    @Override
                    public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                        setAlbumArt(null);
                    }
                });
    }

    private void setAlbumArt(@Nullable Bitmap albumArt) {
        boolean backgroundColorSet = false;
        if (albumArt != null) {
            notificationLayout.setImageViewBitmap(R.id.icon, albumArt);
            notificationLayoutExpanded.setImageViewBitmap(R.id.icon, albumArt);
            if (isColored) {
                Palette.Swatch vibrantSwatch = Palette.from(albumArt).resizeBitmapSize(100).generate().getVibrantSwatch();
                if (vibrantSwatch != null) {
                    int bgColor = vibrantSwatch.getRgb();
                    setBackgroundColor(bgColor);
                    setNotificationTextDark(ColorUtil.useDarkTextColorOnBackground(bgColor));
                    backgroundColorSet = true;
                }
            }
        } else {
            notificationLayout.setImageViewResource(R.id.icon, R.drawable.default_album_art);
            notificationLayoutExpanded.setImageViewResource(R.id.icon, R.drawable.default_album_art);
        }

        if (!backgroundColorSet) {
            setBackgroundColor(Color.TRANSPARENT);
            setNotificationTextDark(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
        }

        if (notification != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void setBackgroundColor(int color) {
        notificationLayout.setInt(R.id.root, "setBackgroundColor", color);
        notificationLayoutExpanded.setInt(R.id.root, "setBackgroundColor", color);
    }

    public void killNotification() {
        if (isReceiverRegistered)
            service.unregisterReceiver(notificationColorPreferenceChangedReceiver);
        isReceiverRegistered = false;
        service.stopForeground(true);
        notification = null;
        isNotificationShown = false;
    }

    public void updatePlayState(final boolean setPlaying) {
        isPlaying = setPlaying;

        if (notification == null) {
            updateNotification();
        }
        int playPauseRes = getPlayPauseRes();
        if (notificationLayout != null) {
            notificationLayout.setImageViewResource(R.id.action_play_pause, playPauseRes);
        }
        if (notificationLayoutExpanded != null) {
            notificationLayoutExpanded.setImageViewResource(R.id.action_play_pause, playPauseRes);
        }
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void setNotificationTextDark(boolean setDark) {
        isDark = setDark;

        if (notificationLayout != null && notificationLayoutExpanded != null) {
            int darkContentColor = service.getResources().getColor(R.color.notification_dark_text_content_color);
            int darkContentSecondaryColor = service.getResources().getColor(R.color.notification_dark_text_secondary_content_color);
            int contentColor = service.getResources().getColor(R.color.notification_content_color);
            int contentSecondaryColor = service.getResources().getColor(R.color.notification_secondary_content_color);

            notificationLayout.setTextColor(R.id.title, setDark ? darkContentColor : contentColor);
            notificationLayout.setTextColor(R.id.text, setDark ? darkContentSecondaryColor : contentSecondaryColor);
            notificationLayout.setImageViewResource(R.id.action_prev, setDark ? R.drawable.ic_skip_previous_black_36dp : R.drawable.ic_skip_previous_white_36dp);
            notificationLayout.setImageViewResource(R.id.action_play_pause, getPlayPauseRes());
            notificationLayout.setImageViewResource(R.id.action_next, setDark ? R.drawable.ic_skip_next_black_36dp : R.drawable.ic_skip_next_white_36dp);

            notificationLayoutExpanded.setTextColor(R.id.title, setDark ? darkContentColor : contentColor);
            notificationLayoutExpanded.setTextColor(R.id.text, setDark ? darkContentSecondaryColor : contentSecondaryColor);
            notificationLayoutExpanded.setTextColor(R.id.text2, setDark ? darkContentSecondaryColor : contentSecondaryColor);
            notificationLayoutExpanded.setImageViewResource(R.id.action_prev, setDark ? R.drawable.ic_skip_previous_black_36dp : R.drawable.ic_skip_previous_white_36dp);
            notificationLayoutExpanded.setImageViewResource(R.id.action_play_pause, getPlayPauseRes());
            notificationLayoutExpanded.setImageViewResource(R.id.action_next, setDark ? R.drawable.ic_skip_next_black_36dp : R.drawable.ic_skip_next_white_36dp);
            notificationLayoutExpanded.setImageViewResource(R.id.action_quit, setDark ? R.drawable.ic_close_black_24dp : R.drawable.ic_close_white_24dp);
        }
    }

    private int getPlayPauseRes() {
        return isPlaying ?
                (isDark ? R.drawable.ic_pause_black_36dp : R.drawable.ic_pause_white_36dp) :
                (isDark ? R.drawable.ic_play_arrow_black_36dp : R.drawable.ic_play_arrow_white_36dp);
    }
}
