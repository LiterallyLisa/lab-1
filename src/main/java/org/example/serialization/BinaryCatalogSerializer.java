package org.example.serialization;

import org.example.serialization.SerializationException.ErrorCode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Собственный бинарный формат (big-endian):
 * <pre>
 *   [4]  магическое число "SHOP"
 *   [4]  версия формата (int)
 *   [4]  число записей (int)
 *   записи:
 *     [1] тип (0 BASE, 1 WARRANTY, 2 DISCONTINUED)
 *     [4] артикул, [4+n] название, [4+n] категория (строки: длина + UTF-8), [8] цена, [4] остаток
 *     WARRANTY: [4] гарантия, мес.      DISCONTINUED: [8] дата как epoch-day
 *   [4]  CRC32 всех предыдущих байтов
 * </pre>
 * Версия стоит в заголовке, поэтому будущую версию можно распознать до разбора тела.
 * Контрольная сумма ловит обрезанные и испорченные файлы.
 */
public final class BinaryCatalogSerializer implements Serializer<CatalogData> {

    static final byte[] MAGIC = {'S', 'H', 'O', 'P'};

    private static final int HEADER_SIZE = MAGIC.length + 4 + 4;
    private static final int CRC_SIZE = 4;

    /** Минимальный размер записи: тип + артикул + две длины строк + цена + остаток. */
    private static final int MIN_RECORD_SIZE = 1 + 4 + 4 + 4 + 8 + 4;

    private static final int MAX_STRING_BYTES = 65_535;

    private static final byte CODE_BASE = 0;
    private static final byte CODE_WARRANTY = 1;
    private static final byte CODE_DISCONTINUED = 2;

    @Override
    public String format() {
        return "bin";
    }

    @Override
    public String extension() {
        return "bin";
    }

    @Override
    public String description() {
        return "Бинарный формат (*.bin)";
    }

    // ---------------------------------------------------------------- запись

