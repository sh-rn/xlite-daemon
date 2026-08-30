package io.cloudchains.app.net.settlement;

import static io.cloudchains.app.net.settlement.SettlementContractV1.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class SettlementEnvelopeCodecV1Test {
    private static final String HEX_A = "aa".repeat(32);
    private static final String HEX_B = "bb".repeat(32);
    private static final String HEX_C = "cc".repeat(32);

    @Test
    void decodesExactlySixClosedSelectorsAndThreeEnvelopeVariants() {
        assertInstanceOf(
                ReserveWalletInputsRequest.class,
                ((ContextualRequestEnvelope)
                                SettlementEnvelopeCodecV1.decodeRequest(
                                        request(Operation.RESERVE_WALLET_INPUTS)))
                        .body());
        assertInstanceOf(
                ProveReservedUtxoOwnershipRequest.class,
                ((ContextualRequestEnvelope)
                                SettlementEnvelopeCodecV1.decodeRequest(
                                        request(Operation.PROVE_RESERVED_UTXO_OWNERSHIP)))
                        .body());
        assertInstanceOf(
                OwnedAddressRequestEnvelope.class,
                SettlementEnvelopeCodecV1.decodeRequest(request(Operation.CHECK_OWNED_ADDRESS)));
        assertInstanceOf(
                SignSettlementStageRequest.class,
                ((ContextualRequestEnvelope)
                                SettlementEnvelopeCodecV1.decodeRequest(
                                        request(Operation.SIGN_SETTLEMENT_STAGE)))
                        .body());
        assertInstanceOf(
                BroadcastSettlementStageRequest.class,
                ((ContextualRequestEnvelope)
                                SettlementEnvelopeCodecV1.decodeRequest(
                                        request(Operation.BROADCAST_SETTLEMENT_STAGE)))
                        .body());
        assertInstanceOf(
                LookupRequestEnvelope.class,
                SettlementEnvelopeCodecV1.decodeRequest(
                        request(Operation.GET_SETTLEMENT_OPERATION_RESULT)));

        JsonObject unknown =
                SettlementCanonicalJsonV1.parse(request(Operation.RESERVE_WALLET_INPUTS))
                        .getAsJsonObject();
        unknown.addProperty("operation", "signrawtransaction");
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementEnvelopeCodecV1.decodeRequest(
                        SettlementCanonicalJsonV1.encode(unknown)));
    }

    @Test
    void enforcesExactRequestKeysPresenceIdentityAndCrossFields() {
        JsonObject owned =
                SettlementCanonicalJsonV1.parse(request(Operation.CHECK_OWNED_ADDRESS))
                        .getAsJsonObject();
        assertEquals(12, owned.size());
        owned.addProperty("parentIntentId", "");
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementEnvelopeCodecV1.decodeRequest(
                        SettlementCanonicalJsonV1.encode(owned)));

        JsonObject contextual =
                SettlementCanonicalJsonV1.parse(request(Operation.RESERVE_WALLET_INPUTS))
                        .getAsJsonObject();
        assertEquals(16, contextual.size());
        contextual.remove("role");
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementEnvelopeCodecV1.decodeRequest(
                        SettlementCanonicalJsonV1.encode(contextual)));

        JsonObject lookup =
                SettlementCanonicalJsonV1.parse(
                                request(Operation.GET_SETTLEMENT_OPERATION_RESULT))
                        .getAsJsonObject();
        assertEquals(16, lookup.size());
        lookup.addProperty("role", "taker");
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementEnvelopeCodecV1.decodeRequest(
                        SettlementCanonicalJsonV1.encode(lookup)));

        JsonObject identity =
                SettlementCanonicalJsonV1.parse(request(Operation.SIGN_SETTLEMENT_STAGE))
                        .getAsJsonObject();
        identity.addProperty("payloadDigest", HEX_C);
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementEnvelopeCodecV1.decodeRequest(
                        SettlementCanonicalJsonV1.encode(identity)));

        JsonObject deadline =
                SettlementCanonicalJsonV1.parse(request(Operation.CHECK_OWNED_ADDRESS))
                        .getAsJsonObject();
        deadline.addProperty("issuedAtUnixMillis", "201");
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementEnvelopeCodecV1.decodeRequest(
                        SettlementCanonicalJsonV1.encode(deadline)));
    }

    @Test
    void roundTripsEveryStatusAwareResultBodyAndRejectsInvalidPairings() {
        List<ResultEnvelope> envelopes =
                List.of(
                        result(
                                Operation.RESERVE_WALLET_INPUTS,
                                Status.RESERVED,
                                new ReservationResult(
                                        HEX_A,
                                        HEX_B,
                                        List.of(new OutpointIdentity(HEX_C, "0")),
                                        false)),
                        result(
                                Operation.PROVE_RESERVED_UTXO_OWNERSHIP,
                                Status.PROOF_CREATED,
                                new OwnershipProofResult(HEX_A, "AA==", HEX_B)),
                        result(
                                Operation.CHECK_OWNED_ADDRESS,
                                Status.OWNERSHIP_CHECKED,
                                new OwnedAddressResult(true)),
                        result(
                                Operation.SIGN_SETTLEMENT_STAGE,
                                Status.SIGNED,
                                new SignedStageResult("00", HEX_A, HEX_B, HEX_C, List.of("0"))),
                        result(
                                Operation.RESERVE_WALLET_INPUTS,
                                Status.QUARANTINED,
                                new NonBroadcastQuarantinedResult(
                                        QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED,
                                        HEX_A)),
                        result(
                                Operation.BROADCAST_SETTLEMENT_STAGE,
                                Status.BROADCAST_DISPATCH_RECORDED,
                                new BroadcastDispatchRecordedResult(HEX_A, HEX_B, HEX_C, HEX_A)),
                        result(
                                Operation.BROADCAST_SETTLEMENT_STAGE,
                                Status.BROADCAST_SUCCEEDED,
                                new BroadcastSucceededResult(HEX_A, HEX_B, HEX_C, HEX_B)),
                        result(
                                Operation.BROADCAST_SETTLEMENT_STAGE,
                                Status.BROADCAST_REJECTED,
                                new BroadcastRejectedResult(
                                        HEX_A,
                                        HEX_B,
                                        HEX_C,
                                        BroadcastRejectionCode.BACKEND_DEFINITIVE_REJECTION,
                                        HEX_A)),
                        result(
                                Operation.BROADCAST_SETTLEMENT_STAGE,
                                Status.QUARANTINED,
                                new BroadcastQuarantinedResult(
                                        HEX_A,
                                        HEX_B,
                                        HEX_C,
                                        QuarantineCode.BROADCAST_IDENTITY_MISMATCH,
                                        HEX_A)));
        for (ResultEnvelope envelope : envelopes) {
            byte[] wire = SettlementEnvelopeCodecV1.encodeResult(envelope);
            assertEquals(envelope, SettlementEnvelopeCodecV1.decodeResult(wire));
        }

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ResultEnvelope(
                                Schema.RESULT,
                                Operation.GET_SETTLEMENT_OPERATION_RESULT,
                                HEX_A,
                                HEX_B,
                                Status.RESERVED,
                                HEX_C,
                                "1",
                                new ReservationResult(
                                        HEX_A,
                                        HEX_B,
                                        List.of(new OutpointIdentity(HEX_C, "0")),
                                        false)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SettlementEnvelopeCodecV1.encodeResult(
                                result(
                                        Operation.PROVE_RESERVED_UTXO_OWNERSHIP,
                                        Status.PROOF_CREATED,
                                        new OwnershipProofResult(HEX_A, "AQ", HEX_B))));
    }

    @Test
    void enforcesCompleteErrorSemanticsWithTypedRequestIdentity() {
        RequestEnvelope reserve =
                SettlementEnvelopeCodecV1.decodeRequest(request(Operation.RESERVE_WALLET_INPUTS));
        RequestEnvelope lookup =
                SettlementEnvelopeCodecV1.decodeRequest(
                        request(Operation.GET_SETTLEMENT_OPERATION_RESULT));

        for (ErrorCode code : ErrorCode.values()) {
            if (code == ErrorCode.BACKEND_DEFINITIVE_REJECTION
                    || code == ErrorCode.BROADCAST_IDENTITY_MISMATCH
                    || code == ErrorCode.AMBIGUOUS_OUTCOME_QUARANTINED) {
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SettlementEnvelopeCodecV1.decodeError(
                                        error(
                                                Operation.RESERVE_WALLET_INPUTS,
                                                HEX_A,
                                                code,
                                                false),
                                        new ValidatedRequestIdentity(reserve)));
            } else if (code == ErrorCode.INVALID_REQUEST || code == ErrorCode.UNAUTHENTICATED) {
                assertEquals(
                        code,
                        SettlementEnvelopeCodecV1.decodeError(
                                        error(
                                                Operation.RESERVE_WALLET_INPUTS,
                                                null,
                                                code,
                                                false),
                                        new NoSemanticIdentity(Operation.RESERVE_WALLET_INPUTS))
                                .code());
            } else {
                RequestEnvelope context = code == ErrorCode.OPERATION_NOT_FOUND ? lookup : reserve;
                Operation operation =
                        code == ErrorCode.OPERATION_NOT_FOUND
                                ? Operation.GET_SETTLEMENT_OPERATION_RESULT
                                : Operation.RESERVE_WALLET_INPUTS;
                String expected =
                        context instanceof LookupRequestEnvelope value
                                ? value.semanticOperationId()
                                : ((ContextualRequestEnvelope) context).semanticOperationId();
                boolean quarantined =
                        code == ErrorCode.OPERATION_NOT_FOUND
                                || code == ErrorCode.PERSISTENCE_UNAVAILABLE;
                assertEquals(
                        code,
                        SettlementEnvelopeCodecV1.decodeError(
                                        error(operation, expected, code, quarantined),
                                        new ValidatedRequestIdentity(context))
                                .code());
            }
        }

        ContextualRequestEnvelope reserveEnvelope = (ContextualRequestEnvelope) reserve;
        ContextualRequestEnvelope forged =
                new ContextualRequestEnvelope(
                        reserveEnvelope.schema(),
                        reserveEnvelope.operation(),
                        reserveEnvelope.profileId(),
                        reserveEnvelope.runtimeGenerationId(),
                        reserveEnvelope.parentIntentId(),
                        reserveEnvelope.childStageId(),
                        HEX_C,
                        reserveEnvelope.payloadDigest(),
                        reserveEnvelope.role(),
                        reserveEnvelope.stage(),
                        reserveEnvelope.asset(),
                        reserveEnvelope.chainIdentity(),
                        reserveEnvelope.policyDigest(),
                        reserveEnvelope.issuedAtUnixMillis(),
                        reserveEnvelope.deadlineUnixMillis(),
                        reserveEnvelope.body());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SettlementEnvelopeCodecV1.decodeError(
                                error(
                                        Operation.RESERVE_WALLET_INPUTS,
                                        HEX_C,
                                        ErrorCode.CAPABILITY_UNAVAILABLE,
                                        false),
                                new ValidatedRequestIdentity(forged)));
    }

    @Test
    void rejectsWrongSchemasSelectorsStatusesAndEveryTopLevelFieldShape() {
        for (Operation operation : Operation.values()) {
            JsonObject valid = object(request(operation));
            assertInvalidMutation(valid, value -> value.addProperty("schema", Schema.RESULT.wireValue()));
            assertInvalidMutation(valid, value -> value.addProperty("operation", "sendrawtransaction"));
            assertEveryFieldShapeRejected(valid, SettlementEnvelopeCodecV1::decodeRequest);
        }

        for (ResultEnvelope envelope : allResultEnvelopes()) {
            JsonObject valid = object(SettlementEnvelopeCodecV1.encodeResult(envelope));
            assertInvalidMutation(
                    valid,
                    value -> value.addProperty("schema", Schema.REQUEST.wireValue()),
                    SettlementEnvelopeCodecV1::decodeResult);
            assertEveryFieldShapeRejected(valid, SettlementEnvelopeCodecV1::decodeResult);
            for (Status status : Status.values()) {
                if (status != envelope.status()) {
                    assertInvalidMutation(
                            valid,
                            value -> value.addProperty("status", status.wireValue()),
                            SettlementEnvelopeCodecV1::decodeResult);
                }
            }
        }

        RequestEnvelope reserve =
                SettlementEnvelopeCodecV1.decodeRequest(request(Operation.RESERVE_WALLET_INPUTS));
        JsonObject validError =
                object(
                        error(
                                Operation.RESERVE_WALLET_INPUTS,
                                ((ContextualRequestEnvelope) reserve).semanticOperationId(),
                                ErrorCode.CAPABILITY_UNAVAILABLE,
                                false));
        assertInvalidMutation(
                validError,
                value -> value.addProperty("schema", Schema.RESULT.wireValue()),
                wire ->
                        SettlementEnvelopeCodecV1.decodeError(
                                wire, new ValidatedRequestIdentity(reserve)));
        assertEveryFieldShapeRejected(
                validError,
                wire ->
                        SettlementEnvelopeCodecV1.decodeError(
                                wire, new ValidatedRequestIdentity(reserve)));
    }

    @Test
    void rejectsMissingExtraNullAndWrongTypesInEveryNestedRecord() {
        for (Operation operation : Operation.values()) {
            JsonObject request = object(request(operation));
            assertNestedShapeRejected(request, "chainIdentity", SettlementEnvelopeCodecV1::decodeRequest);
            assertNestedShapeRejected(request, "body", SettlementEnvelopeCodecV1::decodeRequest);
        }

        JsonObject reservation = object(request(Operation.RESERVE_WALLET_INPUTS));
        assertArrayElementShapeRejected(
                reservation,
                reservation.getAsJsonObject("body").getAsJsonArray("outpoints"),
                0,
                SettlementEnvelopeCodecV1::decodeRequest);

        JsonObject proof = object(request(Operation.PROVE_RESERVED_UTXO_OWNERSHIP));
        JsonObject proofBody = proof.getAsJsonObject("body");
        assertNestedObjectShapeRejected(
                proof,
                proofBody,
                "outpoint",
                SettlementEnvelopeCodecV1::decodeRequest);

        JsonObject sign = object(request(Operation.SIGN_SETTLEMENT_STAGE));
        JsonObject signBody = sign.getAsJsonObject("body");
        assertArrayElementShapeRejected(
                sign,
                signBody.getAsJsonArray("previousOutputs"),
                0,
                SettlementEnvelopeCodecV1::decodeRequest);
        assertArrayElementShapeRejected(
                sign,
                signBody.getAsJsonArray("expectedOutputs"),
                0,
                SettlementEnvelopeCodecV1::decodeRequest);

        JsonObject broadcast = object(request(Operation.BROADCAST_SETTLEMENT_STAGE));
        JsonObject broadcastBody = broadcast.getAsJsonObject("body");
        JsonObject binding = recoveryBinding();
        broadcastBody.add("recoveryBinding", binding);
        reidentify(broadcast);
        assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(broadcast)));
        assertNestedObjectShapeRejected(
                broadcast,
                broadcastBody,
                "recoveryBinding",
                SettlementEnvelopeCodecV1::decodeRequest);

        for (ResultEnvelope envelope : allResultEnvelopes()) {
            JsonObject result = object(SettlementEnvelopeCodecV1.encodeResult(envelope));
            assertNestedShapeRejected(result, "body", SettlementEnvelopeCodecV1::decodeResult);
            if (envelope.body() instanceof ReservationResult) {
                assertArrayElementShapeRejected(
                        result,
                        result.getAsJsonObject("body").getAsJsonArray("outpoints"),
                        0,
                        SettlementEnvelopeCodecV1::decodeResult);
            }
            if (envelope.body() instanceof SignedStageResult) {
                JsonArray indexes =
                        result.getAsJsonObject("body").getAsJsonArray("signedInputIndexes");
                for (com.google.gson.JsonElement wrong :
                        List.of(JsonNull.INSTANCE, new com.google.gson.JsonPrimitive(true), new JsonObject())) {
                    JsonObject candidate = copy(result);
                    candidate.getAsJsonObject("body").getAsJsonArray("signedInputIndexes").set(0, wrong);
                    assertInvalid(() -> SettlementEnvelopeCodecV1.decodeResult(bytes(candidate)));
                }
                assertEquals(1, indexes.size());
            }
        }
    }

    @Test
    void admitsTwelveAndSixteenKeyEnvelopeVariantsAndOnlyCoherentLookupContext() {
        JsonObject owned = object(request(Operation.CHECK_OWNED_ADDRESS));
        assertEquals(12, owned.size());
        assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(owned)));

        JsonObject contextual = object(request(Operation.SIGN_SETTLEMENT_STAGE));
        assertEquals(16, contextual.size());
        assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(contextual)));

        JsonObject lookup = object(request(Operation.GET_SETTLEMENT_OPERATION_RESULT));
        assertEquals(16, lookup.size());
        assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(lookup)));
        for (String field : List.of("parentIntentId", "childStageId", "role", "stage")) {
            JsonObject mixed = copy(lookup);
            mixed.addProperty(
                    field,
                    switch (field) {
                        case "parentIntentId" -> SettlementIdentityV1Test.PARENT;
                        case "childStageId" -> SettlementIdentityV1Test.CHILD;
                        case "role" -> "taker";
                        case "stage" -> "taker-deposit";
                        default -> throw new AssertionError();
                    });
            assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(mixed)));
        }
        lookup.addProperty("parentIntentId", SettlementIdentityV1Test.PARENT);
        lookup.addProperty("childStageId", SettlementIdentityV1Test.CHILD);
        lookup.addProperty("role", "taker");
        lookup.addProperty("stage", "taker-deposit");
        assertInstanceOf(
                LookupRequestEnvelope.class,
                SettlementEnvelopeCodecV1.decodeRequest(bytes(lookup)));

        JsonObject wrongBody = copy(lookup);
        wrongBody.getAsJsonObject("body").addProperty("targetSemanticOperationId", HEX_C);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(wrongBody)));
        JsonObject wrongPayload = copy(lookup);
        wrongPayload.getAsJsonObject("body").addProperty("expectedPayloadDigest", HEX_C);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(wrongPayload)));
    }

    @Test
    void validatesAllUnsignedAddressHexBase64AndDeadlineBoundaries() {
        JsonObject request = object(request(Operation.RESERVE_WALLET_INPUTS));
        JsonObject body = request.getAsJsonObject("body");
        JsonObject chain = request.getAsJsonObject("chainIdentity");

        for (String value : List.of("0", "18446744073709551615")) {
            JsonObject candidate = copy(request);
            candidate.getAsJsonObject("body").addProperty("recoveryReserveUnits", value);
            reidentify(candidate);
            assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(candidate)));
        }
        for (String value : List.of("-1", "01", "+1", "18446744073709551616")) {
            assertReidentifiedRequestRejected(
                    request,
                    value,
                    candidate -> candidate.getAsJsonObject("body").addProperty("recoveryReserveUnits", value));
        }
        for (String value : List.of("1", "1000000000")) {
            JsonObject candidate = copy(request);
            candidate.getAsJsonObject("body").addProperty("baseAmountBlockUnits", value);
            reidentify(candidate);
            assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(candidate)));
        }
        assertReidentifiedRequestRejected(
                request,
                "base ceiling",
                candidate -> candidate.getAsJsonObject("body").addProperty("baseAmountBlockUnits", "1000000001"));

        for (String value : List.of("0", "255")) {
            JsonObject candidate = copy(request);
            candidate.getAsJsonObject("chainIdentity").addProperty("p2pkhVersion", value);
            reidentify(candidate);
            assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(candidate)));
        }
        for (String value : List.of("-1", "01", "256")) {
            assertReidentifiedRequestRejected(
                    request,
                    "u8 " + value,
                    candidate -> candidate.getAsJsonObject("chainIdentity").addProperty("p2pkhVersion", value));
        }

        JsonObject signU32 = object(request(Operation.SIGN_SETTLEMENT_STAGE));
        for (String value : List.of("0", "4294967295")) {
            JsonObject candidate = copy(signU32);
            candidate.getAsJsonObject("body").addProperty("lockTime", value);
            reidentify(candidate);
            assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(candidate)));
        }
        for (String value : List.of("-1", "01", "4294967296")) {
            assertReidentifiedRequestRejected(
                    signU32,
                    "u32 " + value,
                    candidate -> candidate.getAsJsonObject("body").addProperty("lockTime", value));
        }

        for (String address : List.of("A", "A".repeat(128))) {
            JsonObject candidate = copy(request);
            candidate.getAsJsonObject("body").addProperty("walletAddress", address);
            reidentify(candidate);
            assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(candidate)));
        }
        for (String address : List.of("", "A".repeat(129), "A B", "é")) {
            assertReidentifiedRequestRejected(
                    request,
                    "address",
                    candidate -> candidate.getAsJsonObject("body").addProperty("walletAddress", address));
        }

        JsonObject proofResult =
                object(
                        SettlementEnvelopeCodecV1.encodeResult(
                                result(
                                        Operation.PROVE_RESERVED_UTXO_OWNERSHIP,
                                        Status.PROOF_CREATED,
                                        new OwnershipProofResult(HEX_A, "AQ==", HEX_B))));
        for (String value : List.of("AQ", "AQ=", "AQ===", " AQ==")) {
            JsonObject invalid = copy(proofResult);
            invalid.getAsJsonObject("body").addProperty("compactProofBase64", value);
            assertInvalid(() -> SettlementEnvelopeCodecV1.decodeResult(bytes(invalid)));
        }

        JsonObject proofRequest = object(request(Operation.PROVE_RESERVED_UTXO_OWNERSHIP));
        for (String value : List.of("", "0", "ABC", "gg")) {
            assertReidentifiedRequestRejected(
                    proofRequest,
                    "hex " + value,
                    candidate -> candidate.getAsJsonObject("body").addProperty("messageHex", value));
        }

        JsonObject deadline = copy(request);
        deadline.addProperty("issuedAtUnixMillis", "18446744073709551615");
        deadline.addProperty("deadlineUnixMillis", "18446744073709551615");
        reidentify(deadline);
        assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(deadline)));
        JsonObject reversed = copy(request);
        reversed.addProperty("issuedAtUnixMillis", "2");
        reversed.addProperty("deadlineUnixMillis", "1");
        reidentify(reversed);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(reversed)));

        assertNotNull(body);
        assertNotNull(chain);
    }

    @Test
    void enforcesEveryChainReservationSignAndResultCardinalityRule() {
        JsonObject reserve = object(request(Operation.RESERVE_WALLET_INPUTS));
        JsonArray versions = reserve.getAsJsonObject("chainIdentity").getAsJsonArray("p2shVersions");
        versions.remove(0);
        versions.remove(0);
        for (int value = 0; value < 8; value++) {
            versions.add(Integer.toString(value));
        }
        reidentify(reserve);
        assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(reserve)));
        versions.add("8");
        reidentify(reserve);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(reserve)));
        JsonObject duplicateVersions = object(request(Operation.RESERVE_WALLET_INPUTS));
        JsonArray duplicate = duplicateVersions.getAsJsonObject("chainIdentity").getAsJsonArray("p2shVersions");
        duplicate.set(1, duplicate.get(0));
        reidentify(duplicateVersions);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(duplicateVersions)));
        JsonObject emptyVersions = object(request(Operation.RESERVE_WALLET_INPUTS));
        JsonArray empty = emptyVersions.getAsJsonObject("chainIdentity").getAsJsonArray("p2shVersions");
        while (!empty.isEmpty()) {
            empty.remove(empty.size() - 1);
        }
        reidentify(emptyVersions);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(emptyVersions)));
        JsonObject descendingVersions = object(request(Operation.RESERVE_WALLET_INPUTS));
        JsonArray descending =
                descendingVersions.getAsJsonObject("chainIdentity").getAsJsonArray("p2shVersions");
        descending.set(0, new com.google.gson.JsonPrimitive("50"));
        descending.set(1, new com.google.gson.JsonPrimitive("5"));
        reidentify(descendingVersions);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(descendingVersions)));

        JsonObject wrongAsset = object(request(Operation.RESERVE_WALLET_INPUTS));
        wrongAsset.getAsJsonObject("chainIdentity").addProperty("asset", "BLOCK");
        reidentify(wrongAsset);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(wrongAsset)));
        JsonObject wrongNetwork = object(request(Operation.RESERVE_WALLET_INPUTS));
        wrongNetwork.getAsJsonObject("chainIdentity").addProperty("network", "testnet");
        reidentify(wrongNetwork);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(wrongNetwork)));

        JsonObject tenReserve = object(request(Operation.RESERVE_WALLET_INPUTS));
        JsonArray reserveOutpoints = tenReserve.getAsJsonObject("body").getAsJsonArray("outpoints");
        populatePrevouts(reserveOutpoints, 10);
        reidentify(tenReserve);
        assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(tenReserve)));
        populatePrevouts(reserveOutpoints, 11);
        reidentify(tenReserve);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(tenReserve)));
        populatePrevouts(reserveOutpoints, 2);
        reserveOutpoints.set(1, reserveOutpoints.get(0));
        reidentify(tenReserve);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(tenReserve)));
        while (!reserveOutpoints.isEmpty()) {
            reserveOutpoints.remove(reserveOutpoints.size() - 1);
        }
        reidentify(tenReserve);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(tenReserve)));

        JsonObject serviceFee = object(request(Operation.RESERVE_WALLET_INPUTS));
        serviceFee.getAsJsonObject("body").addProperty("purpose", "service-fee");
        serviceFee.getAsJsonObject("body").addProperty("fixedTakerFeeBlockUnits", "1500000");
        reidentify(serviceFee);
        assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(serviceFee)));
        serviceFee.getAsJsonObject("body").addProperty("fixedTakerFeeBlockUnits", "0");
        reidentify(serviceFee);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(serviceFee)));
        JsonObject depositFee = object(request(Operation.RESERVE_WALLET_INPUTS));
        depositFee.getAsJsonObject("body").addProperty("fixedTakerFeeBlockUnits", "1");
        reidentify(depositFee);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(depositFee)));

        JsonObject sign = object(request(Operation.SIGN_SETTLEMENT_STAGE));
        JsonObject signBody = sign.getAsJsonObject("body");
        JsonArray signVersions = sign.getAsJsonObject("chainIdentity").getAsJsonArray("p2shVersions");
        while (!signVersions.isEmpty()) {
            signVersions.remove(signVersions.size() - 1);
        }
        for (int value = 0; value < 8; value++) {
            signVersions.add(Integer.toString(value));
        }
        populatePrevouts(signBody.getAsJsonArray("previousOutputs"), 10);
        populateSequences(signBody.getAsJsonArray("inputSequences"), 10);
        populateExpectedOutputs(signBody.getAsJsonArray("expectedOutputs"), 64);
        reidentify(sign);
        assertEquals(311, countObjectMembers(sign));
        assertEquals(92, countArrayEntries(sign));
        assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(sign)));
        populateExpectedOutputs(signBody.getAsJsonArray("expectedOutputs"), 65);
        assertThrows(IllegalArgumentException.class, () -> reidentify(sign));
        populateExpectedOutputs(signBody.getAsJsonArray("expectedOutputs"), 2);
        signBody.getAsJsonArray("expectedOutputs").get(1).getAsJsonObject().addProperty("index", "0");
        reidentify(sign);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(sign)));
        populateExpectedOutputs(signBody.getAsJsonArray("expectedOutputs"), 2);
        signBody.getAsJsonArray("expectedOutputs").get(0).getAsJsonObject().addProperty("index", "2");
        signBody.getAsJsonArray("expectedOutputs").get(1).getAsJsonObject().addProperty("index", "1");
        reidentify(sign);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(sign)));
        while (!signBody.getAsJsonArray("expectedOutputs").isEmpty()) {
            signBody.getAsJsonArray("expectedOutputs")
                    .remove(signBody.getAsJsonArray("expectedOutputs").size() - 1);
        }
        reidentify(sign);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(sign)));
        populateExpectedOutputs(signBody.getAsJsonArray("expectedOutputs"), 1);
        populateSequences(signBody.getAsJsonArray("inputSequences"), 9);
        reidentify(sign);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(sign)));
        populateSequences(signBody.getAsJsonArray("inputSequences"), 0);
        reidentify(sign);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(sign)));
        populateSequences(signBody.getAsJsonArray("inputSequences"), 11);
        reidentify(sign);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(sign)));
        populateSequences(signBody.getAsJsonArray("inputSequences"), 10);
        while (!signBody.getAsJsonArray("previousOutputs").isEmpty()) {
            signBody.getAsJsonArray("previousOutputs")
                    .remove(signBody.getAsJsonArray("previousOutputs").size() - 1);
        }
        reidentify(sign);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(sign)));
        populatePrevouts(signBody.getAsJsonArray("previousOutputs"), 11);
        populateSequences(signBody.getAsJsonArray("inputSequences"), 10);
        reidentify(sign);
        assertInvalid(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(sign)));

        assertInvalid(
                () ->
                        SettlementEnvelopeCodecV1.encodeResult(
                                result(
                                        Operation.RESERVE_WALLET_INPUTS,
                                        Status.RESERVED,
                                        new ReservationResult(
                                                HEX_A,
                                                HEX_B,
                                                List.of(
                                                        new OutpointIdentity(HEX_C, "0"),
                                                        new OutpointIdentity(HEX_C, "0")),
                                                false))));
        List<OutpointIdentity> eleven = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            eleven.add(new OutpointIdentity("%02x".formatted(index).repeat(32), Integer.toString(index)));
        }
        assertInvalid(
                () ->
                        SettlementEnvelopeCodecV1.encodeResult(
                                result(
                                        Operation.RESERVE_WALLET_INPUTS,
                                        Status.RESERVED,
                                        new ReservationResult(HEX_A, HEX_B, eleven, false))));
        assertInvalid(
                () ->
                        SettlementEnvelopeCodecV1.encodeResult(
                                result(
                                        Operation.RESERVE_WALLET_INPUTS,
                                        Status.RESERVED,
                                        new ReservationResult(HEX_A, HEX_B, List.of(), false))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReservationResult(
                                HEX_A,
                                HEX_B,
                                List.of(new OutpointIdentity(HEX_C, "0")),
                                true));
        assertInvalid(
                () ->
                        SettlementEnvelopeCodecV1.encodeResult(
                                result(
                                        Operation.SIGN_SETTLEMENT_STAGE,
                                        Status.SIGNED,
                                        new SignedStageResult(
                                                "00", HEX_A, HEX_B, HEX_C, List.of("1", "1")))));
        assertInvalid(
                () ->
                        SettlementEnvelopeCodecV1.encodeResult(
                                result(
                                        Operation.SIGN_SETTLEMENT_STAGE,
                                        Status.SIGNED,
                                        new SignedStageResult(
                                                "00", HEX_A, HEX_B, HEX_C, List.of("2", "1")))));
        assertInvalid(
                () ->
                        SettlementEnvelopeCodecV1.encodeResult(
                                result(
                                        Operation.SIGN_SETTLEMENT_STAGE,
                                        Status.SIGNED,
                                        new SignedStageResult(
                                                "00", HEX_A, HEX_B, HEX_C, List.of()))));
        List<String> elevenIndexes = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            elevenIndexes.add(Integer.toString(index));
        }
        assertInvalid(
                () ->
                        SettlementEnvelopeCodecV1.encodeResult(
                                result(
                                        Operation.SIGN_SETTLEMENT_STAGE,
                                        Status.SIGNED,
                                        new SignedStageResult(
                                                "00", HEX_A, HEX_B, HEX_C, elevenIndexes))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BroadcastSucceededResult(HEX_A, HEX_B, HEX_C, HEX_A));
    }

    @Test
    void enforcesBothNullableMembersAndEveryErrorOperationContextRuleWithoutLeaks() {
        JsonObject broadcast = object(request(Operation.BROADCAST_SETTLEMENT_STAGE));
        assertTrue(broadcast.getAsJsonObject("body").get("recoveryBinding").isJsonNull());
        assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(broadcast)));
        broadcast.getAsJsonObject("body").add("recoveryBinding", recoveryBinding());
        reidentify(broadcast);
        assertDoesNotThrow(() -> SettlementEnvelopeCodecV1.decodeRequest(bytes(broadcast)));

        for (Operation operation : Operation.values()) {
            assertInvalid(
                    () ->
                            SettlementEnvelopeCodecV1.decodeError(
                                    error(operation, HEX_A, ErrorCode.INVALID_REQUEST, false),
                                    new NoSemanticIdentity(operation)));
            assertInvalid(
                    () ->
                            SettlementEnvelopeCodecV1.decodeError(
                                    error(operation, null, ErrorCode.CAPABILITY_UNAVAILABLE, false),
                                    new NoSemanticIdentity(operation)));
            Operation other =
                    operation == Operation.RESERVE_WALLET_INPUTS
                            ? Operation.SIGN_SETTLEMENT_STAGE
                            : Operation.RESERVE_WALLET_INPUTS;
            assertInvalid(
                    () ->
                            SettlementEnvelopeCodecV1.decodeError(
                                    error(operation, null, ErrorCode.INVALID_REQUEST, false),
                                    new NoSemanticIdentity(other)));
            RequestEnvelope context = SettlementEnvelopeCodecV1.decodeRequest(request(operation));
            String semantic = expectedSemantic(context);
            assertInvalid(
                    () ->
                            SettlementEnvelopeCodecV1.decodeError(
                                    error(other, semantic, ErrorCode.CAPABILITY_UNAVAILABLE, false),
                                    new ValidatedRequestIdentity(context)));
        }

        assertInvalid(
                () ->
                        SettlementEnvelopeCodecV1.decodeError(
                                error(
                                        Operation.RESERVE_WALLET_INPUTS,
                                        HEX_A,
                                        ErrorCode.OPERATION_NOT_FOUND,
                                        true),
                                new ValidatedRequestIdentity(
                                        SettlementEnvelopeCodecV1.decodeRequest(
                                                request(Operation.RESERVE_WALLET_INPUTS)))));
        assertInvalid(
                () ->
                        SettlementEnvelopeCodecV1.decodeError(
                                error(
                                        Operation.RESERVE_WALLET_INPUTS,
                                        HEX_A,
                                        ErrorCode.CAPABILITY_UNAVAILABLE,
                                        true),
                                new ValidatedRequestIdentity(
                                        SettlementEnvelopeCodecV1.decodeRequest(
                                                request(Operation.RESERVE_WALLET_INPUTS)))));

        ContextualRequestEnvelope valid =
                (ContextualRequestEnvelope)
                        SettlementEnvelopeCodecV1.decodeRequest(
                                request(Operation.RESERVE_WALLET_INPUTS));
        ChainIdentity invalidChain =
                new ChainIdentity(
                        valid.asset(),
                        "testnet",
                        valid.chainIdentity().genesisHash(),
                        valid.chainIdentity().networkMagicHex(),
                        valid.chainIdentity().p2pkhVersion(),
                        List.of("5", "5"),
                        valid.chainIdentity().signedMessagePrefixHex(),
                        valid.chainIdentity().baseUnitsPerCoin(),
                        valid.chainIdentity().minimumConfirmations(),
                        valid.chainIdentity().chainPolicyDigest());
        ContextualRequestEnvelope untrusted =
                new ContextualRequestEnvelope(
                        valid.schema(),
                        valid.operation(),
                        valid.profileId(),
                        valid.runtimeGenerationId(),
                        valid.parentIntentId(),
                        valid.childStageId(),
                        valid.semanticOperationId(),
                        valid.payloadDigest(),
                        valid.role(),
                        valid.stage(),
                        valid.asset(),
                        invalidChain,
                        valid.policyDigest(),
                        valid.issuedAtUnixMillis(),
                        valid.deadlineUnixMillis(),
                        valid.body());
        IllegalArgumentException checked =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SettlementEnvelopeCodecV1.decodeError(
                                        error(
                                                valid.operation(),
                                                valid.semanticOperationId(),
                                                ErrorCode.CAPABILITY_UNAVAILABLE,
                                                false),
                                        new ValidatedRequestIdentity(untrusted)));
        assertEquals("Invalid settlement envelope.", checked.getMessage());
        assertNull(checked.getCause());
        assertInvalid(
                () ->
                        SettlementEnvelopeCodecV1.decodeError(
                                error(valid.operation(), null, ErrorCode.INVALID_REQUEST, false), null));
        assertThrows(
                NullPointerException.class,
                () ->
                        new ChainIdentity(
                                Asset.LTC,
                                "mainnet",
                                HEX_A,
                                "01020304",
                                "48",
                                Arrays.asList("5", null),
                                "18",
                                "1",
                                "1",
                                HEX_B));
    }

    @Test
    void codecHasNoStoreCallbackGenerationOrFallbackDependency() throws Exception {
        String source =
                java.nio.file.Files.readString(
                        java.nio.file.Path.of(
                                "src/main/java/io/cloudchains/app/net/settlement/SettlementEnvelopeCodecV1.java"));
        for (String forbidden :
                List.of(
                        "InertSettlementOperationStoreV1",
                        "OperationLookup",
                        "FileChannel",
                        "callback",
                        "currentGenerationId",
                        "Class.forName",
                        "getDeclaredMethod")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    private static List<ResultEnvelope> allResultEnvelopes() {
        return List.of(
                result(
                        Operation.RESERVE_WALLET_INPUTS,
                        Status.RESERVED,
                        new ReservationResult(
                                HEX_A,
                                HEX_B,
                                List.of(new OutpointIdentity(HEX_C, "0")),
                                false)),
                result(
                        Operation.PROVE_RESERVED_UTXO_OWNERSHIP,
                        Status.PROOF_CREATED,
                        new OwnershipProofResult(HEX_A, "AQ==", HEX_B)),
                result(
                        Operation.CHECK_OWNED_ADDRESS,
                        Status.OWNERSHIP_CHECKED,
                        new OwnedAddressResult(true)),
                result(
                        Operation.SIGN_SETTLEMENT_STAGE,
                        Status.SIGNED,
                        new SignedStageResult("00", HEX_A, HEX_B, HEX_C, List.of("0"))),
                result(
                        Operation.RESERVE_WALLET_INPUTS,
                        Status.QUARANTINED,
                        new NonBroadcastQuarantinedResult(
                                QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED, HEX_A)),
                result(
                        Operation.PROVE_RESERVED_UTXO_OWNERSHIP,
                        Status.QUARANTINED,
                        new NonBroadcastQuarantinedResult(
                                QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED, HEX_A)),
                result(
                        Operation.CHECK_OWNED_ADDRESS,
                        Status.QUARANTINED,
                        new NonBroadcastQuarantinedResult(
                                QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED, HEX_A)),
                result(
                        Operation.SIGN_SETTLEMENT_STAGE,
                        Status.QUARANTINED,
                        new NonBroadcastQuarantinedResult(
                                QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED, HEX_A)),
                result(
                        Operation.BROADCAST_SETTLEMENT_STAGE,
                        Status.BROADCAST_DISPATCH_RECORDED,
                        new BroadcastDispatchRecordedResult(HEX_A, HEX_B, HEX_C, HEX_A)),
                result(
                        Operation.BROADCAST_SETTLEMENT_STAGE,
                        Status.BROADCAST_SUCCEEDED,
                        new BroadcastSucceededResult(HEX_A, HEX_B, HEX_C, HEX_B)),
                result(
                        Operation.BROADCAST_SETTLEMENT_STAGE,
                        Status.BROADCAST_REJECTED,
                        new BroadcastRejectedResult(
                                HEX_A,
                                HEX_B,
                                HEX_C,
                                BroadcastRejectionCode.BACKEND_DEFINITIVE_REJECTION,
                                HEX_A)),
                result(
                        Operation.BROADCAST_SETTLEMENT_STAGE,
                        Status.QUARANTINED,
                        new BroadcastQuarantinedResult(
                                HEX_A,
                                HEX_B,
                                HEX_C,
                                QuarantineCode.BROADCAST_IDENTITY_MISMATCH,
                                HEX_A)));
    }

    private static JsonObject object(byte[] value) {
        return SettlementCanonicalJsonV1.parse(value).getAsJsonObject();
    }

    private static JsonObject copy(JsonObject value) {
        return object(bytes(value));
    }

    private static byte[] bytes(JsonObject value) {
        return SettlementCanonicalJsonV1.encode(value);
    }

    private static void assertInvalid(Executable executable) {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, executable);
        assertEquals("Invalid settlement envelope.", exception.getMessage());
        assertNull(exception.getCause());
    }

    private static void assertInvalidMutation(JsonObject valid, Consumer<JsonObject> mutation) {
        assertInvalidMutation(valid, mutation, SettlementEnvelopeCodecV1::decodeRequest);
    }

    private static void assertInvalidMutation(
            JsonObject valid, Consumer<JsonObject> mutation, WireDecoder decoder) {
        JsonObject candidate = copy(valid);
        mutation.accept(candidate);
        assertInvalid(() -> decoder.decode(bytes(candidate)));
    }

    private static void assertEveryFieldShapeRejected(JsonObject valid, WireDecoder decoder) {
        for (String field : List.copyOf(valid.keySet())) {
            assertInvalidMutation(valid, value -> value.remove(field), decoder);
            assertInvalidMutation(valid, value -> value.add(field, JsonNull.INSTANCE), decoder);
            assertInvalidMutation(
                    valid, value -> value.add(field, wrongType(value.get(field))), decoder);
        }
        assertInvalidMutation(valid, value -> value.addProperty("unexpected", "x"), decoder);
    }

    private static void assertNestedShapeRejected(
            JsonObject envelope, String field, WireDecoder decoder) {
        JsonObject nested = envelope.getAsJsonObject(field);
        for (String nestedField : List.copyOf(nested.keySet())) {
            assertInvalidMutation(
                    envelope,
                    value -> value.getAsJsonObject(field).remove(nestedField),
                    decoder);
            if (!(field.equals("body") && nestedField.equals("recoveryBinding"))) {
                assertInvalidMutation(
                        envelope,
                        value -> value.getAsJsonObject(field).add(nestedField, JsonNull.INSTANCE),
                        decoder);
            }
            assertInvalidMutation(
                    envelope,
                    value ->
                            value.getAsJsonObject(field)
                                    .add(
                                            nestedField,
                                            wrongType(
                                                    value.getAsJsonObject(field)
                                                            .get(nestedField))),
                    decoder);
        }
        assertInvalidMutation(
                envelope,
                value -> value.getAsJsonObject(field).addProperty("unexpected", "x"),
                decoder);
    }

    private static void assertNestedObjectShapeRejected(
            JsonObject envelope,
            JsonObject ignoredParent,
            String field,
            WireDecoder decoder) {
        JsonObject nested = envelope.getAsJsonObject("body").getAsJsonObject(field);
        for (String nestedField : List.copyOf(nested.keySet())) {
            assertInvalidMutation(
                    envelope,
                    value -> value.getAsJsonObject("body").getAsJsonObject(field).remove(nestedField),
                    decoder);
            assertInvalidMutation(
                    envelope,
                    value ->
                            value.getAsJsonObject("body")
                                    .getAsJsonObject(field)
                                    .add(nestedField, JsonNull.INSTANCE),
                    decoder);
            assertInvalidMutation(
                    envelope,
                    value ->
                            value.getAsJsonObject("body")
                                    .getAsJsonObject(field)
                                    .add(nestedField, new com.google.gson.JsonPrimitive(true)),
                    decoder);
        }
        assertInvalidMutation(
                envelope,
                value ->
                        value.getAsJsonObject("body")
                                .getAsJsonObject(field)
                                .addProperty("unexpected", "x"),
                decoder);
    }

    private static void assertArrayElementShapeRejected(
            JsonObject envelope,
            JsonArray ignoredArray,
            int index,
            WireDecoder decoder) {
        String arrayName = findArrayName(envelope.getAsJsonObject("body"), ignoredArray);
        JsonObject element =
                envelope.getAsJsonObject("body").getAsJsonArray(arrayName).get(index).getAsJsonObject();
        for (String field : List.copyOf(element.keySet())) {
            assertInvalidMutation(
                    envelope,
                    value ->
                            value.getAsJsonObject("body")
                                    .getAsJsonArray(arrayName)
                                    .get(index)
                                    .getAsJsonObject()
                                    .remove(field),
                    decoder);
            assertInvalidMutation(
                    envelope,
                    value ->
                            value.getAsJsonObject("body")
                                    .getAsJsonArray(arrayName)
                                    .get(index)
                                    .getAsJsonObject()
                                    .add(field, JsonNull.INSTANCE),
                    decoder);
            assertInvalidMutation(
                    envelope,
                    value ->
                            value.getAsJsonObject("body")
                                    .getAsJsonArray(arrayName)
                                    .get(index)
                                    .getAsJsonObject()
                                    .add(field, new com.google.gson.JsonPrimitive(true)),
                    decoder);
        }
        assertInvalidMutation(
                envelope,
                value ->
                        value.getAsJsonObject("body")
                                .getAsJsonArray(arrayName)
                                .get(index)
                                .getAsJsonObject()
                                .addProperty("unexpected", "x"),
                decoder);
    }

    private static String findArrayName(JsonObject body, JsonArray identity) {
        for (String name : body.keySet()) {
            if (body.get(name) == identity) {
                return name;
            }
        }
        throw new AssertionError();
    }

    private static void assertReidentifiedRequestRejected(
            JsonObject base, String label, Consumer<JsonObject> mutation) {
        JsonObject candidate = copy(base);
        mutation.accept(candidate);
        try {
            reidentify(candidate);
        } catch (RuntimeException ignored) {
            // Invalid canonical fields can be rejected while deriving their identity.
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementEnvelopeCodecV1.decodeRequest(bytes(candidate)),
                label);
    }

    private static com.google.gson.JsonElement wrongType(com.google.gson.JsonElement original) {
        if (original != null && original.isJsonPrimitive()
                && original.getAsJsonPrimitive().isBoolean()) {
            return new com.google.gson.JsonPrimitive("not-a-boolean");
        }
        return new com.google.gson.JsonPrimitive(true);
    }

    private static void reidentify(JsonObject request) {
        Operation operation =
                Arrays.stream(Operation.values())
                        .filter(value -> value.wireValue().equals(request.get("operation").getAsString()))
                        .findFirst()
                        .orElseThrow();
        if (operation == Operation.GET_SETTLEMENT_OPERATION_RESULT) {
            return;
        }
        JsonObject body = request.getAsJsonObject("body");
        JsonObject chain = request.getAsJsonObject("chainIdentity");
        String parent = operation == Operation.CHECK_OWNED_ADDRESS ? "" : request.get("parentIntentId").getAsString();
        String child = operation == Operation.CHECK_OWNED_ADDRESS ? "" : request.get("childStageId").getAsString();
        String role = operation == Operation.CHECK_OWNED_ADDRESS ? "" : request.get("role").getAsString();
        String stage = operation == Operation.CHECK_OWNED_ADDRESS ? "" : request.get("stage").getAsString();
        Asset asset = Asset.valueOf(request.get("asset").getAsString());
        String objectId = operationObjectId(operation, body);
        String semantic =
                SettlementIdentityV1.semanticOperationId(
                        new IdentityInputs(
                                operation,
                                request.get("profileId").getAsString(),
                                parent,
                                child,
                                role,
                                stage,
                                asset,
                                chain.get("chainPolicyDigest").getAsString(),
                                request.get("policyDigest").getAsString(),
                                objectId));
        String payload =
                SettlementIdentityV1.payloadDigest(
                        new PayloadInputs(
                                operation,
                                request.get("profileId").getAsString(),
                                parent,
                                child,
                                role,
                                stage,
                                asset,
                                digest(bytes(chain)),
                                chain.get("chainPolicyDigest").getAsString(),
                                request.get("policyDigest").getAsString(),
                                request.get("deadlineUnixMillis").getAsString(),
                                objectId,
                                digest(bytes(body))));
        request.addProperty("semanticOperationId", semantic);
        request.addProperty("payloadDigest", payload);
    }

    private static void populatePrevouts(JsonArray array, int count) {
        while (!array.isEmpty()) {
            array.remove(array.size() - 1);
        }
        for (int index = 0; index < count; index++) {
            JsonObject value = prevout();
            value.addProperty("txid", "%02x".formatted(index).repeat(32));
            value.addProperty("vout", Integer.toString(index));
            array.add(value);
        }
    }

    private static void populateSequences(JsonArray array, int count) {
        while (!array.isEmpty()) {
            array.remove(array.size() - 1);
        }
        for (int index = 0; index < count; index++) {
            array.add(Integer.toString(index));
        }
    }

    private static void populateExpectedOutputs(JsonArray array, int count) {
        while (!array.isEmpty()) {
            array.remove(array.size() - 1);
        }
        for (int index = 0; index < count; index++) {
            JsonObject output = new JsonObject();
            output.addProperty("index", Integer.toString(index));
            output.addProperty("valueBaseUnits", "1");
            output.addProperty("scriptPubKeyHex", "00");
            array.add(output);
        }
    }

    private static JsonObject recoveryBinding() {
        JsonObject value = new JsonObject();
        value.addProperty("recoveryRecordId", HEX_A);
        value.addProperty("fundingTxid", HEX_B);
        value.addProperty("refundTxid", HEX_C);
        value.addProperty("recoveryPayloadDigest", HEX_A);
        value.addProperty("persistedAtUnixMillis", "0");
        return value;
    }

    private static int countObjectMembers(com.google.gson.JsonElement value) {
        if (value.isJsonObject()) {
            int total = value.getAsJsonObject().size();
            for (var entry : value.getAsJsonObject().entrySet()) {
                total += countObjectMembers(entry.getValue());
            }
            return total;
        }
        if (value.isJsonArray()) {
            int total = 0;
            for (com.google.gson.JsonElement child : value.getAsJsonArray()) {
                total += countObjectMembers(child);
            }
            return total;
        }
        return 0;
    }

    private static int countArrayEntries(com.google.gson.JsonElement value) {
        if (value.isJsonArray()) {
            int total = value.getAsJsonArray().size();
            for (com.google.gson.JsonElement child : value.getAsJsonArray()) {
                total += countArrayEntries(child);
            }
            return total;
        }
        if (value.isJsonObject()) {
            int total = 0;
            for (var entry : value.getAsJsonObject().entrySet()) {
                total += countArrayEntries(entry.getValue());
            }
            return total;
        }
        return 0;
    }

    private static String expectedSemantic(RequestEnvelope request) {
        if (request instanceof ContextualRequestEnvelope value) {
            return value.semanticOperationId();
        }
        if (request instanceof OwnedAddressRequestEnvelope value) {
            return value.semanticOperationId();
        }
        return ((LookupRequestEnvelope) request).body().targetSemanticOperationId();
    }

    @FunctionalInterface
    private interface WireDecoder {
        Object decode(byte[] value);
    }

    static byte[] request(Operation operation) {
        JsonObject body = body(operation);
        JsonObject chain = chainJson();
        String parent = operation == Operation.CHECK_OWNED_ADDRESS ? "" : SettlementIdentityV1Test.PARENT;
        String child = operation == Operation.CHECK_OWNED_ADDRESS ? "" : SettlementIdentityV1Test.CHILD;
        String role = operation == Operation.CHECK_OWNED_ADDRESS ? "" : "taker";
        String stage = operation == Operation.CHECK_OWNED_ADDRESS ? "" : "taker-deposit";
        String semantic;
        String payload;
        if (operation == Operation.GET_SETTLEMENT_OPERATION_RESULT) {
            semantic = HEX_A;
            payload = HEX_B;
            body.addProperty("targetSemanticOperationId", semantic);
            body.addProperty("expectedPayloadDigest", payload);
            parent = "";
            child = "";
            role = "";
            stage = "";
        } else {
            String objectId = operationObjectId(operation, body);
            semantic =
                    SettlementIdentityV1.semanticOperationId(
                            new IdentityInputs(
                                    operation,
                                    SettlementIdentityV1Test.PROFILE,
                                    parent,
                                    child,
                                    role,
                                    stage,
                                    Asset.LTC,
                                    SettlementIdentityV1Test.CHAIN_POLICY,
                                    SettlementIdentityV1Test.POLICY,
                                    objectId));
            payload =
                    SettlementIdentityV1.payloadDigest(
                            new PayloadInputs(
                                    operation,
                                    SettlementIdentityV1Test.PROFILE,
                                    parent,
                                    child,
                                    role,
                                    stage,
                                    Asset.LTC,
                                    digest(SettlementCanonicalJsonV1.encode(chain)),
                                    SettlementIdentityV1Test.CHAIN_POLICY,
                                    SettlementIdentityV1Test.POLICY,
                                    "200",
                                    objectId,
                                    digest(SettlementCanonicalJsonV1.encode(body))));
        }
        JsonObject request = new JsonObject();
        request.addProperty("schema", Schema.REQUEST.wireValue());
        request.addProperty("operation", operation.wireValue());
        request.addProperty("profileId", SettlementIdentityV1Test.PROFILE);
        request.addProperty("runtimeGenerationId", SettlementIdentityV1Test.GENERATION);
        if (operation != Operation.CHECK_OWNED_ADDRESS) {
            request.addProperty("parentIntentId", parent);
            request.addProperty("childStageId", child);
        }
        request.addProperty("semanticOperationId", semantic);
        request.addProperty("payloadDigest", payload);
        if (operation != Operation.CHECK_OWNED_ADDRESS) {
            request.addProperty("role", role);
            request.addProperty("stage", stage);
        }
        request.addProperty("asset", "LTC");
        request.add("chainIdentity", chain);
        request.addProperty("policyDigest", SettlementIdentityV1Test.POLICY);
        request.addProperty("issuedAtUnixMillis", "100");
        request.addProperty("deadlineUnixMillis", "200");
        request.add("body", body);
        return SettlementCanonicalJsonV1.encode(request);
    }

    static ResultEnvelope result(Operation operation, Status status, ResultBody body) {
        ResultDigestInputs inputs =
                new ResultDigestInputs(operation, HEX_A, HEX_B, status, "300", body);
        return new ResultEnvelope(
                Schema.RESULT,
                operation,
                HEX_A,
                HEX_B,
                status,
                SettlementIdentityV1.resultDigest(inputs),
                "300",
                body);
    }

    private static byte[] error(
            Operation operation, String semantic, ErrorCode code, boolean quarantined) {
        JsonObject object = new JsonObject();
        object.addProperty("schema", Schema.ERROR.wireValue());
        object.addProperty("operation", operation.wireValue());
        if (semantic == null) {
            object.add("semanticOperationId", JsonNull.INSTANCE);
        } else {
            object.addProperty("semanticOperationId", semantic);
        }
        object.addProperty("code", code.wireValue());
        object.addProperty("quarantined", quarantined);
        object.addProperty("recordedAtUnixMillis", "300");
        return SettlementCanonicalJsonV1.encode(object);
    }

    private static JsonObject body(Operation operation) {
        JsonObject body = new JsonObject();
        switch (operation) {
            case RESERVE_WALLET_INPUTS -> {
                body.addProperty("purpose", "deposit");
                body.addProperty("walletAddress", "Labc");
                body.addProperty("baseAmountBlockUnits", "1");
                body.addProperty("outgoingAmountUnits", "1");
                body.addProperty("fixedTakerFeeBlockUnits", "0");
                body.addProperty("maxStageNativeFeeUnits", "1");
                body.addProperty("maxGraphNativeFeeUnits", "1");
                body.addProperty("recoveryReserveUnits", "0");
                JsonArray outpoints = new JsonArray();
                outpoints.add(prevout());
                body.add("outpoints", outpoints);
            }
            case PROVE_RESERVED_UTXO_OWNERSHIP -> {
                body.addProperty("reservationId", HEX_A);
                JsonObject outpoint = new JsonObject();
                outpoint.addProperty("txid", HEX_B);
                outpoint.addProperty("vout", "0");
                body.add("outpoint", outpoint);
                body.addProperty("messageHex", "00");
                body.addProperty("messageDigest", HEX_C);
                body.addProperty("purpose", "xbridge-reserved-utxo-proof-v1");
            }
            case CHECK_OWNED_ADDRESS -> {
                body.addProperty("address", "Labc");
                body.addProperty("purpose", "xbridge-owned-address-probe-v1");
            }
            case SIGN_SETTLEMENT_STAGE -> {
                body.addProperty("reservationId", HEX_A);
                body.addProperty("unsignedTransactionHex", "00");
                body.addProperty("unsignedBytesDigest", HEX_B);
                body.addProperty("unsignedTxid", HEX_C);
                body.addProperty("unsignedWtxid", HEX_A);
                JsonArray previous = new JsonArray();
                previous.add(prevout());
                body.add("previousOutputs", previous);
                JsonArray expected = new JsonArray();
                JsonObject output = new JsonObject();
                output.addProperty("index", "0");
                output.addProperty("valueBaseUnits", "1");
                output.addProperty("scriptPubKeyHex", "00");
                expected.add(output);
                body.add("expectedOutputs", expected);
                body.addProperty("changeAddress", "Labc");
                JsonArray sequences = new JsonArray();
                sequences.add("0");
                body.add("inputSequences", sequences);
                body.addProperty("lockTime", "0");
                body.addProperty("sighashPolicy", "all");
                body.addProperty("maxStageNativeFeeUnits", "1");
                body.addProperty("contractPolicyDigest", HEX_B);
            }
            case BROADCAST_SETTLEMENT_STAGE -> {
                body.addProperty("finalTransactionHex", "00");
                body.addProperty("finalBytesDigest", HEX_A);
                body.addProperty("finalTxid", HEX_B);
                body.addProperty("finalWtxid", HEX_C);
                body.addProperty("producer", "xlite-wallet-input-signer-v1");
                body.addProperty("predecessorOperationId", HEX_A);
                body.addProperty("contractPolicyDigest", HEX_B);
                body.add("recoveryBinding", JsonNull.INSTANCE);
            }
            case GET_SETTLEMENT_OPERATION_RESULT -> {
                // The target fields are supplied after the fixed lookup identity is selected.
            }
        }
        return body;
    }

    private static JsonObject prevout() {
        JsonObject value = new JsonObject();
        value.addProperty("txid", HEX_B);
        value.addProperty("vout", "0");
        value.addProperty("valueBaseUnits", "1");
        value.addProperty("scriptPubKeyHex", "00");
        value.addProperty("ownedAddress", "Labc");
        value.addProperty("confirmationBlockHash", HEX_C);
        value.addProperty("confirmationHeight", "0");
        value.addProperty("observedConfirmations", "0");
        return value;
    }

    private static JsonObject chainJson() {
        ChainIdentity chain = SettlementIdentityV1Test.validChain(Asset.LTC);
        JsonObject object = new JsonObject();
        object.addProperty("asset", chain.asset().wireValue());
        object.addProperty("network", chain.network());
        object.addProperty("genesisHash", chain.genesisHash());
        object.addProperty("networkMagicHex", chain.networkMagicHex());
        object.addProperty("p2pkhVersion", chain.p2pkhVersion());
        JsonArray p2sh = new JsonArray();
        chain.p2shVersions().forEach(p2sh::add);
        object.add("p2shVersions", p2sh);
        object.addProperty("signedMessagePrefixHex", chain.signedMessagePrefixHex());
        object.addProperty("baseUnitsPerCoin", chain.baseUnitsPerCoin());
        object.addProperty("minimumConfirmations", chain.minimumConfirmations());
        object.addProperty("chainPolicyDigest", chain.chainPolicyDigest());
        return object;
    }

    private static String operationObjectId(Operation operation, JsonObject body) {
        if (operation == Operation.RESERVE_WALLET_INPUTS
                || operation == Operation.SIGN_SETTLEMENT_STAGE
                || operation == Operation.BROADCAST_SETTLEMENT_STAGE) {
            return SettlementIdentityV1Test.CHILD;
        }
        JsonObject identity = new JsonObject();
        if (operation == Operation.PROVE_RESERVED_UTXO_OWNERSHIP) {
            JsonObject outpoint = body.getAsJsonObject("outpoint");
            identity.addProperty("txid", outpoint.get("txid").getAsString());
            identity.addProperty("vout", outpoint.get("vout").getAsString());
        } else {
            identity.addProperty("address", body.get("address").getAsString());
            identity.addProperty("asset", "LTC");
        }
        return digest(SettlementCanonicalJsonV1.encode(identity));
    }

    private static String digest(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
