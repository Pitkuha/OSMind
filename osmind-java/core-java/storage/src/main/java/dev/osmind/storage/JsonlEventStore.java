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
}
