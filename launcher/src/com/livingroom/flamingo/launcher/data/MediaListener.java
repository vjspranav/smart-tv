package com.livingroom.flamingo.launcher.data;

import android.service.notification.NotificationListenerService;

/**
 * Exists so the launcher may read active media sessions (granted with tools/grant-launcher.sh). The system keeps it
 * bound, so it also hosts the remote-button reader.
 */
public class MediaListener extends NotificationListenerService {
    @Override public void onListenerConnected() { RemoteButtons.start(this); }

    @Override public void onListenerDisconnected() { RemoteButtons.stop(); }
}
