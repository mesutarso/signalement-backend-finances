package cd.gouv.finances.signalements.report.source;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.function.Supplier;
import cd.gouv.finances.signalements.report.ReportProperties;
import cd.gouv.finances.signalements.report.ReportRejected;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.JsonNode;

@Component
@Slf4j
public class FacnoReportSource implements ReportSource {

    private final RestClient facnoRestClient;
    private final ReportProperties reportProperties;
    private final StoredPhotoIndex storedPhotoIndex;
    private final CircuitBreaker circuit =
            CircuitBreaker.of("facno",
                    CircuitBreakerConfig.custom()
                            .slidingWindowSize(10).minimumNumberOfCalls(5).failureRateThreshold(50)
                            .waitDurationInOpenState(Duration.ofSeconds(60))
                            .permittedNumberOfCallsInHalfOpenState(1).build());

    private <T> T protectedCall(Supplier<T> operation) {
        try {
            return circuit.executeSupplier(operation);
        } catch (CallNotPermittedException exception) {
            throw new ReportRejected("circuit-open", 503);
        }
    }


    public FacnoReportSource(
            @Qualifier("facnoRestClient") RestClient facnoRestClient,
            ReportProperties reportProperties,
            StoredPhotoIndex storedPhotoIndex) {
        this.facnoRestClient = facnoRestClient;
        this.reportProperties = reportProperties;
        this.storedPhotoIndex = storedPhotoIndex;
    }

    @Override
    public SourcedPage read(LocalDate start, LocalDate end, int pageNumber, int pageSize) {
        var key = apiKey();
        var page = readJson(builder -> builder.path("/GetProblems")
                .queryParam("StartDate", start)
                .queryParam("EndDate", end)
                .queryParam("PageNumber", pageNumber)
                .queryParam("PageSize", pageSize)
                .build(), key);
        var problems = child(page, "problems");
        var reports = new ArrayList<SourcedReport>();
        if (problems != null && problems.isArray()) {
            for (var problem : problems) {
                var report = report(problem, start, end, key);
                if (report != null) {
                    reports.add(report);
                }
            }
        }
        return new SourcedPage(List.copyOf(reports), lastPage(page, reports.size(), pageSize));
    }

    private SourcedReport report(JsonNode problem, LocalDate start, LocalDate end, String key) {
        var uid = text(problem, "uid");
        if (uid == null) {
            return null;
        }
        var images = child(problem, "invoiceProblemImages");
        if (images == null) {
            problem = readJson("/GetProblem/{uid}", key, uid);
            images = child(problem, "invoiceProblemImages");
        }
        var reportedAt = reportedAt(text(problem, "reportedDateTime"));
        if (reportedAt == null
                || reportedAt.toLocalDate().isBefore(start)
                || reportedAt.toLocalDate().isAfter(end)) {
            return null;
        }
        return new SourcedReport(
                uid,
                reportedAt,
                text(problem, "title"),
                text(problem, "probDesc"),
                text(problem, "name"),
                text(problem, "email"),
                text(problem, "phone"),
                text(problem, "uidUser"),
                photos(uid, images, key));
    }

    private List<SourcedPhoto> photos(String reportUid, JsonNode images, String key) {
        if (images == null || !images.isArray()) {
            return List.of();
        }
        var photos = new ArrayList<SourcedPhoto>();
        for (var image : images) {
            var photoUid = imageUid(image);
            if (photoUid == null) {
                log.warn("A report image has no identifier");
                photos.add(new SourcedPhoto("", "application/octet-stream", new byte[0]));
                continue;
            }
            if (storedPhotoIndex.contains(reportUid, photoUid)) {
                photos.add(new SourcedPhoto(photoUid, contentType(image), new byte[0], true));
                continue;
            }
            photos.add(readImage(photoUid, key));
        }
        return List.copyOf(photos);
    }

    private SourcedPhoto readImage(String uid, String key) {
        return protectedCall(() -> readImageUnprotected(uid, key));
    }

