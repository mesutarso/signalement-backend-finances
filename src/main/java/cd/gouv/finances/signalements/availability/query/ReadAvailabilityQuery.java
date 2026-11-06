package cd.gouv.finances.signalements.availability.query;

import cd.gouv.finances.signalements.availability.dto.AvailabilityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReadAvailabilityQuery {

    private final DatabaseCheck databaseCheck;
    private final StringRedisTemplate redis;

    public AvailabilityResponse execute() {
        return new AvailabilityResponse(cacheOpen() && databaseCheck.reachable());
    }

    private boolean cacheOpen() {
        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
