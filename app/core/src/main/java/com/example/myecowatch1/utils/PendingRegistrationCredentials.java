package com.example.myecowatch1.utils;

/**
 * The password a resident typed while registering, held only until the account is
 * created.
 *
 * <p><b>Memory only, and never written to disk.</b> {@link PendingRegistrationStore}
 * deliberately persists nothing sensitive, so this is the one place the password
 * lives, and it lives only for as long as the process does. The value is cleared
 * the moment it is used, and it is simply absent if Android reclaimed the process
 * while the resident was in their email app — which is an ordinary outcome, not a
 * failure, and the account is simply remembered without a session.
 *
 * <p>It exists for one reason: "remember me on this device" cannot save a session
 * for an account that has never been signed into, and registration never signs
 * anyone in. Without this, a brand-new account could only appear in the account
 * dialog as a username to fill in; it could never be one that signs in on tap.
 *
 * <p>Why the password is needed at all, rather than the verification token: the
 * EcoWatch session is a JWT the backend issues only from a successful
 * {@code /auth/login}, and a code that merely proved the address cannot stand in
 * for it. So the one legitimate way to obtain the session is to perform the
 * sign-in, and the resident's own password from a moment ago is what that takes.
 *
 * <p>Held only when the resident ticked the box. An account registered without it
 * keeps no trace of anything on this device beyond the username, exactly as
 * before.
 *
 * <p>Static because this has to outlive the register screen: the account is
 * finished on a different step, reached after the register one is gone.
 */
public final class PendingRegistrationCredentials {

    private static volatile String username;
    private static volatile String password;

    private PendingRegistrationCredentials() {
        // Utility class.
    }

    /**
     * Remembers the credentials until the account is created.
     *
     * <p>A blank username or password is treated as "nothing to hold": the
     * activation step then falls back to remembering the username alone rather
     * than attempting a sign-in that could only fail.
     */
    public static void hold(String username, String password) {
        String name = username == null ? "" : username.trim();
        String secret = password == null ? "" : password;
        if (name.isEmpty() || secret.isEmpty()) {
            clear();
            return;
        }
        PendingRegistrationCredentials.username = name;
        PendingRegistrationCredentials.password = secret;
    }

    /** The username held for activation, or an empty string when there is none. */
    public static String username() {
        return username == null ? "" : username;
    }

    /** The password held for activation, or an empty string when there is none. */
    public static String password() {
        return password == null ? "" : password;
    }

    public static boolean hasCredentials() {
        return !username().isEmpty() && !password().isEmpty();
    }

    /**
     * Drops the held password.
     *
     * <p>Called as soon as the credentials have been consumed, and on the way out
     * of a failed registration, so the window during which it is readable is as
     * short as the sign-in itself.
     */
    public static void clear() {
        username = null;
        password = null;
    }
}
