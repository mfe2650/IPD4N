import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class Mp3TagViewer {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Mp3TagViewer <path-to-mp3>");
            System.exit(1);
        }

        Path mp3Path = Path.of(args[0]);
        if (!Files.isRegularFile(mp3Path)) {
            System.err.println("File not found: " + mp3Path);
            System.exit(1);
        }

        try {
            byte[] data = Files.readAllBytes(mp3Path);
            Map<String, String> tags = new LinkedHashMap<>();

            parseId3v2(data, tags);
            parseId3v1(data, tags);

            if (tags.isEmpty()) {
                System.out.println("No MP3 tags found.");
                return;
            }

            System.out.println("Tags in: " + mp3Path);
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                System.out.println(entry.getKey() + " = " + entry.getValue());
            }
        } catch (IOException e) {
            System.err.println("Failed to read file: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void parseId3v2(byte[] data, Map<String, String> tags) {
        if (data.length < 10) {
            return;
        }

        if (data[0] != 'I' || data[1] != 'D' || data[2] != '3') {
            return;
        }

        int majorVersion = Byte.toUnsignedInt(data[3]);
        int tagSize = readSynchsafeInt(data, 6);
        int pos = 10;
        int end = Math.min(data.length, pos + tagSize);

        while (pos + 10 <= end) {
            String frameId;
            int frameSize;
            int headerSize;

            if (majorVersion == 2) {
                if (pos + 6 > end) {
                    break;
                }
                frameId = readAscii(data, pos, 3);
                frameSize = ((data[pos + 3] & 0xFF) << 16)
                    | ((data[pos + 4] & 0xFF) << 8)
                    | (data[pos + 5] & 0xFF);
                headerSize = 6;
            } else {
                frameId = readAscii(data, pos, 4);
                frameSize = (majorVersion == 4)
                    ? readSynchsafeInt(data, pos + 4)
                    : readInt(data, pos + 4);
                headerSize = 10;
            }

            if (isFramePadding(frameId) || frameSize <= 0) {
                break;
            }

            int frameDataStart = pos + headerSize;
            int frameDataEnd = frameDataStart + frameSize;
            if (frameDataEnd > end || frameDataStart >= data.length) {
                break;
            }

            String value = decodeFrameValue(frameId, data, frameDataStart, frameSize);
            if (!value.isBlank()) {
                tags.put("ID3v2." + frameId, value);
            }

            pos = frameDataEnd;
        }
    }

    private static void parseId3v1(byte[] data, Map<String, String> tags) {
        if (data.length < 128) {
            return;
        }

        int start = data.length - 128;
        if (data[start] != 'T' || data[start + 1] != 'A' || data[start + 2] != 'G') {
            return;
        }

        tags.put("ID3v1.Title", readLatin1Trimmed(data, start + 3, 30));
        tags.put("ID3v1.Artist", readLatin1Trimmed(data, start + 33, 30));
        tags.put("ID3v1.Album", readLatin1Trimmed(data, start + 63, 30));
        tags.put("ID3v1.Year", readLatin1Trimmed(data, start + 93, 4));
        tags.put("ID3v1.Comment", readLatin1Trimmed(data, start + 97, 30));
        tags.put("ID3v1.Genre", Integer.toString(Byte.toUnsignedInt(data[start + 127])));
    }

    private static String decodeFrameValue(String frameId, byte[] data, int offset, int length) {
        if (length <= 0 || offset + length > data.length) {
            return "";
        }

        if (frameId.startsWith("T")) {
            return decodeTextFrame(data, offset, length);
        }

        if ("COMM".equals(frameId) && length > 4) {
            int encoding = Byte.toUnsignedInt(data[offset]);
            Charset charset = getTextCharset(encoding);
            int textStart = offset + 4;
            return trimNulls(new String(data, textStart, length - 4, charset)).trim();
        }

        return "[" + length + " bytes]";
    }

    private static String decodeTextFrame(byte[] data, int offset, int length) {
        if (length <= 1) {
            return "";
        }

        int encoding = Byte.toUnsignedInt(data[offset]);
        Charset charset = getTextCharset(encoding);
        String text = new String(data, offset + 1, length - 1, charset);
        return trimNulls(text).trim();
    }

    private static Charset getTextCharset(int encodingByte) {
        switch (encodingByte) {
            case 0:
                return StandardCharsets.ISO_8859_1;
            case 1:
                return StandardCharsets.UTF_16;
            case 2:
                return StandardCharsets.UTF_16BE;
            case 3:
                return StandardCharsets.UTF_8;
            default:
                return StandardCharsets.ISO_8859_1;
        }
    }

    private static int readSynchsafeInt(byte[] data, int offset) {
        return ((data[offset] & 0x7F) << 21)
            | ((data[offset + 1] & 0x7F) << 14)
            | ((data[offset + 2] & 0x7F) << 7)
            | (data[offset + 3] & 0x7F);
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
            | ((data[offset + 1] & 0xFF) << 16)
            | ((data[offset + 2] & 0xFF) << 8)
            | (data[offset + 3] & 0xFF);
    }

    private static String readAscii(byte[] data, int offset, int length) {
        return new String(data, offset, length, StandardCharsets.US_ASCII);
    }

    private static boolean isFramePadding(String frameId) {
        for (int i = 0; i < frameId.length(); i++) {
            if (frameId.charAt(i) != '\u0000') {
                return false;
            }
        }
        return true;
    }

    private static String readLatin1Trimmed(byte[] data, int offset, int length) {
        String value = new String(data, offset, length, StandardCharsets.ISO_8859_1);
        return trimNulls(value).trim();
    }

    private static String trimNulls(String value) {
        return value.replace("\u0000", "");
    }
}