package io.cloudchains.app.net.settlement;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class SettlementCanonicalJsonV1Test {
    @Test
    void acceptsOnlyCanonicalAsciiJsonWithoutNumbersOrDuplicates() {
        byte[] canonical =
                "{\"a\":[true,false,null,\"text\"],\"b\":\"line\\nquote\\\"slash\\\\\"}"
                        .getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(
                canonical,
                SettlementCanonicalJsonV1.encode(SettlementCanonicalJsonV1.parse(canonical)));

        assertInvalid("{\"a\":true,\"a\":false}");
        assertInvalid("{ \"a\":true}");
        assertInvalid("{\"a\":1}");
        assertInvalid("{\"a\":\"\\u0061\"}");
        assertInvalid("{\"a\":\"\\/\"}");
        assertInvalid("true false");
        assertInvalid("\ufeffnull");
        assertInvalid("{\"a\":\"é\"}");
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementCanonicalJsonV1.parse(new byte[] {(byte) 0xc3}));
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementCanonicalJsonV1.encode(new JsonPrimitive(1)));
    }

    @Test
    void enforcesEachPerContainerAndCumulativeBudgetIndependently() {
        JsonObject maximumObject = new JsonObject();
        for (int index = 0; index < 128; index++) {
            maximumObject.addProperty(String.format("k%03d", index), "v");
        }
        byte[] encodedObject = SettlementCanonicalJsonV1.encode(maximumObject);
        assertEquals(maximumObject, SettlementCanonicalJsonV1.parse(encodedObject));
        maximumObject.addProperty("overflow", "v");
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementCanonicalJsonV1.encode(maximumObject));

        JsonArray maximumArray = new JsonArray();
        for (int index = 0; index < 64; index++) {
            maximumArray.add("v");
        }
        assertEquals(
                maximumArray,
                SettlementCanonicalJsonV1.parse(SettlementCanonicalJsonV1.encode(maximumArray)));
        maximumArray.add("overflow");
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementCanonicalJsonV1.encode(maximumArray));

        JsonObject cumulativeObjects = new JsonObject();
        for (int parent = 0; parent < 4; parent++) {
            JsonObject child = new JsonObject();
            for (int member = 0; member < 127; member++) {
                child.addProperty(String.format("k%03d", member), "v");
            }
            cumulativeObjects.add("p" + parent, child);
        }
        assertDoesNotThrow(() -> SettlementCanonicalJsonV1.encode(cumulativeObjects));
        cumulativeObjects.getAsJsonObject("p0").addProperty("overflow", "v");
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementCanonicalJsonV1.encode(cumulativeObjects));

        JsonObject cumulativeArrays = new JsonObject();
        JsonArray first = new JsonArray();
        JsonArray second = new JsonArray();
        JsonArray third = new JsonArray();
        for (int index = 0; index < 64; index++) {
            first.add("v");
            second.add("v");
        }
        third.add("overflow");
        cumulativeArrays.add("a", first);
        cumulativeArrays.add("b", second);
        assertDoesNotThrow(() -> SettlementCanonicalJsonV1.encode(cumulativeArrays));
        cumulativeArrays.add("c", third);
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementCanonicalJsonV1.encode(cumulativeArrays));
    }

    @Test
    void enforcesDepthStringAndInputByteLimits() {
        JsonArray depth16 = new JsonArray();
        JsonArray cursor = depth16;
        for (int depth = 1; depth < 16; depth++) {
            JsonArray next = new JsonArray();
            cursor.add(next);
            cursor = next;
        }
        cursor.add("end");
        assertDoesNotThrow(() -> SettlementCanonicalJsonV1.encode(depth16));
        JsonArray depth17 = new JsonArray();
        cursor.add(depth17);
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementCanonicalJsonV1.encode(depth16));

        assertDoesNotThrow(
                () -> SettlementCanonicalJsonV1.encode(new JsonPrimitive("a".repeat(200_000))));
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementCanonicalJsonV1.encode(new JsonPrimitive("a".repeat(200_001))));
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementCanonicalJsonV1.parse(new byte[262_145]));

        JsonObject exactInput = new JsonObject();
        exactInput.addProperty("a", "a".repeat(131_064));
        exactInput.addProperty("b", "b".repeat(131_065));
        byte[] exactBytes = SettlementCanonicalJsonV1.encode(exactInput);
        assertEquals(262_144, exactBytes.length);
        assertEquals(exactInput, SettlementCanonicalJsonV1.parse(exactBytes));
        exactInput.addProperty("b", "b".repeat(131_066));
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementCanonicalJsonV1.encode(exactInput));
    }

    private static void assertInvalid(String value) {
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementCanonicalJsonV1.parse(value.getBytes(StandardCharsets.UTF_8)));
    }
}
