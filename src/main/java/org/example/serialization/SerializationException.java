package org.example.serialization;

public class SerializationException extends Exception {

    public enum ErrorCode {
        IO_ERROR,             // файл не найден, нет прав, диск недоступен
        EMPTY_FILE,           // файл пустой
        BAD_FORMAT,           // это не файл нужного формата (чужое содержимое, сломанный JSON, нет версии)
        CORRUPTED_FILE,       // файл нужного формата, но повреждён (обрезан, не сошлась контрольная сумма)
        UNSUPPORTED_VERSION,  // файл создан более новой версией приложения (или версия некорректна)
        BAD_FIELD             // поле записи отсутствует или содержит недопустимое значение
    }

    private final ErrorCode errorCode;

    public SerializationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SerializationException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "[" + errorCode + "] " + getMessage();
    }
}
