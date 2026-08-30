package io.cloudchains.app.net.settlement;

import static io.cloudchains.app.net.settlement.SettlementContractV1.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SettlementIdentityV1Test {
    static final String SEMANTIC =
            "718fd3390ec6b05e15306b9fb331db604aba5457e5b95f3c8da4c4248fc92016";
    static final String PAYLOAD =
            "f7c2b4911611397cddaa42f3f56cd25a6eeeafc39efc5b1c1fcf30cf13b2cbd9";
    static final String RESULT =
            "97230fe9d7520b9d95a8a068ad507694b89b7fdfec61cc096aba8611c8bcc2b0";
    static final String PROFILE = "11".repeat(32);
    static final String GENERATION = "88".repeat(32);
    static final String PARENT = "22".repeat(32);
    static final String CHILD = "33".repeat(32);
    static final String POLICY = "55".repeat(32);
    static final String CHAIN_POLICY = "44".repeat(32);

    @Test
    void reproducesSemanticPayloadHandoverAndResultVectors() throws Exception {
        IdentityInputs identity =
                new IdentityInputs(
                        Operation.RESERVE_WALLET_INPUTS,
                        PROFILE,
                        PARENT,
                        CHILD,
                        "taker",
                        "taker-deposit",
                        Asset.LTC,
                        CHAIN_POLICY,
                        POLICY,
                        CHILD);
        assertEquals(SEMANTIC, SettlementIdentityV1.semanticOperationId(identity));

        PayloadInputs payload =
                new PayloadInputs(
                        Operation.RESERVE_WALLET_INPUTS,
                        PROFILE,
                        PARENT,
                        CHILD,
                        "taker",
                        "taker-deposit",
                        Asset.LTC,
                        "77".repeat(32),
                        CHAIN_POLICY,
                        POLICY,
                        "1780000000000",
                        CHILD,
                        "66".repeat(32));
        assertEquals(PAYLOAD, SettlementIdentityV1.payloadDigest(payload));

        GenerationHandover handover =
                new GenerationHandover(
                        PROFILE,
                        GENERATION,
                        "99".repeat(32),
                        "aa".repeat(32),
                        "00".repeat(32));
        assertEquals(
                "b5895ff95be0245f82ab2cfb9883022453b141c2e5f2f83ef10a0491a7a96b8b",
                SettlementIdentityV1.handoverDigest(handover));

        ResultDigestInputs result = vectorResultInputs();
        byte[] canonical = SettlementEnvelopeCodecV1.encodeResultWithoutDigest(result);
        assertEquals(614, canonical.length);
        assertEquals(expectedCanonicalResultWithoutDigest(), new String(canonical, StandardCharsets.UTF_8));
        assertEquals(RESULT, SettlementIdentityV1.resultDigest(result));
        byte[] preimage =
                ("BLOCKNET-XLITE-SETTLEMENT-RESULT-V1\nresultEnvelope:614:"
                                + new String(canonical, StandardCharsets.UTF_8)
                                + "\n")
                        .getBytes(StandardCharsets.UTF_8);
        assertEquals(670, preimage.length);
        assertEquals(
                RESULT,
                HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(preimage)));
    }

    @Test
    void rejectsEverySingleNibbleResultDigestMutationOnDecodeAndEncode() {
        ResultEnvelope valid = vectorResultEnvelope();
        byte[] validBytes = SettlementEnvelopeCodecV1.encodeResult(valid);
        assertEquals(valid, SettlementEnvelopeCodecV1.decodeResult(validBytes));

        for (int index = 0; index < 64; index++) {
            char[] mutated = RESULT.toCharArray();
            int value = Character.digit(mutated[index], 16);
            mutated[index] = Character.forDigit((value + 1) & 0xf, 16);
            ResultEnvelope invalid =
                    new ResultEnvelope(
                            valid.schema(),
                            valid.operation(),
                            valid.semanticOperationId(),
                            valid.payloadDigest(),
                            valid.status(),
                            new String(mutated),
                            valid.recordedAtUnixMillis(),
                            valid.body());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SettlementEnvelopeCodecV1.encodeResult(invalid),
                    "position " + index);
            String wire =
                    new String(validBytes, StandardCharsets.UTF_8)
                            .replace(RESULT, new String(mutated));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SettlementEnvelopeCodecV1.decodeResult(wire.getBytes(StandardCharsets.UTF_8)),
                    "position " + index);
        }
    }

    @Test
    void everySemanticPayloadHandoverAndResultFieldOrderAndExclusionIsFrozen() throws Exception {
        IdentityInputs base =
                new IdentityInputs(
                        Operation.RESERVE_WALLET_INPUTS,
                        PROFILE,
                        PARENT,
                        CHILD,
                        "taker",
                        "taker-deposit",
                        Asset.LTC,
                        CHAIN_POLICY,
                        POLICY,
                        CHILD);
        String digest = SettlementIdentityV1.semanticOperationId(base);
        assertEquals(
                digest,
                independentDigest(
                        "BLOCKNET-XLITE-SETTLEMENT-OP-V1",
                        fields(
                                "operation", "reserveWalletInputs",
                                "profileId", PROFILE,
                                "parentIntentId", PARENT,
                                "childStageId", CHILD,
                                "role", "taker",
                                "stage", "taker-deposit",
                                "asset", "LTC",
                                "chainPolicyDigest", CHAIN_POLICY,
                                "policyDigest", POLICY,
                                "operationObjectId", CHILD)));
        for (IdentityInputs changed :
                List.of(
                        new IdentityInputs(Operation.SIGN_SETTLEMENT_STAGE, PROFILE, PARENT, CHILD, "taker", "taker-deposit", Asset.LTC, CHAIN_POLICY, POLICY, CHILD),
                        new IdentityInputs(base.operation(), "12".repeat(32), PARENT, CHILD, "taker", "taker-deposit", Asset.LTC, CHAIN_POLICY, POLICY, CHILD),
                        new IdentityInputs(base.operation(), PROFILE, "23".repeat(32), CHILD, "taker", "taker-deposit", Asset.LTC, CHAIN_POLICY, POLICY, CHILD),
                        new IdentityInputs(base.operation(), PROFILE, PARENT, "34".repeat(32), "taker", "taker-deposit", Asset.LTC, CHAIN_POLICY, POLICY, CHILD),
                        new IdentityInputs(base.operation(), PROFILE, PARENT, CHILD, "maker", "taker-deposit", Asset.LTC, CHAIN_POLICY, POLICY, CHILD),
                        new IdentityInputs(base.operation(), PROFILE, PARENT, CHILD, "taker", "maker-deposit", Asset.LTC, CHAIN_POLICY, POLICY, CHILD),
                        new IdentityInputs(base.operation(), PROFILE, PARENT, CHILD, "taker", "taker-deposit", Asset.BLOCK, CHAIN_POLICY, POLICY, CHILD),
                        new IdentityInputs(base.operation(), PROFILE, PARENT, CHILD, "taker", "taker-deposit", Asset.LTC, "45".repeat(32), POLICY, CHILD),
                        new IdentityInputs(base.operation(), PROFILE, PARENT, CHILD, "taker", "taker-deposit", Asset.LTC, CHAIN_POLICY, "56".repeat(32), CHILD),
                        new IdentityInputs(base.operation(), PROFILE, PARENT, CHILD, "taker", "taker-deposit", Asset.LTC, CHAIN_POLICY, POLICY, "35".repeat(32)))) {
            assertNotEquals(digest, SettlementIdentityV1.semanticOperationId(changed), changed.toString());
        }
        assertNotEquals(
                digest,
                independentDigest(
                        "BLOCKNET-XLITE-SETTLEMENT-OP-V1",
                        fields(
                                "profileId", PROFILE,
                                "operation", "reserveWalletInputs",
                                "parentIntentId", PARENT,
                                "childStageId", CHILD,
                                "role", "taker",
                                "stage", "taker-deposit",
                                "asset", "LTC",
                                "chainPolicyDigest", CHAIN_POLICY,
                                "policyDigest", POLICY,
                                "operationObjectId", CHILD)));

        PayloadInputs payload =
                new PayloadInputs(
                        Operation.RESERVE_WALLET_INPUTS,
                        PROFILE,
                        PARENT,
                        CHILD,
                        "taker",
                        "taker-deposit",
                        Asset.LTC,
                        "77".repeat(32),
                        CHAIN_POLICY,
                        POLICY,
                        "1780000000000",
                        CHILD,
                        "66".repeat(32));
        String payloadDigest = SettlementIdentityV1.payloadDigest(payload);
        assertEquals(
                payloadDigest,
                independentDigest(
                        "BLOCKNET-XLITE-SETTLEMENT-PAYLOAD-V1",
                        fields(
                                "operation", "reserveWalletInputs",
                                "profileId", PROFILE,
                                "parentIntentId", PARENT,
                                "childStageId", CHILD,
                                "role", "taker",
                                "stage", "taker-deposit",
                                "asset", "LTC",
                                "chainIdentityDigest", "77".repeat(32),
                                "chainPolicyDigest", CHAIN_POLICY,
                                "policyDigest", POLICY,
                                "deadlineUnixMillis", "1780000000000",
                                "operationObjectId", CHILD,
                                "bodyDigest", "66".repeat(32))));
        for (PayloadInputs changed : payloadVariants(payload)) {
            assertNotEquals(payloadDigest, SettlementIdentityV1.payloadDigest(changed), changed.toString());
        }
        assertNotEquals(
                payloadDigest,
                independentDigest(
                        "BLOCKNET-XLITE-SETTLEMENT-PAYLOAD-V1",
                        fields(
                                "operation", "reserveWalletInputs",
                                "profileId", PROFILE,
                                "parentIntentId", PARENT,
                                "childStageId", CHILD,
                                "role", "taker",
                                "stage", "taker-deposit",
                                "asset", "LTC",
                                "chainPolicyDigest", CHAIN_POLICY,
                                "chainIdentityDigest", "77".repeat(32),
                                "policyDigest", POLICY,
                                "deadlineUnixMillis", "1780000000000",
                                "operationObjectId", CHILD,
                                "bodyDigest", "66".repeat(32))));

        GenerationHandover handover =
                new GenerationHandover(
                        PROFILE,
                        GENERATION,
                        "99".repeat(32),
                        "aa".repeat(32),
                        "00".repeat(32));
        String handoverDigest = SettlementIdentityV1.handoverDigest(handover);
        assertEquals(
                handoverDigest,
                independentDigest(
                        "BLOCKNET-XLITE-SETTLEMENT-HANDOVER-V1",
                        fields(
                                "profileId", PROFILE,
                                "priorGenerationId", GENERATION,
                                "nextGenerationId", "99".repeat(32),
                                "desktopJournalHeadDigest", "aa".repeat(32))));
        for (GenerationHandover changed :
                List.of(
                        new GenerationHandover("12".repeat(32), GENERATION, "99".repeat(32), "aa".repeat(32), handover.handoverDigest()),
                        new GenerationHandover(PROFILE, "89".repeat(32), "99".repeat(32), "aa".repeat(32), handover.handoverDigest()),
                        new GenerationHandover(PROFILE, GENERATION, "98".repeat(32), "aa".repeat(32), handover.handoverDigest()),
                        new GenerationHandover(PROFILE, GENERATION, "99".repeat(32), "ab".repeat(32), handover.handoverDigest()))) {
            assertNotEquals(handoverDigest, SettlementIdentityV1.handoverDigest(changed));
        }
        GenerationHandover excludedDigest =
                new GenerationHandover(
                        handover.profileId(),
                        handover.priorGenerationId(),
                        handover.nextGenerationId(),
                        handover.desktopJournalHeadDigest(),
                        "ff".repeat(32));
        assertEquals(handoverDigest, SettlementIdentityV1.handoverDigest(excludedDigest));
        assertNotEquals(
                handoverDigest,
                independentDigest(
                        "BLOCKNET-XLITE-SETTLEMENT-HANDOVER-V1",
                        fields(
                                "priorGenerationId", GENERATION,
                                "profileId", PROFILE,
                                "nextGenerationId", "99".repeat(32),
                                "desktopJournalHeadDigest", "aa".repeat(32))));

        ResultDigestInputs result = vectorResultInputs();
        String resultDigest = SettlementIdentityV1.resultDigest(result);
        List<ResultDigestInputs> resultChanges =
                List.of(
                        new ResultDigestInputs(Operation.PROVE_RESERVED_UTXO_OWNERSHIP, result.semanticOperationId(), result.payloadDigest(), Status.PROOF_CREATED, result.recordedAtUnixMillis(), new OwnershipProofResult("bb".repeat(32), "AQ==", "cc".repeat(32))),
                        new ResultDigestInputs(result.operation(), "72".repeat(32), result.payloadDigest(), result.status(), result.recordedAtUnixMillis(), result.body()),
                        new ResultDigestInputs(result.operation(), result.semanticOperationId(), "f8".repeat(32), result.status(), result.recordedAtUnixMillis(), result.body()),
                        new ResultDigestInputs(result.operation(), result.semanticOperationId(), result.payloadDigest(), result.status(), "1780000000124", result.body()),
                        new ResultDigestInputs(result.operation(), result.semanticOperationId(), result.payloadDigest(), result.status(), result.recordedAtUnixMillis(), new ReservationResult("bc".repeat(32), "cc".repeat(32), List.of(new OutpointIdentity("dd".repeat(32), "0")), false)));
        for (ResultDigestInputs changed : resultChanges) {
            assertNotEquals(resultDigest, SettlementIdentityV1.resultDigest(changed));
        }
        ResultDigestInputs statusOnlyInvalid =
                new ResultDigestInputs(
                        result.operation(),
                        result.semanticOperationId(),
                        result.payloadDigest(),
                        Status.QUARANTINED,
                        result.recordedAtUnixMillis(),
                        result.body());
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementIdentityV1.resultDigest(statusOnlyInvalid));
    }

    static ResultDigestInputs vectorResultInputs() {
        return new ResultDigestInputs(
                Operation.RESERVE_WALLET_INPUTS,
                SEMANTIC,
                PAYLOAD,
                Status.RESERVED,
                "1780000000123",
                new ReservationResult(
                        "bb".repeat(32),
                        "cc".repeat(32),
                        List.of(new OutpointIdentity("dd".repeat(32), "0")),
                        false));
    }

    static ResultEnvelope vectorResultEnvelope() {
        ResultDigestInputs input = vectorResultInputs();
        return new ResultEnvelope(
                Schema.RESULT,
                input.operation(),
                input.semanticOperationId(),
                input.payloadDigest(),
                input.status(),
                RESULT,
                input.recordedAtUnixMillis(),
                input.body());
    }

    static ChainIdentity validChain(Asset asset) {
        return new ChainIdentity(
                asset,
                "mainnet",
                "ab".repeat(32),
                "01020304",
                "48",
                List.of("5", "50"),
                "18",
                "100000000",
                "1",
                CHAIN_POLICY);
    }

    static TargetContext validTarget(Operation operation) {
        boolean owned = operation == Operation.CHECK_OWNED_ADDRESS;
        return new TargetContext(
                PROFILE,
                owned ? "" : PARENT,
                owned ? "" : CHILD,
                owned ? "" : "taker",
                owned ? "" : "taker-deposit",
                Asset.LTC,
                validChain(Asset.LTC),
                POLICY);
    }

    private static List<PayloadInputs> payloadVariants(PayloadInputs base) {
        return List.of(
                new PayloadInputs(Operation.SIGN_SETTLEMENT_STAGE, base.profileId(), base.parentIntentId(), base.childStageId(), base.role(), base.stage(), base.asset(), base.chainIdentityDigest(), base.chainPolicyDigest(), base.policyDigest(), base.deadlineUnixMillis(), base.operationObjectId(), base.bodyDigest()),
                new PayloadInputs(base.operation(), "12".repeat(32), base.parentIntentId(), base.childStageId(), base.role(), base.stage(), base.asset(), base.chainIdentityDigest(), base.chainPolicyDigest(), base.policyDigest(), base.deadlineUnixMillis(), base.operationObjectId(), base.bodyDigest()),
                new PayloadInputs(base.operation(), base.profileId(), "23".repeat(32), base.childStageId(), base.role(), base.stage(), base.asset(), base.chainIdentityDigest(), base.chainPolicyDigest(), base.policyDigest(), base.deadlineUnixMillis(), base.operationObjectId(), base.bodyDigest()),
                new PayloadInputs(base.operation(), base.profileId(), base.parentIntentId(), "34".repeat(32), base.role(), base.stage(), base.asset(), base.chainIdentityDigest(), base.chainPolicyDigest(), base.policyDigest(), base.deadlineUnixMillis(), base.operationObjectId(), base.bodyDigest()),
                new PayloadInputs(base.operation(), base.profileId(), base.parentIntentId(), base.childStageId(), "maker", base.stage(), base.asset(), base.chainIdentityDigest(), base.chainPolicyDigest(), base.policyDigest(), base.deadlineUnixMillis(), base.operationObjectId(), base.bodyDigest()),
                new PayloadInputs(base.operation(), base.profileId(), base.parentIntentId(), base.childStageId(), base.role(), "maker-deposit", base.asset(), base.chainIdentityDigest(), base.chainPolicyDigest(), base.policyDigest(), base.deadlineUnixMillis(), base.operationObjectId(), base.bodyDigest()),
                new PayloadInputs(base.operation(), base.profileId(), base.parentIntentId(), base.childStageId(), base.role(), base.stage(), Asset.BLOCK, base.chainIdentityDigest(), base.chainPolicyDigest(), base.policyDigest(), base.deadlineUnixMillis(), base.operationObjectId(), base.bodyDigest()),
                new PayloadInputs(base.operation(), base.profileId(), base.parentIntentId(), base.childStageId(), base.role(), base.stage(), base.asset(), "78".repeat(32), base.chainPolicyDigest(), base.policyDigest(), base.deadlineUnixMillis(), base.operationObjectId(), base.bodyDigest()),
                new PayloadInputs(base.operation(), base.profileId(), base.parentIntentId(), base.childStageId(), base.role(), base.stage(), base.asset(), base.chainIdentityDigest(), "45".repeat(32), base.policyDigest(), base.deadlineUnixMillis(), base.operationObjectId(), base.bodyDigest()),
                new PayloadInputs(base.operation(), base.profileId(), base.parentIntentId(), base.childStageId(), base.role(), base.stage(), base.asset(), base.chainIdentityDigest(), base.chainPolicyDigest(), "56".repeat(32), base.deadlineUnixMillis(), base.operationObjectId(), base.bodyDigest()),
                new PayloadInputs(base.operation(), base.profileId(), base.parentIntentId(), base.childStageId(), base.role(), base.stage(), base.asset(), base.chainIdentityDigest(), base.chainPolicyDigest(), base.policyDigest(), "1780000000001", base.operationObjectId(), base.bodyDigest()),
                new PayloadInputs(base.operation(), base.profileId(), base.parentIntentId(), base.childStageId(), base.role(), base.stage(), base.asset(), base.chainIdentityDigest(), base.chainPolicyDigest(), base.policyDigest(), base.deadlineUnixMillis(), "35".repeat(32), base.bodyDigest()),
                new PayloadInputs(base.operation(), base.profileId(), base.parentIntentId(), base.childStageId(), base.role(), base.stage(), base.asset(), base.chainIdentityDigest(), base.chainPolicyDigest(), base.policyDigest(), base.deadlineUnixMillis(), base.operationObjectId(), "67".repeat(32)));
    }

    private static List<TestField> fields(String... namesAndValues) {
        List<TestField> result = new ArrayList<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            result.add(new TestField(namesAndValues[index], namesAndValues[index + 1]));
        }
        return List.copyOf(result);
    }

    private static String independentDigest(String domain, List<TestField> fields)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(domain.getBytes(StandardCharsets.US_ASCII));
        output.write('\n');
        for (TestField field : fields) {
            byte[] value = field.value().getBytes(StandardCharsets.UTF_8);
            output.writeBytes(field.name().getBytes(StandardCharsets.US_ASCII));
            output.write(':');
            output.writeBytes(Integer.toString(value.length).getBytes(StandardCharsets.US_ASCII));
            output.write(':');
            output.writeBytes(value);
            output.write('\n');
        }
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(output.toByteArray()));
    }

    private record TestField(String name, String value) {}

    private static String expectedCanonicalResultWithoutDigest() {
        return "{\"body\":{\"deduplicated\":false,\"outpoints\":[{\"txid\":\""
                + "dd".repeat(32)
                + "\",\"vout\":\"0\"}],\"reservationDigest\":\""
                + "cc".repeat(32)
                + "\",\"reservationId\":\""
                + "bb".repeat(32)
                + "\"},\"operation\":\"reserveWalletInputs\",\"payloadDigest\":\""
                + PAYLOAD
                + "\",\"recordedAtUnixMillis\":\"1780000000123\",\"schema\":\"blocknet.xlite.settlement.result.v1\",\"semanticOperationId\":\""
                + SEMANTIC
                + "\",\"status\":\"reserved\"}";
    }
}
