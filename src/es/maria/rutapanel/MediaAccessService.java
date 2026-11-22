package es.maria.rutapanel;

import android.service.notification.NotificationListenerService;

/** Only enables Android's MediaSession API. Notification contents are never read,
 * persisted, transmitted or used. No notification callbacks are implemented. */
public final class MediaAccessService extends NotificationListenerService { }
