package dev.osmind.storage;

import dev.osmind.schema.OsEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class InMemoryEventStore implements EventStore {
    private final List<OsEvent> events = new ArrayList<>();

    @Override
    public synchronized void append(OsEvent event) {
        events.add(event);
    }

    @Override
    public synchronized List<OsEvent> since(Instant from) {
        return events.stream()
                .filter(event -> !event.timestamp().isBefore(from))
                .toList();
    }
}