    @Override
    public void save(CatalogData data, Path file) throws SerializationException {
        CatalogValidator.validate(data);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER_SIZE + data.products().size() * 64);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.write(MAGIC);
            out.writeInt(data.schemaVersion());
            out.writeInt(data.products().size());
            for (ProductRecord r : data.products()) {
                writeRecord(out, r);
            }
        } catch (IOException e) {
            throw new SerializationException(ErrorCode.IO_ERROR, "Ошибка формирования данных: " + e.getMessage(), e);
        }

        byte[] body = bytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(body, 0, body.length);

        byte[] full = ByteBuffer.allocate(body.length + CRC_SIZE)
                .put(body)
                .putInt((int) crc.getValue())
                .array();
        AtomicFiles.write(file, full);
    }

    private static void writeRecord(DataOutputStream out, ProductRecord r)
            throws IOException, SerializationException {
        switch (r.type()) {
            case ProductRecord.TYPE_WARRANTY -> out.writeByte(CODE_WARRANTY);
            case ProductRecord.TYPE_DISCONTINUED -> out.writeByte(CODE_DISCONTINUED);
            default -> out.writeByte(CODE_BASE);
        }
        out.writeInt(r.article());
        writeString(out, r.name(), r.article());
        writeString(out, r.category(), r.article());
        out.writeDouble(r.price());
        out.writeInt(r.stock());
        if (ProductRecord.TYPE_WARRANTY.equals(r.type())) {
            out.writeInt(r.warrantyMonths());
        } else if (ProductRecord.TYPE_DISCONTINUED.equals(r.type())) {
            out.writeLong(LocalDate.parse(r.discontinuedDate()).toEpochDay());
        }
    }

    private static void writeString(DataOutputStream out, String s, int article)
            throws IOException, SerializationException {
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > MAX_STRING_BYTES) {
            throw new SerializationException(ErrorCode.BAD_FIELD,
                    "Артикул " + article + ": слишком длинная строка (" + utf8.length + " байт)");
        }
        out.writeInt(utf8.length);
        out.write(utf8);
    }

    // ---------------------------------------------------------------- чтение

    @Override
    public CatalogData load(Path file) throws SerializationException {
        byte[] bytes = AtomicFiles.read(file);

        if (bytes.length == 0) {
            throw new SerializationException(ErrorCode.EMPTY_FILE, "Файл пустой");
        }
        if (!hasMagic(bytes)) {
            throw new SerializationException(ErrorCode.BAD_FORMAT,
                    "Это не бинарный файл каталога (неверная сигнатура)");
        }
        if (bytes.length < HEADER_SIZE + CRC_SIZE) {
            throw new SerializationException(ErrorCode.CORRUPTED_FILE, "Файл обрезан: нет заголовка");
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        int version = buf.getInt(MAGIC.length);
        if (version < CatalogData.MIN_SUPPORTED_VERSION || version > CatalogData.CURRENT_VERSION) {
            throw new SerializationException(ErrorCode.UNSUPPORTED_VERSION,
                    "Версия формата " + version + " не поддерживается (поддерживаются версии "
                            + CatalogData.MIN_SUPPORTED_VERSION + "–" + CatalogData.CURRENT_VERSION + ")");
        }

        int crcPos = bytes.length - CRC_SIZE;
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, crcPos);
        if ((crc.getValue() & 0xFFFFFFFFL) != (buf.getInt(crcPos) & 0xFFFFFFFFL)) {
            throw new SerializationException(ErrorCode.CORRUPTED_FILE,
                    "Контрольная сумма не совпадает: файл повреждён или обрезан");
        }

        int count = buf.getInt(MAGIC.length + 4);
        long bodySize = (long) crcPos - HEADER_SIZE;
        if (count < 0 || (long) count * MIN_RECORD_SIZE > bodySize) {
            throw new SerializationException(ErrorCode.CORRUPTED_FILE,
                    "Некорректное число записей: " + count);
        }

        List<ProductRecord> records = new ArrayList<>(count);
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(bytes, HEADER_SIZE, (int) bodySize))) {
            for (int i = 0; i < count; i++) {
                records.add(readRecord(in, i + 1));
            }
            if (in.available() != 0) {
                throw new SerializationException(ErrorCode.CORRUPTED_FILE, "После записей остались лишние данные");
            }
        } catch (EOFException e) {
            throw new SerializationException(ErrorCode.CORRUPTED_FILE, "Файл обрезан: неожиданный конец данных", e);
        } catch (IOException e) {
            throw new SerializationException(ErrorCode.CORRUPTED_FILE, "Ошибка чтения данных: " + e.getMessage(), e);
        }

        CatalogData data = new CatalogData(version, records);
        CatalogValidator.validate(data);
        return data;
    }

    private static boolean hasMagic(byte[] bytes) {
        if (bytes.length < MAGIC.length) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (bytes[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static ProductRecord readRecord(DataInputStream in, int index)
            throws IOException, SerializationException {
        byte code = in.readByte();
        int article = in.readInt();
        String name = readString(in, index);
        String category = readString(in, index);
        double price = in.readDouble();
        int stock = in.readInt();

        return switch (code) {
            case CODE_BASE -> new ProductRecord(ProductRecord.TYPE_BASE, article, name, category,
                    price, stock, null, null);
            case CODE_WARRANTY -> new ProductRecord(ProductRecord.TYPE_WARRANTY, article, name, category,
                    price, stock, in.readInt(), null);
            case CODE_DISCONTINUED -> new ProductRecord(ProductRecord.TYPE_DISCONTINUED, article, name, category,
                    price, stock, null, epochDayToIso(in.readLong(), index));
            default -> throw new SerializationException(ErrorCode.BAD_FIELD,
                    "Запись №" + index + ": неизвестный код типа " + code);
        };
    }

    private static String readString(DataInputStream in, int index) throws IOException, SerializationException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES || length > in.available()) {
            throw new SerializationException(ErrorCode.CORRUPTED_FILE,
                    "Запись №" + index + ": некорректная длина строки " + length);
        }
        byte[] utf8 = new byte[length];
        in.readFully(utf8);
        return new String(utf8, StandardCharsets.UTF_8);
    }

    private static String epochDayToIso(long epochDay, int index) throws SerializationException {
        try {
            return LocalDate.ofEpochDay(epochDay).toString();
        } catch (DateTimeException e) {
            throw new SerializationException(ErrorCode.BAD_FIELD,
                    "Запись №" + index + ": некорректная дата", e);
        }
    }
}
