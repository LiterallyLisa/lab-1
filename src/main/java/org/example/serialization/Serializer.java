package org.example.serialization;

import java.nio.file.Path;

public interface Serializer<T> {

    String format();
    String extension();
    String description();
    void save(T data, Path file) throws SerializationException;
    T load(Path file) throws SerializationException;
}
