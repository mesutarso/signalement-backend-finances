package cd.gouv.finances.signalements.ocr;

public enum DocumentField {
    DOCUMENT_TYPE("documentType"),
    DOCUMENT_NUMBER("documentNumber"),
    DOCUMENT_DATE("documentDate"),
    SELLER_TAX_ID("sellerTaxId"),
    SELLER_NAME("sellerName"),
    BUYER_TAX_ID("buyerTaxId"),
    TOTAL_AMOUNT("totalAmount"),
    CURRENCY("currency"),
    READ_CODE("readCode");

    private final String name;

    DocumentField(String name) {
        this.name = name;
    }

    public String nameKey() {
        return name;
    }
}
