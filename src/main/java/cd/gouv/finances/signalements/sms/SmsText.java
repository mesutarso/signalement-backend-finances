package cd.gouv.finances.signalements.sms;

public final class SmsText {
    public static final int LIMIT = 100;

    private SmsText() { }

    public static boolean valid(String text) {
        return text != null && !text.isBlank() && text.length() <= LIMIT
                && text.chars().noneMatch(Character::isISOControl);
    }
}
