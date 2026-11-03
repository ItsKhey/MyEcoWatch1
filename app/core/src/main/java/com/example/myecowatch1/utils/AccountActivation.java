package com.example.myecowatch1.utils;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.myecowatch1.data.models.User;
import com.example.myecowatch1.data.repository.AuthRepository;

/**
 * Leaves a newly created account on this device the way its owner asked at
 * registration, so that choosing it in the login screen's account dialog does the
 * right thing.
 *
 * <p>Registration creates an account but never signs anyone in, so there is no
 * session to remember. Remembering is therefore only half the job: without a
 * session, the account can appear in the dialog but can do nothing except fill in
 * a username. This is what turns a remembered account into one that signs in on
 * tap.
 *
 * <p>The session is obtained the only way the backend offers one: by performing
 * the sign-in itself, using the password the resident typed a moment ago and that
 * {@link PendingRegistrationCredentials} has been holding in memory. That sign-in
 * is then undone, so registering still does not log anybody in — the resident is
 * returned to the login screen either way, which is the only thing this class
 * promises.
 *
 * <p>Nothing here navigates. The caller supplies {@code onFinished}, which runs
 * exactly once, on the main thread, in every outcome including the ones where no
 * network call was made at all. Deciding where the resident goes next belongs to
 * the screen that is finishing.
 */
public final class AccountActivation {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AccountActivation() {
        // Utility class.
    }

    /**
     * Remembers a freshly created account, capturing a session for it when the
     * resident asked to be remembered.
     *
     * <p>Always succeeds from the resident's point of view. A token that cannot be
     * captured leaves the account remembered as a username, which is exactly how
     * an account registered without the box behaves. Registration genuinely
     * succeeded in every one of these cases, so none of them is reported as a
     * failure.
     *
     * @param username    the account that was just created
     * @param onFinished  runs on the main thread once there is nothing left to do
     */
    public static void remember(Context context, String username, Runnable onFinished) {
        Context appContext = context.getApplicationContext();

        // The held password is spent either way. Leaving it readable after the
        // capture has been attempted would widen its lifetime for nothing.
        String heldPassword = PendingRegistrationCredentials.password();
        PendingRegistrationCredentials.clear();

        if (username == null || username.trim().isEmpty()
                || heldPassword.isEmpty() || !(appContext instanceof Application)) {
            /*
             * Either the resident did not tick the box, or the process was reclaimed
             * while they were away in their email app. Both are ordinary outcomes
             * rather than failures, and both end the same way: the username is worth
             * remembering, it just cannot go further on its own.
             */
            SavedAccounts.remember(appContext, username);
            run(onFinished);
            return;
        }

        final SessionManager sessionManager = new SessionManager(appContext);
        final AuthRepository repository = new AuthRepository((Application) appContext);

        repository.login(username.trim(), heldPassword, new AuthRepository.ApiCallback<User>() {
            @Override
            public void onSuccess(User user) {
                // Read before the session is torn down below; this is the token the
                // server issued a moment ago.
                String token = sessionManager.fetchAuthToken();
                SavedAccounts.rememberWithSession(
                        appContext, username, user.getServerId(), token);

                // Registration is not a sign-in, so the session and the cached
                // profile that this capture left behind are dropped. Only the token
                // the account dialog needs survives.
                repository.discardSession();

                run(onFinished);
            }

            @Override
            public void onError(String error) {
                // The account exists regardless, so it stays remembered; only the
                // capture failed and the resident signs in by hand.
                SavedAccounts.remember(appContext, username);
                run(onFinished);
            }
        });
    }

    private static void run(Runnable action) {
        if (action != null) {
            MAIN.post(action);
        }
    }
}
