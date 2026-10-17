package in.soumikseth.links;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the service. Responsibilities are split across a few packages:
 * controller (HTTP), service (create/resolve/caching/clicks), core (the codec),
 * and domain/repository (persistence).
 */
@SpringBootApplication
public class LinkShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkShortenerApplication.class, args);
    }
}
