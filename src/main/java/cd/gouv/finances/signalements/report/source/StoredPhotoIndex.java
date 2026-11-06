package cd.gouv.finances.signalements.report.source;

public interface StoredPhotoIndex {

    boolean contains(String reportExternalId, String photoExternalId);
}
