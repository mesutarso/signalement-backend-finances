package cd.gouv.finances.signalements.ocr;

public record ExtractedField(String name, String value) {

    public boolean present() {
        return value != null && !value.isBlank();
    }
}
