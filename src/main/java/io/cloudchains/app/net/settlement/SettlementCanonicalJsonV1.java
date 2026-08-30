package io.cloudchains.app.net.settlement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class SettlementCanonicalJsonV1 {
    private static final int MAX_INPUT_BYTES = 262_144;
    private static final int MAX_DEPTH = 16;
    private static final int MAX_STRING_BYTES = 200_000;
    private static final int MAX_OBJECT_MEMBERS = 128;
    private static final int MAX_ARRAY_ENTRIES = 64;
    private static final int MAX_TOTAL_OBJECT_MEMBERS = 512;
    private static final int MAX_TOTAL_ARRAY_ENTRIES = 128;
    private static final String INVALID = "Invalid canonical JSON.";

    private SettlementCanonicalJsonV1() {}

    static JsonElement parse(byte[] utf8) {
        if (utf8 == null || utf8.length == 0 || utf8.length > MAX_INPUT_BYTES) {
            throw invalid();
        }
        for (byte value : utf8) {
            if ((value & 0x80) != 0) {
                throw invalid();
            }
        }
        final String input;
        try {
            input = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(utf8))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw invalid();
        }
        if (!input.isEmpty() && input.charAt(0) == '\ufeff') {
            throw invalid();
        }
        Parser parser = new Parser(input);
        JsonElement value = parser.readValue(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw invalid();
        }
        byte[] canonical = encode(value);
        if (!Arrays.equals(utf8, canonical)) {
            throw invalid();
        }
        return value;
    }

    static byte[] encode(JsonElement value) {
        if (value == null) {
            throw invalid();
        }
        StringBuilder output = new StringBuilder();
        Counters counters = new Counters();
        writeValue(value, output, counters, 0);
        byte[] encoded = output.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length == 0 || encoded.length > MAX_INPUT_BYTES) {
            throw invalid();
        }
        return encoded;
    }

    private static void writeValue(
            JsonElement value, StringBuilder output, Counters counters, int depth) {
        if (value == null || value.isJsonNull()) {
            output.append("null");
            return;
        }
        if (value.isJsonObject()) {
            int nextDepth = depth + 1;
            if (nextDepth > MAX_DEPTH) {
                throw invalid();
            }
            JsonObject object = value.getAsJsonObject();
            if (object.size() > MAX_OBJECT_MEMBERS) {
                throw invalid();
            }
            counters.objectMembers += object.size();
            if (counters.objectMembers > MAX_TOTAL_OBJECT_MEMBERS) {
                throw invalid();
            }
            output.append('{');
            boolean first = true;
            for (String key : object.keySet().stream().sorted().toList()) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                writeString(key, output);
                output.append(':');
                writeValue(object.get(key), output, counters, nextDepth);
            }
            output.append('}');
            return;
        }
        if (value.isJsonArray()) {
            int nextDepth = depth + 1;
            if (nextDepth > MAX_DEPTH) {
                throw invalid();
            }
            JsonArray array = value.getAsJsonArray();
            if (array.size() > MAX_ARRAY_ENTRIES) {
                throw invalid();
            }
            counters.arrayEntries += array.size();
            if (counters.arrayEntries > MAX_TOTAL_ARRAY_ENTRIES) {
                throw invalid();
            }
            output.append('[');
            for (int index = 0; index < array.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                writeValue(array.get(index), output, counters, nextDepth);
            }
            output.append(']');
            return;
        }
        if (!value.isJsonPrimitive()) {
            throw invalid();
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            output.append(primitive.getAsBoolean() ? "true" : "false");
        } else if (primitive.isString()) {
            writeString(primitive.getAsString(), output);
        } else {
            throw invalid();
        }
    }

    private static void writeString(String value, StringBuilder output) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > MAX_STRING_BYTES) {
            throw invalid();
        }
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character > 0x7f) {
                throw invalid();
            }
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\t' -> output.append("\\t");
                case '\n' -> output.append("\\n");
                case '\f' -> output.append("\\f");
                case '\r' -> output.append("\\r");
                default -> {
                    if (character < 0x20) {
                        output.append("\\u00");
                        String hex = Integer.toHexString(character);
                        if (hex.length() == 1) {
                            output.append('0');
                        }
                        output.append(hex);
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(INVALID);
    }

    private static final class Counters {
        private int objectMembers;
        private int arrayEntries;
    }

    private static final class Parser {
        private final String input;
        private final Counters counters = new Counters();
        private int position;

        private Parser(String input) {
            this.input = input;
        }

        private JsonElement readValue(int depth) {
            skipWhitespace();
            if (atEnd()) {
                throw invalid();
            }
            return switch (input.charAt(position)) {
                case '{' -> readObject(depth + 1);
                case '[' -> readArray(depth + 1);
                case '"' -> new JsonPrimitive(readString());
                case 't' -> readLiteral("true", new JsonPrimitive(true));
                case 'f' -> readLiteral("false", new JsonPrimitive(false));
                case 'n' -> readLiteral("null", JsonNull.INSTANCE);
                default -> throw invalid();
            };
        }

        private JsonObject readObject(int depth) {
            if (depth > MAX_DEPTH) {
                throw invalid();
            }
            expect('{');
            JsonObject object = new JsonObject();
            Set<String> keys = new HashSet<>();
            skipWhitespace();
            if (consume('}')) {
                return object;
            }
            int members = 0;
            while (true) {
                skipWhitespace();
                if (atEnd() || input.charAt(position) != '"') {
                    throw invalid();
                }
                String key = readString();
                if (!keys.add(key)) {
                    throw invalid();
                }
                skipWhitespace();
                expect(':');
                JsonElement value = readValue(depth);
                object.add(key, value);
                members++;
                counters.objectMembers++;
                if (members > MAX_OBJECT_MEMBERS
                        || counters.objectMembers > MAX_TOTAL_OBJECT_MEMBERS) {
                    throw invalid();
                }
                skipWhitespace();
                if (consume('}')) {
                    return object;
                }
                expect(',');
            }
        }

        private JsonArray readArray(int depth) {
            if (depth > MAX_DEPTH) {
                throw invalid();
            }
            expect('[');
            JsonArray array = new JsonArray();
            skipWhitespace();
            if (consume(']')) {
                return array;
            }
            int entries = 0;
            while (true) {
                array.add(readValue(depth));
                entries++;
                counters.arrayEntries++;
                if (entries > MAX_ARRAY_ENTRIES
                        || counters.arrayEntries > MAX_TOTAL_ARRAY_ENTRIES) {
                    throw invalid();
                }
                skipWhitespace();
                if (consume(']')) {
                    return array;
                }
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (!atEnd()) {
                char character = input.charAt(position++);
                if (character == '"') {
                    if (value.toString().getBytes(StandardCharsets.UTF_8).length
                            > MAX_STRING_BYTES) {
                        throw invalid();
                    }
                    return value.toString();
                }
                if (character == '\\') {
                    if (atEnd()) {
                        throw invalid();
                    }
                    char escaped = input.charAt(position++);
                    switch (escaped) {
                        case '"' -> value.append('"');
                        case '\\' -> value.append('\\');
                        case '/' -> value.append('/');
                        case 'b' -> value.append('\b');
                        case 'f' -> value.append('\f');
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> value.append(readUnicodeEscape());
                        default -> throw invalid();
                    }
                } else {
                    if (character < 0x20 || character > 0x7f) {
                        throw invalid();
                    }
                    value.append(character);
                }
                if (value.length() > MAX_STRING_BYTES) {
                    throw invalid();
                }
            }
            throw invalid();
        }

        private char readUnicodeEscape() {
            if (position + 4 > input.length()) {
                throw invalid();
            }
            int value = 0;
            for (int index = 0; index < 4; index++) {
                char digit = input.charAt(position++);
                int parsed = Character.digit(digit, 16);
                if (parsed < 0) {
                    throw invalid();
                }
                value = (value << 4) | parsed;
            }
            if (value > 0x7f) {
                throw invalid();
            }
            return (char) value;
        }

        private JsonElement readLiteral(String literal, JsonElement value) {
            if (!input.startsWith(literal, position)) {
                throw invalid();
            }
            position += literal.length();
            return value;
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                char character = input.charAt(position);
                if (character != ' ' && character != '\t' && character != '\n'
                        && character != '\r') {
                    return;
                }
                position++;
            }
        }

        private boolean consume(char expected) {
            if (!atEnd() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw invalid();
            }
        }

        private boolean atEnd() {
            return position >= input.length();
        }
    }
}
