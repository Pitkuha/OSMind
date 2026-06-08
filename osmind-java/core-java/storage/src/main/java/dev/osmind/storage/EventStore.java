package dev.osmind.storage;

import dev.osmind.schema.OsEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

public interface EventStore {
    void append(OsEvent event);

    List<OsEvent> since(Instant from);

    int removeIf(Predicate<OsEvent> predicate);

    default List<OsEvent> last(Duration duration) {
        return since(Instant.now().minus(duration));
    }
}
