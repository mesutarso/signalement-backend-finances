package cd.gouv.finances.signalements.report.source;

public record SourcedPhoto(String externalId, String contentType, byte[] content, boolean alreadyStored) {

    public SourcedPhoto(String externalId, String contentType, byte[] content) {
        this(externalId, contentType, content, false);
    }
}
