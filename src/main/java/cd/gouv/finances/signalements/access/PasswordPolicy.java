package cd.gouv.finances.signalements.access;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static boolean accepts(String password, String username) {
        if (password == null || username == null) {
            return false;
        }
        if (password.length() < 8 || password.length() > 72) {
            return false;
        }
        if (password.equalsIgnoreCase(username)) {
            return false;
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        return letter && digit;
    }
}
