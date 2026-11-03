package com.example.myecowatch1.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myecowatch1.core.R;
import com.example.myecowatch1.data.remote.ServerConfig;
import com.example.myecowatch1.utils.AccountActivation;
import com.example.myecowatch1.utils.PendingRegistrationCredentials;
import com.example.myecowatch1.utils.PendingRegistrationStore;
import com.example.myecowatch1.utils.SavedAccounts;

/**
 * The sign-up form.
 *
 * <p>Two verification transports exist and the build picks one:
 *
 * <ul>
 *   <li><b>OTP</b> (the default) — the backend emails a 4-digit code, which the
 *       resident types into the panel this screen shows. No Firebase is
 *       contacted.</li>
 *   <li><b>EMAIL_LINK</b> — the backend stages a Firebase verification identity
 *       and the resident follows a link in their mail, so the registrationId is
 *       persisted and they are moved to {@link EmailVerificationActivity}, which
 *       is also where the emailed link brings them back to. The process can be
 *       killed while the resident is away in their mail app.</li>
 * </ul>
 */
public class RegisterFragment extends Fragment {

    private static final String STATE_NAVIGATED = "register_navigated";

    private EditText regFirstNameInput, regLastNameInput, regUsernameInput, regEmailInput,
            regPhoneNumberInput, regPasswordInput, regConfirmPasswordInput;
    private TextInputLayout regPhoneNumberLayout;
    private EditText verificationCodeInput;
    private View inputContainer;
    private View verificationContainer;
    private TextView verificationPrompt;
    private Button registerBtn;
    private MaterialCheckBox rememberMeCheckBox;
    private Button verifyBtn;
    private TextView backToRegisterBtn;
    private ProgressBar progressBar;
    private RegisterViewModel viewModel;
    private PendingRegistrationStore pendingStore;

