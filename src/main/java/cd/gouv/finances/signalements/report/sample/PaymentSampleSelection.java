package cd.gouv.finances.signalements.report.sample;

import java.util.ArrayList;
import java.util.List;

public final class PaymentSampleSelection {

    private PaymentSampleSelection() {
    }

    public static int size(int count) {
        if (count <= 0) {
            return 0;
        }
        return (count * 5 + 99) / 100;
    }

    public static List<Integer> indexes(int count) {
        var picked = size(count);
        var indexes = new ArrayList<Integer>(picked);
        for (var index = 0; index < picked; index++) {
            indexes.add(index * count / picked);
        }
        return indexes;
    }
}
