package cd.gouv.finances.signalements.ocr;

import java.util.List;

public enum ReadingQuality {
    READABLE,
    PARTIAL,
    UNREADABLE;

    public static ReadingQuality from(List<ExtractedField> fields) {
        boolean number = present(fields, DocumentField.DOCUMENT_NUMBER);
        boolean seller = present(fields, DocumentField.SELLER_TAX_ID) || present(fields, DocumentField.SELLER_NAME);
        boolean amount = present(fields, DocumentField.TOTAL_AMOUNT);
        if (number && seller && amount) {
            return READABLE;
        }
        return from(fields, false);
    }

    public static ReadingQuality from(List<ExtractedField> fields, boolean hasArticle) {
        boolean number = present(fields, DocumentField.DOCUMENT_NUMBER);
        boolean seller = present(fields, DocumentField.SELLER_TAX_ID) || present(fields, DocumentField.SELLER_NAME);
        boolean amount = present(fields, DocumentField.TOTAL_AMOUNT);
        if (number && seller && amount) {
            return READABLE;
        }
        boolean other = present(fields, DocumentField.DOCUMENT_DATE) || present(fields, DocumentField.READ_CODE);
        if (number || seller || amount || other || hasArticle) {
            return PARTIAL;
        }
        return UNREADABLE;
    }

    private static boolean present(List<ExtractedField> fields, DocumentField field) {
        return fields.stream()
                .filter(extracted -> extracted.name().equals(field.nameKey()))
                .anyMatch(ExtractedField::present);
    }
}
