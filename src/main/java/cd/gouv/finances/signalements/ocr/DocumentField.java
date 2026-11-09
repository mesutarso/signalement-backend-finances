package cd.gouv.finances.signalements.ocr;

public enum DocumentField {
    SELLER_NAME("sellerName"),
    SELLER_TAX_ID("sellerTaxId"),
    DOCUMENT_DATE("documentDate"),
    TOTAL_AMOUNT("totalAmount"),
    DOCUMENT_NUMBER("documentNumber"),
    READ_CODE("readCode"),
    CURRENCY("currency"),
    NET_AMOUNT("netAmount"),
    VAT_AMOUNT("vatAmount"),
    VAT_RATE("vatRate"),
    PAID_AMOUNT("paidAmount"),
    DEF_CODE("defCode"),
    DEF_NUMBER("defNumber");

    private final String name;

    DocumentField(String name) {
        this.name = name;
    }

    public String nameKey() {
        return name;
    }
}
