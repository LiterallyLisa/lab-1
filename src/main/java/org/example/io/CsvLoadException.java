package org.example.io;

/**
 * Исключение, возникающее при разборе одной строки CSV.
 * Содержит код ошибки (для программной обработки) и номер строки в файле (для сообщения пользователю).
 */
public class CsvLoadException extends Exception {

    /** Код ошибки — определяет тип проблемы. */
    public enum ErrorCode {
        WRONG_FIELD_COUNT,   // неверное количество колонок в строке
        BAD_NUMBER,          // не удалось распарсить число (int или double)
        BAD_DATE,            // не удалось распарсить дату
        UNKNOWN_TYPE,        // неизвестный тип товара в первой колонке
        MISSING_FIELD        // обязательное поле пустое
    }

    private final ErrorCode errorCode;
    private final int lineNumber;

    public CsvLoadException(ErrorCode errorCode, int lineNumber, String message) {
        super(message);
        this.errorCode = errorCode;
        this.lineNumber = lineNumber;
    }

    public CsvLoadException(ErrorCode errorCode, int lineNumber, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.lineNumber = lineNumber;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    /** Удобное строковое представление для логов и диалогов. */
    @Override
    public String toString() {
        return "Строка " + lineNumber + ": [" + errorCode + "] " + getMessage();
    }
}