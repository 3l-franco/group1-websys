package enrollease.util;

/** Input checks reused by the controllers (same rules as the website's JavaScript). */
public class Validator {

    public static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    // Same pattern the website used: something@something.something
    public static boolean isValidEmail(String email) {
        return email != null && email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 6;
    }
}
