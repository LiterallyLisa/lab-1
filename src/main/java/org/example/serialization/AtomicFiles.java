package org.example.serialization;

import org.example.serialization.SerializationException.ErrorCode;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Запись файла «всё или ничего»: сначала во временный файл рядом, затем переименование.
 * Если запись оборвалась, прежний файл не портится.
 */
final class AtomicFiles {

    private AtomicFiles() {
    }

    static void write(Path file, byte[] content) throws SerializationException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.write(tmp, content);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // временный файл удалить не удалось — основная ошибка важнее
            }
            throw new SerializationException(ErrorCode.IO_ERROR,
                    "Не удалось записать файл «" + file.getFileName() + "»: " + e.getMessage(), e);
        }
    }

    static byte[] read(Path file) throws SerializationException {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new SerializationException(ErrorCode.IO_ERROR,
                    "Не удалось прочитать файл «" + file.getFileName() + "»: " + e.getMessage(), e);
        }
    }
}
