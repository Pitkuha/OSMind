package dev.osmind.storage;

import dev.osmind.schema.OsEvent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

public final class JsonlEventStore implements EventStore {
    private final Path path;
    private final InMemoryEventStore memory = new InMemoryEventStore();

    public JsonlEventStore(Path path) {
        this.path = path;
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(path)) {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    JsonlEventStoreCodec.decode(line).ifPresent(memory::append);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public synchronized void append(OsEvent event) {
        memory.append(event);
        try {
            Files.writeString(
                    path,
                    JsonlEventStoreCodec.encode(event) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public List<OsEvent> since(Instant from) {
        return memory.since(from);
    }

    @Override
    public synchronized int removeIf(Predicate<OsEvent> predicate) {
        List<OsEvent> allEvents = memory.since(Instant.EPOCH);
        List<OsEvent> remaining = allEvents.stream()
                .filter(event -> !predicate.test(event))
                .toList();
        int removed = allEvents.size() - remaining.size();
        if (removed == 0) {
            return 0;
        }

        try {
            String content = remaining.isEmpty()
                    ? ""
                    : remaining.stream()
                    .map(JsonlEventStoreCodec::encode)
                    .collect(java.util.stream.Collectors.joining(System.lineSeparator(), "", System.lineSeparator()));
            Files.writeString(
                    path,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        memory.removeIf(event -> true);
        remaining.forEach(memory::append);
        return removed;
    }
}
