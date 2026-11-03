package com.example.myecowatch1.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;

/**
 * Reads the Firebase action link.
 *
 * <p>The verification link and the password reset link are minted from the same
 * {@link #CONTINUE_URL} because that is what the backend asks Firebase for.
 * Firebase does not put that URL in the email, though: it wraps it in
 * {@code /__/auth/links?link=<action URL>}, and the {@code mode} and
 * {@code oobCode} ride on the inner URL. The app claims that path as well, so one
 * intent can arrive for either journey and {@link #readMode} decides which.
 *
 * <p>The URL in the email is not the action itself. Firebase wraps it in its own
 * redirector, {@code https://<project>.firebaseapp.com/__/auth/links?link=<action>},
 * and the {@code mode} and {@code oobCode} live only on the inner
 * {@code /__/auth/action} URL. The app claims that redirector path too, so the
 * wrapper is unwrapped here instead of being handed to the browser.
 *
 * <p>Three ways the code can reach the app are supported:
 *
 * <ul>
 *   <li>as a query parameter on the wrapped link from the email, after the
 *       {@code link} parameter is unwrapped,
 *   <li>as a query parameter on the action link itself, either because Firebase
 *       produced that shape or because the continue URL was customised, and
 *   <li>as an intent extra, which is how {@link
 *       com.example.myecowatch1.ui.auth.EmailActionActivity} forwards it after
 *       deciding which screen the {@code mode} belongs to.
 * </ul>
 *
 * The oobCode is a credential, so it is never logged and never persisted.
 */
public final class EmailActionLink {

    /** Firebase's values for the {@code mode} query parameter. */
    public static final String MODE_RESET_PASSWORD = "resetPassword";
    public static final String MODE_VERIFY_EMAIL = "verifyEmail";

    private static final String QUERY_OOB_CODE = "oobCode";
    private static final String QUERY_MODE = "mode";
    private static final String QUERY_WRAPPED_LINK = "link";

    private static final String EXTRA_OOB_CODE = "ecowatch.extra.OOB_CODE";
    private static final String EXTRA_MODE = "ecowatch.extra.MODE";

    private EmailActionLink() {
    }

    /**
     * Returns the action code, or an empty string when the link carried none.
     *
     * <p>The extra is checked first so a value forwarded by the router is not
     * re-parsed out of a URI it did not come from.
     */
    @Nullable
    public static String readOobCode(Intent intent) {
        if (intent == null) {
            return null;
        }

        String forwarded = intent.getStringExtra(EXTRA_OOB_CODE);
        if (!TextUtils.isEmpty(forwarded)) {
            return forwarded.trim();
        }

        return readQueryParameter(intent, QUERY_OOB_CODE);
    }

    /**
     * Returns {@link #MODE_RESET_PASSWORD} or {@link #MODE_VERIFY_EMAIL}, or an
     * empty string when the link named neither.
     */
    @Nullable
    public static String readMode(Intent intent) {
        if (intent == null) {
            return null;
        }

        String forwarded = intent.getStringExtra(EXTRA_MODE);
        if (!TextUtils.isEmpty(forwarded)) {
            return forwarded;
        }

        return readQueryParameter(intent, QUERY_MODE);
    }

    @Nullable
    private static String readQueryParameter(Intent intent, String name) {
        Uri action = resolveActionUri(intent);
        if (action == null) {
            return null;
        }

        String value = action.getQueryParameter(name);
        return TextUtils.isEmpty(value) ? null : value.trim();
    }

    /**
     * Returns the URI that actually carries the action parameters.
     *
     * <p>Firebase's redirector keeps the parameters one level down: the emailed
     * URL is {@code /__/auth/links?link=<encoded action URL>}, and only that inner
     * URL names a {@code mode} or carries an {@code oobCode}. Reading the wrapper
     * without unwrapping it therefore finds neither, which is indistinguishable
     * from a continue-URL landing that finished on Firebase's hosted page.
     *
     * <p>Anything that is not a wrapper is returned unchanged, so an action link
     * and a bare continue URL behave exactly as before.
     */
    @Nullable
    private static Uri resolveActionUri(Intent intent) {
        Uri data = intent.getData();
        if (data == null) {
            return null;
        }

        String wrapped = data.getQueryParameter(QUERY_WRAPPED_LINK);
        if (TextUtils.isEmpty(wrapped)) {
            return data;
        }

        Uri inner = Uri.parse(wrapped);
        // A wrapper that does not contain a URI is not one; keep the outer link
        // rather than losing whatever it may have carried.
        return inner.isHierarchical() ? inner : data;
    }

    public static boolean isResetPassword(Intent intent) {
        return MODE_RESET_PASSWORD.equals(readMode(intent));
    }

    public static boolean isVerifyEmail(Intent intent) {
        return MODE_VERIFY_EMAIL.equals(readMode(intent));
    }

    /**
     * Carries the action code from the incoming link onto the screen that needs
     * it.
     *
     * <p>Only the opaque code crosses this boundary. Nothing about the resident
     * is attached, and no password is ever present in the intent.
     */
    public static void forwardTo(Context context, Class<?> target, Intent incoming) {
        Intent intent = new Intent(context, target);

        String oobCode = readOobCode(incoming);
        if (oobCode != null) {
            intent.putExtra(EXTRA_OOB_CODE, oobCode);
        }

        String mode = readMode(incoming);
        if (mode != null) {
            intent.putExtra(EXTRA_MODE, mode);
        }

        context.startActivity(intent);
    }
}
