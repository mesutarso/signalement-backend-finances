package cd.gouv.finances.signalements.report.export;

public final class CsvRows {
    private CsvRows() { }

    public static void append(StringBuilder output, char separator, String... cells) {
        for (var index = 0; index < cells.length; index++) {
            if (index > 0) output.append(separator);
            var text = cells[index] == null ? "" : cells[index];
            var trimmed = text.stripLeading();
            if (!text.isEmpty() && ("\t\r\n".indexOf(text.charAt(0)) >= 0
                    || !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0)) {
                text = "'" + text;
            }
            output.append('"').append(text.replace("\"", "\"\"")).append('"');
        }
        output.append('\n');
    }
}
