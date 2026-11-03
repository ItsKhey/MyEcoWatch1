package com.example.myecowatch1.ui.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.myecowatch1.utils.EmailActionLink;

/**
 * Entry point for the emailed links.
 *
 * <p>Registration verification and password reset both continue at the same App
 * Link ({@code https://<project>.firebaseapp.com/finishSignUp}), because that is
 * the single continue URL the backend hands Firebase. This activity exists only
 * to read the {@code mode} parameter and forward the link to the screen that
 * actually needs it, so the two journeys can keep separate UI without asking
 * Firebase for two different URLs.
 *
 * <p>It has no layout of its own. It is declared {@code noHistory} and
 * {@code excludeFromRecents} so it never appears in the back stack: pressing back
 * from the screen it opened should leave the app, not land here.
 *
 * <p>Nothing is authenticated here. The activity forwards an opaque code and the
 * backend decides what it means.
 */
public class EmailActionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);

        route(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        route(intent);
    }

    /**
     * Decides which screen the incoming link belongs to.
     *
     * <p>Three shapes arrive here, and all must be handled:
     *
     * <ul>
     *   <li><b>The emailed redirector.</b> The link in the email is
     *       {@code /__/auth/links?link=<action URL>}, where the inner URL carries
     *       {@code mode} and {@code oobCode}. {@link EmailActionLink} unwraps it,
     *       so the routing below applies unchanged.
     *   <li><b>With parameters.</b> When Firebase redirects straight into the app
     *       (handleCodeInApp), the link carries {@code mode} and {@code oobCode}.
     *       {@code mode=resetPassword} opens the new-password screen, and anything
     *       else opens the verification screen.
     *   <li><b>With no parameters.</b> Firebase can instead complete the action on
     *       its own hosted page and then redirect to the continue URL carrying
     *       nothing at all. That is still a verification landing, so it must reach
     *       the verification screen rather than dead-ending.
     * </ul>
     *
     * <p>Registration verification deliberately does not depend on the link. The
     * app already holds the Firebase session, and it proves verification by
     * refreshing that session and presenting its token to the backend. The link
     * only has to bring the resident back to the app, which is why an empty landing
     * is routed rather than rejected.
     */
    private void route(Intent incoming) {
        if (EmailActionLink.isResetPassword(incoming)) {
            EmailActionLink.forwardTo(this, ResetPasswordActivity.class, incoming);
            finish();
            return;
        }

        // Either an explicit verifyEmail mode, an oobCode that is not a reset, or
        // a bare landing. All of them belong to the verification screen.
        EmailActionLink.forwardTo(this, EmailVerificationActivity.class, incoming);
        finish();
    }
}