    private SourcedPhoto readImageUnprotected(String uid, String key) {
        try {
            return facnoRestClient.get()
                    .uri("/GetImage/{uid}", uid)
                    .header("x-api-key", key)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == 404) {
                            return new SourcedPhoto(uid, "application/octet-stream", new byte[0]);
                        }
                        if (response.getStatusCode().isError()) {
                            throw new ReportRejected("source", 502);
                        }
                        var body = response.bodyTo(byte[].class);
                        return photo(uid, response.getHeaders().getContentType(), body == null ? new byte[0] : body);
                    });
        } catch (ReportRejected rejected) {
            throw rejected;
        } catch (RestClientException exception) {
            throw new ReportRejected("source", 502);
        }
    }

    private JsonNode readJson(String path, String key, Object... variables) {
        return protectedCall(() -> readJsonUnprotected(path, key, variables));
    }

    private JsonNode readJsonUnprotected(String path, String key, Object... variables) {
        try {
            var body = facnoRestClient.get()
                    .uri(path, variables)
                    .header("x-api-key", key)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new ReportRejected("source", 502);
            }
            return body;
        } catch (ReportRejected rejected) {
            throw rejected;
        } catch (RestClientException exception) {
            throw new ReportRejected("source", 502);
        }
    }

    private JsonNode readJson(Function<UriBuilder, URI> uri, String key) {
        return protectedCall(() -> readJsonUnprotected(uri, key));
    }

    private JsonNode readJsonUnprotected(Function<UriBuilder, URI> uri, String key) {
        try {
            var body = facnoRestClient.get()
                    .uri(uri)
                    .header("x-api-key", key)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new ReportRejected("source", 502);
            }
            return body;
        } catch (ReportRejected rejected) {
            throw rejected;
        } catch (RestClientException exception) {
            throw new ReportRejected("source", 502);
        }
    }

    private String apiKey() {
        var facno = reportProperties.facno();
        if (facno == null || facno.apiKey() == null || facno.apiKey().isBlank()) {
            throw new ReportRejected("source", 502);
        }
        return facno.apiKey();
    }

    private static boolean lastPage(JsonNode page, int reportCount, int pageSize) {
        var hasNext = child(page, "hasNext");
        if (hasNext != null && hasNext.isBoolean()) {
            return !hasNext.asBoolean();
        }
        return reportCount < pageSize;
    }

    private static String contentType(JsonNode image) {
        var declared = text(image, "imageContentType");
        if (declared == null) {
            return "application/octet-stream";
        }
        var mime = declared.split(";", 2)[0].trim();
        return mime.length() > 40 ? mime.substring(0, 40) : mime;
    }

    private static String imageUid(JsonNode image) {
        if (image == null || image.isNull()) {
            return null;
        }
        if (image.isString()) {
            return blankToNull(image.asString());
        }
        return firstText(image, "uid", "id", "imageUid", "imageId");
    }

    private static SourcedPhoto photo(String uid, MediaType type, byte[] body) {
        if (body.length > 5 && body[0] == 'd') {
            var text = new String(body, java.nio.charset.StandardCharsets.UTF_8);
            var separator = text.indexOf(',');
            if (text.startsWith("data:") && separator > 5) {
                var mime = text.substring(5, separator).split(";", 2)[0];
                try {
                    return new SourcedPhoto(uid, mime, Base64.getDecoder().decode(text.substring(separator + 1)));
                } catch (IllegalArgumentException ignored) {
                    return new SourcedPhoto(uid, "application/octet-stream", new byte[0]);
                }
            }
        }
        var contentType = type == null ? "application/octet-stream" : type.toString();
        return new SourcedPhoto(uid, contentType, body);
    }

    private static LocalDateTime reportedAt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).toLocalDateTime();
            } catch (DateTimeParseException offsetIgnored) {
                if (value.length() < 10) {
                    return null;
                }
                try {
                    return LocalDate.parse(value.substring(0, 10)).atStartOfDay();
                } catch (DateTimeParseException dateIgnored) {
                    return null;
                }
            }
        }
    }

    private static String firstText(JsonNode node, String... names) {
        for (var name : names) {
            var value = text(node, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String name) {
        var child = child(node, name);
        if (child == null || !child.isString()) {
            return null;
        }
        return blankToNull(child.asString());
    }

    private static JsonNode child(JsonNode node, String name) {
        if (node == null || !node.isObject()) {
            return null;
        }
        var direct = node.get(name);
        if (direct != null && !direct.isNull()) {
            return direct;
        }
        for (var field : node.properties()) {
            if (field.getKey().equalsIgnoreCase(name) && !field.getValue().isNull()) {
                return field.getValue();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
