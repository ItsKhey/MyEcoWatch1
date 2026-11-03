package com.example.myecowatch1.navigation;

import android.content.Context;
import android.content.Intent;

/**
 * Builds the intent a tapped system notification uses to reach the dashboard.
 *
 * <p>Notification creation lives in {@code :app:core}, but the destination
 * activity belongs to the {@code :app} shell. Keeping the routing contract here
 * lets both sides agree on the action and extras without the data layer
 * importing a UI class.</p>
 */
public final class NotificationRouting {

    public static final String ACTION_OPEN_NOTIFICATION =
            "com.example.myecowatch1.action.OPEN_NOTIFICATION";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    public static final String EXTRA_REPORT_ID = "report_id";
    public static final String EXTRA_TARGET_SCREEN = "targetScreen";
    public static final String TARGET_SCREEN_ANNOUNCEMENTS = "ANNOUNCEMENTS";

    private NotificationRouting() {
    }

    /**
     * @param announcement when true the dashboard opens the announcement feed
     *                     instead of trying to resolve a report id
     */
    public static Intent openNotification(
            Context context,
            String notificationId,
            String reportId,
            boolean announcement
    ) {
        Intent intent = new Intent();
        intent.setClassName(context.getPackageName(), AppNavigation.DASHBOARD_ACTIVITY);
        intent.setAction(ACTION_OPEN_NOTIFICATION);
        intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        intent.putExtra(EXTRA_REPORT_ID, reportId);
        if (announcement) {
            intent.putExtra(EXTRA_TARGET_SCREEN, TARGET_SCREEN_ANNOUNCEMENTS);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }
}
