package com.example.myecowatch1.navigation;

import android.content.Context;
import android.content.Intent;

/**
 * Cross-module navigation that cannot use a class literal.
 *
 * <p>The dashboard activity is owned by the {@code :app} shell, while the
 * screens that launch it live in feature modules. A direct {@code Class}
 * reference would make a feature depend on the shell and create a cycle, so the
 * component is resolved by its fully qualified name through the package's own
 * class loader.</p>
 */
public final class AppNavigation {

    /** Fully qualified name of the activity that hosts the dashboard. */
    public static final String DASHBOARD_ACTIVITY =
            "com.example.myecowatch1.ui.home.DashboardActivity";

    private AppNavigation() {
    }

    /** Opens the dashboard, whether or not the caller's activity is still around. */
    public static void launchDashboard(Context context) {
        Intent intent = new Intent();
        intent.setClassName(context.getPackageName(), DASHBOARD_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }
}
