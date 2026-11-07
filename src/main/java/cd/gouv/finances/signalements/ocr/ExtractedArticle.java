package cd.gouv.finances.signalements.ocr;

public record ExtractedArticle(String designation, String amount) {

    public boolean present() {
        return text(designation) || text(amount);
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }
}
