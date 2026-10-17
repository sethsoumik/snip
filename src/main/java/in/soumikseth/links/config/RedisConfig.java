package in.soumikseth.links.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Builds the Redis connection from a single REDIS_URL (Upstash/Heroku style,
 * e.g. rediss://default:pass@host:6379).
 *
 * When REDIS_URL isn't set, Spring Boot's default spring.data.redis.* config is
 * used instead, which is what local dev (docker-compose) relies on.
 */
@Configuration
public class RedisConfig {

    @Bean
    @ConditionalOnProperty(name = "REDIS_URL")
    public LettuceConnectionFactory redisConnectionFactory(@Value("${REDIS_URL}") String redisUrl) {
        URI uri = URI.create(redisUrl);
        boolean tls = "rediss".equalsIgnoreCase(uri.getScheme());

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(uri.getHost());
        config.setPort(uri.getPort() == -1 ? 6379 : uri.getPort());

        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            if (colon >= 0) {
                config.setUsername(userInfo.substring(0, colon));
                config.setPassword(userInfo.substring(colon + 1));
            } else {
                config.setPassword(userInfo);
            }
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                LettuceClientConfiguration.builder();
        if (tls) {
            builder.useSsl();
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, builder.build());
        factory.afterPropertiesSet();
        return factory;
    }
}
