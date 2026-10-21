package com.example.myecowatch1.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myecowatch1.R;
import com.example.myecowatch1.data.remote.EmailVerificationStatus;
import com.example.myecowatch1.data.repository.AuthRepository;
import com.example.myecowatch1.utils.AccountActivation;
import com.example.myecowatch1.utils.FirebaseEmailSession;
import com.example.myecowatch1.utils.PendingRegistrationStore;
import com.google.android.material.button.MaterialButton;

/**
 * The "I have opened the link" step of registration.
 *
 * <p>There is no code to type. Proving the address is done by opening the link
 * that was emailed, and this screen's only job is to ask the backend whether
 * that has happened yet. The account is created at the moment the answer turns
 * yes, so the resident never has to press a second button to finish signing up.
 *
 * <p>The registrationId and email are read from {@link PendingRegistrationStore}
 * rather than passed as arguments, so the step still works after the process has
 * been killed while the resident was in their email app.
 */
public class VerifyEmailFragment extends Fragment {

    private AuthRepository authRepository;
    private PendingRegistrationStore pendingStore;

    private TextView verifyEmailText;
    private MaterialButton checkVerificationBtn;
    private MaterialButton resendVerificationBtn;
    private ProgressBar progressBar;

    private String registrationId;
    private String email;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_verify_email, container, false);

        authRepository = new AuthRepository(requireActivity().getApplication());
        pendingStore = new PendingRegistrationStore(requireContext());

        verifyEmailText = view.findViewById(R.id.verifyEmailText);
        checkVerificationBtn = view.findViewById(R.id.checkVerificationBtn);
        resendVerificationBtn = view.findViewById(R.id.resendVerificationBtn);
        progressBar = view.findViewById(R.id.verifyProgressBar);
        TextView backToRegisterBtn = view.findViewById(R.id.backToRegisterBtn);

        registrationId = pendingStore.getRegistrationId();
        email = pendingStore.getEmail();

        if (email != null && !email.isEmpty()) {
            verifyEmailText.setText(email);
        }

        checkVerificationBtn.setOnClickListener(v -> checkVerification());
        resendVerificationBtn.setOnClickListener(v -> resendVerification());
        backToRegisterBtn.setOnClickListener(v -> {
            // Abandoning the registration, so the Firebase session is closed.
            FirebaseEmailSession.signOutQuietly();
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        /*
         * The resident may have just come back from their mail app having opened
         * the link, so the check runs automatically on every return to this
         * screen. It refreshes the Firebase session and asks the backend, which is
         * what makes the deep link feel instant.
         */
        if (hasRegistration()) {
            checkVerification();
        }

        updateResendButton();
    }

    private boolean hasRegistration() {
        return registrationId != null && !registrationId.isEmpty()
                && email != null && !email.isEmpty();
    }

    private void checkVerification() {
        if (!hasRegistration()) {
            showMissingRegistration();
            return;
        }

        if (isBusy()) {
            return;
        }

        setLoading(true);

        /*
         * Firebase owns the truth here. The local session is refreshed from the
         * server first, because emailVerified on the cached user does not update
         * on its own, and the resulting ID token is what the backend checks.
         */
        FirebaseEmailSession.confirmVerification(new FirebaseEmailSession.Callback() {
            @Override
            public void onSuccess() {
                // Unreachable; confirmation flows through onVerifiedToken.
            }

            @Override
            public void onError(String message, boolean keepSession) {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                if (!keepSession) {
                    // The registration is abandoned, so the session must not be
                    // left resting on the device.
                    FirebaseEmailSession.signOutQuietly();
                }
                toast(message);
            }

            @Override
            public void onNotYetVerified() {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                // Not an error. The resident may simply not have opened the link.
                toast("We have not seen your email opened yet. Open the link in your "
                        + "inbox, then tap this again.");
            }

            @Override
            public void onVerifiedToken(String idToken) {
                if (!isAdded()) {
                    return;
                }
                confirmWithBackend(idToken);
            }
        });
    }

    /**
     * Asks the backend to create the account.
     *
     * <p>This is the only decision that matters: the backend confirms the token
     * with Firebase and creates the resident. Nothing here trusts the token on
     * its own, and nothing here signs the resident in.
     */
    private void confirmWithBackend(String idToken) {
        authRepository.checkEmailVerification(registrationId, idToken,
                new AuthRepository.ApiCallback<EmailVerificationStatus>() {
                    @Override
                    public void onSuccess(EmailVerificationStatus status) {
                        if (!isAdded()) {
                            return;
                        }
                        setLoading(false);

                        // The account exists now, so the Firebase session has done
                        // its job and is closed. EcoWatch login is the only session
                        // from here on.
                        FirebaseEmailSession.signOutQuietly();

                        onVerified(status);
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded()) {
                            return;
                        }
                        setLoading(false);
                        toast(error);
                    }
                });
    }

    private void onVerified(EmailVerificationStatus status) {
        // The account now exists, so the stored handle has served its purpose and
        // must not be able to be replayed.
        pendingStore.clear();

        /*
         * Record the account for the sign-in screen's account picker, honouring the
         * "remember me on this device" choice made on the register form. Without
         * this the EMAIL_LINK build would silently produce accounts that cannot be
         * tapped to sign in, while the OTP build could.
         *
         * Done HERE rather than when the form was submitted, because this is the
         * first moment the account actually exists. Saving at submit time would
         * leave the picker offering an account for a registration that was abandoned
         * or never verified, and that entry could never sign in.
         */
        AccountActivation.remember(requireContext(), status.getUsername(), () -> {
            if (!isAdded()) {
                return;
            }
            // SessionManager is not otherwise touched: verification creates the
            // account, it does not sign anyone in. The resident goes to the login
            // screen as before.
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Email verified")
                    .setMessage(status.getMessage().isEmpty()
                            ? "Your EcoWatch account is ready. Sign in with your username and password."
                            : status.getMessage())
                    .setCancelable(false)
                    .setPositiveButton("Go to Login",
                            (dialog, which) -> goToLogin(status.getUsername()))
                    .show();
        });
    }

    private void goToLogin(String username) {
        if (getActivity() == null) {
            return;
        }

        android.content.Intent intent =
                new android.content.Intent(getActivity(), LoginActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (username != null && !username.isEmpty()) {
            // Prefilled so the resident does not have to remember which username
            // they chose. This is not a login shortcut: the password is still
            // required and is still checked by the backend exactly as before.
            intent.putExtra(LoginActivity.EXTRA_PREFILL_USERNAME, username);
        }

        startActivity(intent);

        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    /**
     * Asks Firebase to send the verification email again.
     *
     * <p>No backend call is involved. Firebase throttles repeated sends itself, so
     * there is no local cooldown to keep and the button is always available.
     */
    private void resendVerification() {
        if (isBusy()) {
            return;
        }

        setLoading(true);

        FirebaseEmailSession.resendVerificationEmail(new FirebaseEmailSession.Callback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                // The button stays live so the resident can try again if the email
                // does not arrive; Firebase decides the rate limit.
                updateResendButton();
                toast("We sent another verification link. Check your inbox and your "
                        + "spam folder.");
            }

            @Override
            public void onError(String message, boolean keepSession) {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                if (!keepSession) {
                    FirebaseEmailSession.signOutQuietly();
                }
                toast(message);
            }

            @Override
            public void onNotYetVerified() {
                // Unreachable on this path.
            }

            @Override
            public void onVerifiedToken(String idToken) {
                // Unreachable on this path.
            }
        });
    }

    private void showMissingRegistration() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Verification unavailable")
                .setMessage("We could not find the registration you started on this device. "
                        + "Please register again to get a new verification email.")
                .setCancelable(false)
                .setPositiveButton("Back", (dialog, which) -> {
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                })
                .show();
    }

    private void updateResendButton() {
        // Firebase applies its own rate limiting, so this button stays available
        // and the resident can try again if an email does not arrive.
        resendVerificationBtn.setText("Resend Verification Email");
        resendVerificationBtn.setEnabled(!isBusy());
    }

    private boolean isBusy() {
        return progressBar.getVisibility() == View.VISIBLE;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        checkVerificationBtn.setEnabled(!loading);
        resendVerificationBtn.setEnabled(!loading);
    }

    private void toast(String message) {
        if (message != null && !message.isEmpty() && getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }
}
