package com.ofit.app.workout

import android.service.notification.NotificationListenerService

/**
 * Present only so Android lets OpenFit see which music is playing (title, artist, art) and control
 * that app directly. It reads no notifications. The runner turns it on in Android's notification
 * access settings; OpenFit never does it for them.
 */
class MusicListenerService : NotificationListenerService()