    /** Survives rotation so a delivered LiveData cannot navigate twice. */
    private boolean navigated;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_register, container, false);

        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);
        pendingStore = new PendingRegistrationStore(requireContext());

        if (savedInstanceState != null) {
            navigated = savedInstanceState.getBoolean(STATE_NAVIGATED, false);
        }

        inputContainer = view.findViewById(R.id.inputContainer);
        verificationContainer = view.findViewById(R.id.verificationContainer);
        verificationPrompt = view.findViewById(R.id.verificationPrompt);
        verificationCodeInput = view.findViewById(R.id.verificationCodeInput);
        verifyBtn = view.findViewById(R.id.verifyBtn);
        backToRegisterBtn = view.findViewById(R.id.backToRegisterBtn);

        regFirstNameInput = view.findViewById(R.id.regFirstNameInput);
        regLastNameInput = view.findViewById(R.id.regLastNameInput);
        regUsernameInput = view.findViewById(R.id.regUsernameInput);
        regEmailInput = view.findViewById(R.id.regEmailInput);
        regPhoneNumberLayout = view.findViewById(R.id.regPhoneNumberLayout);
        regPhoneNumberInput = view.findViewById(R.id.regPhoneNumberInput);
        regPasswordInput = view.findViewById(R.id.regPasswordInput);
        regConfirmPasswordInput = view.findViewById(R.id.regConfirmPasswordInput);
        registerBtn = view.findViewById(R.id.registerBtn);
        progressBar = view.findViewById(R.id.registerProgressBar);
        TextView toLoginBtn = view.findViewById(R.id.toLoginBtn);
        rememberMeCheckBox = view.findViewById(R.id.rememberMeCheckbox);

        registerBtn.setOnClickListener(v -> {
            // Checked here as well as on the server so the resident is told what
            // is wrong without waiting for a round trip.
            String phoneNumber = regPhoneNumberInput.getText().toString().trim();
            if (phoneNumber.isEmpty()) {
                regPhoneNumberLayout.setError("Phone number is required.");
                return;
            }
            regPhoneNumberLayout.setError(null);

            /*
             * The password is held in memory only, and only when the resident asked
             * to be remembered. Registration creates no session, so there would
             * otherwise be nothing for the account dialog to sign in with later, and
             * a brand-new account could only ever fill in a username.
             */
            if (rememberMeCheckBox != null && rememberMeCheckBox.isChecked()) {
                PendingRegistrationCredentials.hold(
                        regUsernameInput.getText().toString().trim(),
                        regPasswordInput.getText().toString());
            } else {
                PendingRegistrationCredentials.clear();
            }

            viewModel.register(
                    regFirstNameInput.getText().toString().trim(),
                    regLastNameInput.getText().toString().trim(),
                    regUsernameInput.getText().toString().trim(),
                    regEmailInput.getText().toString().trim(),
                    phoneNumber,
                    regPasswordInput.getText().toString().trim(),
                    regConfirmPasswordInput.getText().toString().trim());
        });

        verifyBtn.setOnClickListener(v -> viewModel.verifyOTP(
                valueOrEmpty(viewModel.getRegistrationId().getValue()),
                verificationCodeInput.getText().toString().trim()));

        backToRegisterBtn.setOnClickListener(v -> {
            inputContainer.setVisibility(View.VISIBLE);
            verificationContainer.setVisibility(View.GONE);
        });

        toLoginBtn.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        // Mirrors the server policy so the guidance appears while typing.
        regPasswordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String password = s.toString();
                String problem = RegisterViewModel.describePasswordProblem(password);
                regPasswordInput.setError(problem);
            }
        });

        observeViewModel(view);

        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(STATE_NAVIGATED, navigated);
        super.onSaveInstanceState(outState);
    }

    private void observeViewModel(View view) {
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                setLoading(Boolean.TRUE.equals(loading)));

        viewModel.getRegistrationId().observe(getViewLifecycleOwner(), registrationId -> {
            if (registrationId == null || registrationId.isEmpty()) {
                return;
            }

            if (ServerConfig.USES_LOCAL_OTP_REGISTRATION) {
                // The account is staged behind a code, so it does not exist yet and
                // is not worth remembering until the resident has verified it.
                showOtpPanel(registrationId);
                return;
            }

            if (navigated) {
                return;
            }
            navigated = true;
            openVerificationScreen(registrationId);
        });

        // Fires for a direct registration, where the account exists as soon as
        // the request returns and no code is involved, and for an OTP one, where
        // it exists once the emailed code has been entered.
        viewModel.getVerified().observe(getViewLifecycleOwner(), verified -> {
            if (!Boolean.TRUE.equals(verified) || navigated) {
                return;
            }
            // Set before the work starts, not on the way out of it: the capture
            // below is a network call, and a rotation in the middle of it would
            // otherwise start a second one for an account that already exists.
            navigated = true;
            onAccountCreated();
        });

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                // There will be no account to activate, so the password held for
                // that purpose has nothing left to do.
                PendingRegistrationCredentials.clear();
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * The account now exists. What happens next is decided by the "remember me on
     * this device" box on the form.
     *
     * <p>Checked, the account is left on this device as something that signs in on
     * tap, so choosing it in the login screen's account dialog goes straight
     * through to the dashboard. Unchecked, it is remembered exactly as it always
     * was: a username to fill in, with the password typed by hand.
     *
     * <p>Either way the resident is returned to the login screen rather than signed
     * in. Registering is not signing in, and this keeps username-and-password the
     * single ordinary way into the app.
     */
    private void onAccountCreated() {
        if (rememberMeCheckBox == null || !rememberMeCheckBox.isChecked()) {
            // The resident said this device should keep nothing beyond the
            // username, so none of the capture machinery is entered at all.
            PendingRegistrationCredentials.clear();
            SavedAccounts.remember(requireContext(), usernameChosen());
            goBackToLogin();
            return;
        }

        Toast.makeText(getContext(), "Account created! Setting up your sign-in...",
                Toast.LENGTH_SHORT).show();
        setLoading(true);

        AccountActivation.remember(requireContext(), usernameChosen(), () -> {
            if (!isAdded()) {
                return;
            }
            setLoading(false);
            goBackToLogin();
        });
    }

    /**
     * Returns to the login screen, which pre-fills the username so the resident does
     * not have to remember which one they chose.
     *
     * <p>This is not a login shortcut: the password is still required, and is still
     * checked by the backend exactly as before.
     */
    private void goBackToLogin() {
        Toast.makeText(getContext(), "Account created! Please login.", Toast.LENGTH_LONG).show();
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    /** Swaps the form for the code entry, keeping the address the code went to. */
    private void showOtpPanel(String registrationId) {
        String email = valueOrEmpty(viewModel.getRegisteredEmail().getValue());
        verificationPrompt.setText(email.isEmpty()
                ? "Enter the 4-digit code sent to your email."
                : "Enter the 4-digit code sent to " + email + ".");
        inputContainer.setVisibility(View.GONE);
        verificationContainer.setVisibility(View.VISIBLE);
        verificationCodeInput.requestFocus();
    }

    private void openVerificationScreen(String registrationId) {
        String email = valueOrEmpty(viewModel.getRegisteredEmail().getValue());
        String username = valueOrEmpty(viewModel.getRegisteredUsername().getValue());

        // Persisted before navigating: this is the only record that ties the
        // emailed link back to a registration, and the process may be killed while
        // the resident is in their mail app.
        pendingStore.save(registrationId, email, username);

        Intent intent = new Intent(requireContext(), EmailVerificationActivity.class);
        startActivity(intent);

        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * The username this form submitted.
     *
     * <p>Read from the still-attached input rather than the ViewModel, because the
     * verification result arrives after the form may have been torn down on a
     * rotation.
     */
    private String usernameChosen() {
        if (regUsernameInput == null) {
            return "";
        }
        return regUsernameInput.getText().toString().trim();
    }

    private void setLoading(boolean loading) {
        if (progressBar == null) {
            return;
        }
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        registerBtn.setEnabled(!loading);
        if (verifyBtn != null) {
            verifyBtn.setEnabled(!loading);
        }
    }
}
