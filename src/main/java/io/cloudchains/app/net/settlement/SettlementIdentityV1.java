package io.cloudchains.app.net.settlement;

import static io.cloudchains.app.net.settlement.SettlementContractV1.*;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class SettlementIdentityV1 {
    private static final String OP_DOMAIN = "BLOCKNET-XLITE-SETTLEMENT-OP-V1";
    private static final String PAYLOAD_DOMAIN = "BLOCKNET-XLITE-SETTLEMENT-PAYLOAD-V1";
    private static final String RESULT_DOMAIN = "BLOCKNET-XLITE-SETTLEMENT-RESULT-V1";
    private static final String HANDOVER_DOMAIN = "BLOCKNET-XLITE-SETTLEMENT-HANDOVER-V1";

    private SettlementIdentityV1() {}

    static String semanticOperationId(IdentityInputs input) {
        requireIdentityInputs(input);
        return digest(
                framed(
                        OP_DOMAIN,
                        new Field("operation", input.operation().wireValue()),
                        new Field("profileId", input.profileId()),
                        new Field("parentIntentId", input.parentIntentId()),
                        new Field("childStageId", input.childStageId()),
                        new Field("role", input.role()),
                        new Field("stage", input.stage()),
                        new Field("asset", input.asset().wireValue()),
                        new Field("chainPolicyDigest", input.chainPolicyDigest()),
                        new Field("policyDigest", input.policyDigest()),
                        new Field("operationObjectId", input.operationObjectId())));
    }

    static String payloadDigest(PayloadInputs input) {
        requirePayloadInputs(input);
        return digest(
                framed(
                        PAYLOAD_DOMAIN,
                        new Field("operation", input.operation().wireValue()),
                        new Field("profileId", input.profileId()),
                        new Field("parentIntentId", input.parentIntentId()),
                        new Field("childStageId", input.childStageId()),
                        new Field("role", input.role()),
                        new Field("stage", input.stage()),
                        new Field("asset", input.asset().wireValue()),
                        new Field("chainIdentityDigest", input.chainIdentityDigest()),
                        new Field("chainPolicyDigest", input.chainPolicyDigest()),
                        new Field("policyDigest", input.policyDigest()),
                        new Field("deadlineUnixMillis", input.deadlineUnixMillis()),
                        new Field("operationObjectId", input.operationObjectId()),
                        new Field("bodyDigest", input.bodyDigest())));
    }

    static String resultDigest(ResultDigestInputs input) {
        if (input == null) {
            throw invalid();
        }
        byte[] canonical = SettlementEnvelopeCodecV1.encodeResultWithoutDigest(input);
        return digest(framed(RESULT_DOMAIN, new Field("resultEnvelope", ascii(canonical))));
    }

    static String handoverDigest(GenerationHandover input) {
        if (input == null) {
            throw invalid();
        }
        requireHex64(input.profileId());
        requireHex64(input.priorGenerationId());
        requireHex64(input.nextGenerationId());
        requireHex64(input.desktopJournalHeadDigest());
        requireHex64(input.handoverDigest());
        return digest(
                framed(
                        HANDOVER_DOMAIN,
                        new Field("profileId", input.profileId()),
                        new Field("priorGenerationId", input.priorGenerationId()),
                        new Field("nextGenerationId", input.nextGenerationId()),
                        new Field("desktopJournalHeadDigest", input.desktopJournalHeadDigest())));
    }

    private static void requireIdentityInputs(IdentityInputs input) {
        if (input == null || input.operation() == Operation.GET_SETTLEMENT_OPERATION_RESULT) {
            throw invalid();
        }
        requireHex64(input.profileId());
        requireContext(input.operation(), input.parentIntentId(), input.childStageId(), input.role(), input.stage());
        requireHex64(input.chainPolicyDigest());
        requireHex64(input.policyDigest());
        requireHex64(input.operationObjectId());
    }

    private static void requirePayloadInputs(PayloadInputs input) {
        if (input == null || input.operation() == Operation.GET_SETTLEMENT_OPERATION_RESULT) {
            throw invalid();
        }
        requireHex64(input.profileId());
        requireContext(input.operation(), input.parentIntentId(), input.childStageId(), input.role(), input.stage());
        requireHex64(input.chainIdentityDigest());
        requireHex64(input.chainPolicyDigest());
        requireHex64(input.policyDigest());
        requireU64(input.deadlineUnixMillis());
        requireHex64(input.operationObjectId());
        requireHex64(input.bodyDigest());
    }

    private static void requireContext(
            Operation operation, String parent, String child, String role, String stage) {
        if (operation == Operation.CHECK_OWNED_ADDRESS) {
            if (!parent.isEmpty() || !child.isEmpty() || !role.isEmpty() || !stage.isEmpty()) {
                throw invalid();
            }
            return;
        }
        requireHex64(parent);
        requireHex64(child);
        if (!isRole(role) || !isStage(stage)) {
            throw invalid();
        }
    }

    private static boolean isRole(String value) {
        return Role.MAKER.wireValue().equals(value) || Role.TAKER.wireValue().equals(value);
    }

    private static boolean isStage(String value) {
        for (Stage stage : Stage.values()) {
            if (stage.wireValue().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] framed(String domain, Field... fields) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, domain);
        output.write('\n');
        for (Field field : fields) {
            byte[] value = field.value().getBytes(StandardCharsets.UTF_8);
            write(output, field.name());
            output.write(':');
            write(output, Integer.toString(value.length));
            output.write(':');
            output.writeBytes(value);
            output.write('\n');
        }
        return output.toByteArray();
    }

    private static void write(ByteArrayOutputStream output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        output.writeBytes(bytes);
    }

    private static String digest(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String ascii(byte[] value) {
        for (byte item : value) {
            if ((item & 0x80) != 0) {
                throw invalid();
            }
        }
        return new String(value, StandardCharsets.US_ASCII);
    }

    private static void requireHex64(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw invalid();
        }
    }

    private static void requireU64(String value) {
        if (value == null || !value.matches("0|[1-9][0-9]*")) {
            throw invalid();
        }
        try {
            if (new BigInteger(value).bitLength() > 64) {
                throw invalid();
            }
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid settlement identity.");
    }

    private record Field(String name, String value) {}
}
