package com.example.myecowatch1.navigation;

/**
 * Navigation actions the dashboard shell offers to the feature modules it
 * hosts.
 *
 * <p>Feature screens used to cast their host activity to the concrete
 * {@code DashboardActivity}. That class lives in the {@code :app} shell, so the
 * hard reference pointed the dependency arrow the wrong way. The shell now
 * implements this interface from {@code :app:core}, and features navigate
 * through it instead.</p>
 */
public interface DashboardNavigation {

    /** Selects the reports tab. */
    void navigateToReports();

    /** Pushes the announcement feed onto the dashboard's fragment container. */
    void navigateToAnnouncements();

    /** Refreshes the announcement feed, then opens it. */
    void openAnnouncementFeed();

    /** Selects the reports tab and opens the given report. */
    void openReportById(String reportId);

    /** Ends the session and returns to the login screen. */
    void signOutAndReturnToLogin(String message);
}
