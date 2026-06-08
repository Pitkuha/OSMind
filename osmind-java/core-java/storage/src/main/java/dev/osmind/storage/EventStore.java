package dev.osmind.storage;

import dev.osmind.schema.OsEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface EventStore {
    void append(OsEvent event);

    List<OsEvent> since(Instant from);

    default List<OsEvent> last(Duration duration) {
        return since(Instant.now().minus(duration));
    }
}
