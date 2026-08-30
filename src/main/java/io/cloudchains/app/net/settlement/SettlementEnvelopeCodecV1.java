package io.cloudchains.app.net.settlement;

import static io.cloudchains.app.net.settlement.SettlementContractV1.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

final class SettlementEnvelopeCodecV1 {
    private static final String INVALID = "Invalid settlement envelope.";
    private static final String ZERO_HEX64 = "0".repeat(64);
    private static final BigInteger U8_MAX = BigInteger.valueOf(255);
    private static final BigInteger U32_MAX = new BigInteger("4294967295");
    private static final BigInteger U64_MAX = new BigInteger("18446744073709551615");

    private SettlementEnvelopeCodecV1() {}

    static RequestEnvelope decodeRequest(byte[] canonicalUtf8) {
        JsonObject object = requireObject(SettlementCanonicalJsonV1.parse(canonicalUtf8));
        Operation operation = parseOperation(requireString(object, "operation"));
        RequestEnvelope envelope = switch (operation) {
            case CHECK_OWNED_ADDRESS -> decodeOwnedAddressRequest(object);
            case GET_SETTLEMENT_OPERATION_RESULT -> decodeLookupRequest(object);
            case RESERVE_WALLET_INPUTS,
                    PROVE_RESERVED_UTXO_OWNERSHIP,
                    SIGN_SETTLEMENT_STAGE,
                    BROADCAST_SETTLEMENT_STAGE -> decodeContextualRequest(object, operation);
        };
        requireRequestIdentity(envelope);
        return envelope;
    }

    static ResultEnvelope decodeResult(byte[] canonicalUtf8) {
        JsonObject object = requireObject(SettlementCanonicalJsonV1.parse(canonicalUtf8));
        requireExactKeys(
                object,
                "schema",
                "operation",
                "semanticOperationId",
                "payloadDigest",
                "status",
                "resultDigest",
                "recordedAtUnixMillis",
                "body");
        requireLiteral(requireString(object, "schema"), Schema.RESULT.wireValue());
        Operation operation = parseOperation(requireString(object, "operation"));
        if (operation == Operation.GET_SETTLEMENT_OPERATION_RESULT) {
            throw invalid();
        }
        String semanticOperationId = requireHex64(requireString(object, "semanticOperationId"));
        String payloadDigest = requireHex64(requireString(object, "payloadDigest"));
        Status status = parseStatus(requireString(object, "status"));
        String resultDigest = requireHex64(requireString(object, "resultDigest"));
        String recordedAt = requireU64(requireString(object, "recordedAtUnixMillis"));
        ResultBody body = decodeResultBody(operation, status, requireObject(object.get("body")));
        ResultEnvelope envelope =
                new ResultEnvelope(
                        Schema.RESULT,
                        operation,
                        semanticOperationId,
                        payloadDigest,
                        status,
                        resultDigest,
                        recordedAt,
                        body);
        String expected =
                SettlementIdentityV1.resultDigest(
                        new ResultDigestInputs(
                                operation,
                                semanticOperationId,
                                payloadDigest,
                                status,
                                recordedAt,
                                body));
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                resultDigest.getBytes(StandardCharsets.US_ASCII))) {
            throw invalid();
        }
        return envelope;
    }

    static ErrorEnvelope decodeError(
            byte[] canonicalUtf8, ExpectedErrorIdentity expectedIdentity) {
        try {
            return decodeErrorValidated(canonicalUtf8, expectedIdentity);
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private static ErrorEnvelope decodeErrorValidated(
            byte[] canonicalUtf8, ExpectedErrorIdentity expectedIdentity) {
        if (expectedIdentity == null) {
            throw invalid();
        }
        JsonObject object = requireObject(SettlementCanonicalJsonV1.parse(canonicalUtf8));
        requireExactKeys(
                object,
                "schema",
                "operation",
                "semanticOperationId",
                "code",
                "quarantined",
                "recordedAtUnixMillis");
        requireLiteral(requireString(object, "schema"), Schema.ERROR.wireValue());
        Operation operation = parseOperation(requireString(object, "operation"));
        ErrorCode code = parseErrorCode(requireString(object, "code"));
        if (code == ErrorCode.BACKEND_DEFINITIVE_REJECTION
                || code == ErrorCode.BROADCAST_IDENTITY_MISMATCH
                || code == ErrorCode.AMBIGUOUS_OUTCOME_QUARANTINED) {
            throw invalid();
        }
        boolean quarantined = requireBoolean(object, "quarantined");
        String recordedAt = requireU64(requireString(object, "recordedAtUnixMillis"));
        JsonElement semanticElement = object.get("semanticOperationId");
        String semanticOperationId;
        if (code == ErrorCode.INVALID_REQUEST || code == ErrorCode.UNAUTHENTICATED) {
            if (!semanticElement.isJsonNull()
                    || !(expectedIdentity instanceof NoSemanticIdentity identity)
                    || identity.operation() != operation
                    || quarantined) {
                throw invalid();
            }
            semanticOperationId = null;
        } else {
            if (!(expectedIdentity instanceof ValidatedRequestIdentity identity)) {
                throw invalid();
            }
            RequestEnvelope validated = revalidateRequest(identity.request());
            if (requestOperation(validated) != operation) {
                throw invalid();
            }
            semanticOperationId = requireHex64(requireString(semanticElement));
            String expected = expectedSemanticOperationId(validated);
            if (!semanticOperationId.equals(expected)) {
                throw invalid();
            }
            boolean expectedQuarantine =
                    code == ErrorCode.OPERATION_NOT_FOUND
                            || code == ErrorCode.PERSISTENCE_UNAVAILABLE;
            if (quarantined != expectedQuarantine
                    || (code == ErrorCode.OPERATION_NOT_FOUND
                            && operation != Operation.GET_SETTLEMENT_OPERATION_RESULT)) {
                throw invalid();
            }
        }
        return new ErrorEnvelope(
                Schema.ERROR,
                operation,
                semanticOperationId,
                code,
                quarantined,
                recordedAt);
    }

    static byte[] encodeResult(ResultEnvelope envelope) {
        if (envelope == null) {
            throw invalid();
        }
        JsonObject object = encodeResultObject(envelope, true);
        byte[] encoded = SettlementCanonicalJsonV1.encode(object);
        ResultEnvelope decoded = decodeResult(encoded);
        if (!decoded.equals(envelope)) {
            throw invalid();
        }
        return encoded;
    }

    static byte[] encodeResultWithoutDigest(ResultDigestInputs input) {
        if (input == null) {
            throw invalid();
        }
        ResultEnvelope envelope =
                new ResultEnvelope(
                        Schema.RESULT,
                        input.operation(),
                        input.semanticOperationId(),
                        input.payloadDigest(),
                        input.status(),
                        ZERO_HEX64,
                        input.recordedAtUnixMillis(),
                        input.body());
        return SettlementCanonicalJsonV1.encode(encodeResultObject(envelope, false));
    }

    private static RequestEnvelope decodeContextualRequest(
            JsonObject object, Operation operation) {
        requireExactKeys(
                object,
                "schema",
                "operation",
                "profileId",
                "runtimeGenerationId",
                "parentIntentId",
                "childStageId",
                "semanticOperationId",
                "payloadDigest",
                "role",
                "stage",
                "asset",
                "chainIdentity",
                "policyDigest",
                "issuedAtUnixMillis",
                "deadlineUnixMillis",
                "body");
        requireLiteral(requireString(object, "schema"), Schema.REQUEST.wireValue());
        String profileId = requireHex64(requireString(object, "profileId"));
        String generationId = requireHex64(requireString(object, "runtimeGenerationId"));
        String parentId = requireHex64(requireString(object, "parentIntentId"));
        String childId = requireHex64(requireString(object, "childStageId"));
        String semanticId = requireHex64(requireString(object, "semanticOperationId"));
        String payload = requireHex64(requireString(object, "payloadDigest"));
        String role = parseRole(requireString(object, "role")).wireValue();
        String stage = parseStage(requireString(object, "stage")).wireValue();
        Asset asset = parseAsset(requireString(object, "asset"));
        ChainIdentity chain = decodeChainIdentity(requireObject(object.get("chainIdentity")), asset);
        String policy = requireHex64(requireString(object, "policyDigest"));
        String issuedAt = requireU64(requireString(object, "issuedAtUnixMillis"));
        String deadline = requireU64(requireString(object, "deadlineUnixMillis"));
        requireDeadlineOrder(issuedAt, deadline);
        ContextualRequestBody body = decodeContextualBody(operation, requireObject(object.get("body")));
        return new ContextualRequestEnvelope(
                Schema.REQUEST,
                operation,
                profileId,
                generationId,
                parentId,
                childId,
                semanticId,
                payload,
                role,
                stage,
                asset,
                chain,
                policy,
                issuedAt,
                deadline,
                body);
    }

    private static RequestEnvelope decodeOwnedAddressRequest(JsonObject object) {
        requireExactKeys(
                object,
                "schema",
                "operation",
                "profileId",
                "runtimeGenerationId",
                "semanticOperationId",
                "payloadDigest",
                "asset",
                "chainIdentity",
                "policyDigest",
                "issuedAtUnixMillis",
                "deadlineUnixMillis",
                "body");
        requireLiteral(requireString(object, "schema"), Schema.REQUEST.wireValue());
        Asset asset = parseAsset(requireString(object, "asset"));
        String issuedAt = requireU64(requireString(object, "issuedAtUnixMillis"));
        String deadline = requireU64(requireString(object, "deadlineUnixMillis"));
        requireDeadlineOrder(issuedAt, deadline);
        return new OwnedAddressRequestEnvelope(
                Schema.REQUEST,
                Operation.CHECK_OWNED_ADDRESS,
                requireHex64(requireString(object, "profileId")),
                requireHex64(requireString(object, "runtimeGenerationId")),
                requireHex64(requireString(object, "semanticOperationId")),
                requireHex64(requireString(object, "payloadDigest")),
                asset,
                decodeChainIdentity(requireObject(object.get("chainIdentity")), asset),
                requireHex64(requireString(object, "policyDigest")),
                issuedAt,
                deadline,
                decodeCheckOwnedAddress(requireObject(object.get("body"))));
    }

    private static RequestEnvelope decodeLookupRequest(JsonObject object) {
        requireExactKeys(
                object,
                "schema",
                "operation",
                "profileId",
                "runtimeGenerationId",
                "parentIntentId",
                "childStageId",
                "semanticOperationId",
                "payloadDigest",
                "role",
                "stage",
                "asset",
                "chainIdentity",
                "policyDigest",
                "issuedAtUnixMillis",
                "deadlineUnixMillis",
                "body");
        requireLiteral(requireString(object, "schema"), Schema.REQUEST.wireValue());
        String parent = requireString(object, "parentIntentId");
        String child = requireString(object, "childStageId");
        String role = requireString(object, "role");
        String stage = requireString(object, "stage");
        boolean allEmpty = parent.isEmpty() && child.isEmpty() && role.isEmpty() && stage.isEmpty();
        boolean allPresent = !parent.isEmpty() && !child.isEmpty() && !role.isEmpty() && !stage.isEmpty();
        if (!allEmpty && !allPresent) {
            throw invalid();
        }
        if (allPresent) {
            requireHex64(parent);
            requireHex64(child);
            role = parseRole(role).wireValue();
            stage = parseStage(stage).wireValue();
        }
        String semanticId = requireHex64(requireString(object, "semanticOperationId"));
        String payload = requireHex64(requireString(object, "payloadDigest"));
        Asset asset = parseAsset(requireString(object, "asset"));
        String issuedAt = requireU64(requireString(object, "issuedAtUnixMillis"));
        String deadline = requireU64(requireString(object, "deadlineUnixMillis"));
        requireDeadlineOrder(issuedAt, deadline);
        GetSettlementOperationResultRequest body =
                decodeLookupBody(requireObject(object.get("body")));
        if (!semanticId.equals(body.targetSemanticOperationId())
                || !payload.equals(body.expectedPayloadDigest())) {
            throw invalid();
        }
        return new LookupRequestEnvelope(
                Schema.REQUEST,
                Operation.GET_SETTLEMENT_OPERATION_RESULT,
                requireHex64(requireString(object, "profileId")),
                requireHex64(requireString(object, "runtimeGenerationId")),
                parent,
                child,
                semanticId,
                payload,
                role,
                stage,
                asset,
                decodeChainIdentity(requireObject(object.get("chainIdentity")), asset),
                requireHex64(requireString(object, "policyDigest")),
                issuedAt,
                deadline,
                body);
    }

    private static ContextualRequestBody decodeContextualBody(
            Operation operation, JsonObject body) {
        return switch (operation) {
            case RESERVE_WALLET_INPUTS -> decodeReservation(body);
            case PROVE_RESERVED_UTXO_OWNERSHIP -> decodeProof(body);
            case SIGN_SETTLEMENT_STAGE -> decodeSign(body);
            case BROADCAST_SETTLEMENT_STAGE -> decodeBroadcast(body);
            case CHECK_OWNED_ADDRESS, GET_SETTLEMENT_OPERATION_RESULT -> throw invalid();
        };
    }

    private static ReserveWalletInputsRequest decodeReservation(JsonObject object) {
        requireExactKeys(
                object,
                "purpose",
                "walletAddress",
                "baseAmountBlockUnits",
                "outgoingAmountUnits",
                "fixedTakerFeeBlockUnits",
                "maxStageNativeFeeUnits",
                "maxGraphNativeFeeUnits",
                "recoveryReserveUnits",
                "outpoints");
        ReservationPurpose purpose = parseReservationPurpose(requireString(object, "purpose"));
        String base = requirePositiveU64(requireString(object, "baseAmountBlockUnits"));
        if (new BigInteger(base).compareTo(BigInteger.valueOf(1_000_000_000L)) > 0) {
            throw invalid();
        }
        String fixed = requireU64(requireString(object, "fixedTakerFeeBlockUnits"));
        if ((purpose == ReservationPurpose.SERVICE_FEE && !fixed.equals("1500000"))
                || (purpose == ReservationPurpose.DEPOSIT && !fixed.equals("0"))) {
            throw invalid();
        }
        List<PrevoutEvidence> outpoints = decodePrevouts(requireArray(object, "outpoints"), 1, 10);
        requireUniquePrevouts(outpoints);
        return new ReserveWalletInputsRequest(
                purpose,
                requireAddress(requireString(object, "walletAddress")),
                base,
                requirePositiveU64(requireString(object, "outgoingAmountUnits")),
                fixed,
                requirePositiveU64(requireString(object, "maxStageNativeFeeUnits")),
                requirePositiveU64(requireString(object, "maxGraphNativeFeeUnits")),
                requireU64(requireString(object, "recoveryReserveUnits")),
                outpoints);
    }

    private static ProveReservedUtxoOwnershipRequest decodeProof(JsonObject object) {
        requireExactKeys(
                object, "reservationId", "outpoint", "messageHex", "messageDigest", "purpose");
        return new ProveReservedUtxoOwnershipRequest(
                requireHex64(requireString(object, "reservationId")),
                decodeOutpoint(requireObject(object.get("outpoint"))),
                requireEvenHex(requireString(object, "messageHex")),
                requireHex64(requireString(object, "messageDigest")),
                parseProofPurpose(requireString(object, "purpose")));
    }

    private static CheckOwnedAddressRequest decodeCheckOwnedAddress(JsonObject object) {
        requireExactKeys(object, "address", "purpose");
        return new CheckOwnedAddressRequest(
                requireAddress(requireString(object, "address")),
                parseAddressProbePurpose(requireString(object, "purpose")));
    }

    private static SignSettlementStageRequest decodeSign(JsonObject object) {
        requireExactKeys(
                object,
                "reservationId",
                "unsignedTransactionHex",
                "unsignedBytesDigest",
                "unsignedTxid",
                "unsignedWtxid",
                "previousOutputs",
                "expectedOutputs",
                "changeAddress",
                "inputSequences",
                "lockTime",
                "sighashPolicy",
                "maxStageNativeFeeUnits",
                "contractPolicyDigest");
        List<PrevoutEvidence> previous =
                decodePrevouts(requireArray(object, "previousOutputs"), 1, 10);
        List<ExpectedOutput> expected = decodeExpectedOutputs(requireArray(object, "expectedOutputs"));
        List<String> sequences = decodeU32Array(requireArray(object, "inputSequences"), 1, 10);
        if (previous.size() != sequences.size()) {
            throw invalid();
        }
        return new SignSettlementStageRequest(
                requireHex64(requireString(object, "reservationId")),
                requireEvenHex(requireString(object, "unsignedTransactionHex")),
                requireHex64(requireString(object, "unsignedBytesDigest")),
                requireHex64(requireString(object, "unsignedTxid")),
                requireHex64(requireString(object, "unsignedWtxid")),
                previous,
                expected,
                requireAddress(requireString(object, "changeAddress")),
                sequences,
                requireU32(requireString(object, "lockTime")),
                parseSighashPolicy(requireString(object, "sighashPolicy")),
                requirePositiveU64(requireString(object, "maxStageNativeFeeUnits")),
                requireHex64(requireString(object, "contractPolicyDigest")));
    }

    private static BroadcastSettlementStageRequest decodeBroadcast(JsonObject object) {
        requireExactKeys(
                object,
                "finalTransactionHex",
                "finalBytesDigest",
                "finalTxid",
                "finalWtxid",
                "producer",
                "predecessorOperationId",
                "contractPolicyDigest",
                "recoveryBinding");
        JsonElement binding = object.get("recoveryBinding");
        RecoveryBinding recovery =
                binding.isJsonNull() ? null : decodeRecoveryBinding(requireObject(binding));
        return new BroadcastSettlementStageRequest(
                requireEvenHex(requireString(object, "finalTransactionHex")),
                requireHex64(requireString(object, "finalBytesDigest")),
                requireHex64(requireString(object, "finalTxid")),
                requireHex64(requireString(object, "finalWtxid")),
                parseProducer(requireString(object, "producer")),
                requireHex64(requireString(object, "predecessorOperationId")),
                requireHex64(requireString(object, "contractPolicyDigest")),
                recovery);
    }

    private static GetSettlementOperationResultRequest decodeLookupBody(JsonObject object) {
        requireExactKeys(object, "targetSemanticOperationId", "expectedPayloadDigest");
        return new GetSettlementOperationResultRequest(
                requireHex64(requireString(object, "targetSemanticOperationId")),
                requireHex64(requireString(object, "expectedPayloadDigest")));
    }

    private static ChainIdentity decodeChainIdentity(JsonObject object, Asset expectedAsset) {
        requireExactKeys(
                object,
                "asset",
                "network",
                "genesisHash",
                "networkMagicHex",
                "p2pkhVersion",
                "p2shVersions",
                "signedMessagePrefixHex",
                "baseUnitsPerCoin",
                "minimumConfirmations",
                "chainPolicyDigest");
        Asset asset = parseAsset(requireString(object, "asset"));
        if (asset != expectedAsset) {
            throw invalid();
        }
        String network = requireString(object, "network");
        requireLiteral(network, "mainnet");
        JsonArray versionsArray = requireArray(object, "p2shVersions");
        requireArraySize(versionsArray, 1, 8);
        List<String> versions = new ArrayList<>();
        int prior = -1;
        for (JsonElement element : versionsArray) {
            String value = requireU8(requireString(element));
            int numeric = Integer.parseInt(value);
            if (numeric <= prior) {
                throw invalid();
            }
            prior = numeric;
            versions.add(value);
        }
        return new ChainIdentity(
                asset,
                network,
                requireHex64(requireString(object, "genesisHash")),
                requireHex8(requireString(object, "networkMagicHex")),
                requireU8(requireString(object, "p2pkhVersion")),
                versions,
                requireEvenHex(requireString(object, "signedMessagePrefixHex")),
                requirePositiveU64(requireString(object, "baseUnitsPerCoin")),
                requirePositiveU32(requireString(object, "minimumConfirmations")),
                requireHex64(requireString(object, "chainPolicyDigest")));
    }

    private static List<PrevoutEvidence> decodePrevouts(
            JsonArray array, int minimum, int maximum) {
        requireArraySize(array, minimum, maximum);
        List<PrevoutEvidence> values = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = requireObject(element);
            requireExactKeys(
                    object,
                    "txid",
                    "vout",
                    "valueBaseUnits",
                    "scriptPubKeyHex",
                    "ownedAddress",
                    "confirmationBlockHash",
                    "confirmationHeight",
                    "observedConfirmations");
            values.add(
                    new PrevoutEvidence(
                            requireHex64(requireString(object, "txid")),
                            requireU32(requireString(object, "vout")),
                            requirePositiveU64(requireString(object, "valueBaseUnits")),
                            requireEvenHex(requireString(object, "scriptPubKeyHex")),
                            requireAddress(requireString(object, "ownedAddress")),
                            requireHex64(requireString(object, "confirmationBlockHash")),
                            requireU32(requireString(object, "confirmationHeight")),
                            requireU32(requireString(object, "observedConfirmations"))));
        }
        return List.copyOf(values);
    }

    private static OutpointIdentity decodeOutpoint(JsonObject object) {
        requireExactKeys(object, "txid", "vout");
        return new OutpointIdentity(
                requireHex64(requireString(object, "txid")),
                requireU32(requireString(object, "vout")));
    }

    private static List<ExpectedOutput> decodeExpectedOutputs(JsonArray array) {
        requireArraySize(array, 1, 64);
        List<ExpectedOutput> values = new ArrayList<>();
        BigInteger prior = BigInteger.valueOf(-1);
        for (JsonElement element : array) {
            JsonObject object = requireObject(element);
            requireExactKeys(object, "index", "valueBaseUnits", "scriptPubKeyHex");
            String index = requireU32(requireString(object, "index"));
            BigInteger numeric = new BigInteger(index);
            if (numeric.compareTo(prior) <= 0) {
                throw invalid();
            }
            prior = numeric;
            values.add(
                    new ExpectedOutput(
                            index,
                            requirePositiveU64(requireString(object, "valueBaseUnits")),
                            requireEvenHex(requireString(object, "scriptPubKeyHex"))));
        }
        return List.copyOf(values);
    }

    private static RecoveryBinding decodeRecoveryBinding(JsonObject object) {
        requireExactKeys(
                object,
                "recoveryRecordId",
                "fundingTxid",
                "refundTxid",
                "recoveryPayloadDigest",
                "persistedAtUnixMillis");
        return new RecoveryBinding(
                requireHex64(requireString(object, "recoveryRecordId")),
                requireHex64(requireString(object, "fundingTxid")),
                requireHex64(requireString(object, "refundTxid")),
                requireHex64(requireString(object, "recoveryPayloadDigest")),
                requireU64(requireString(object, "persistedAtUnixMillis")));
    }

    private static ResultBody decodeResultBody(
            Operation operation, Status status, JsonObject object) {
        if (status == Status.QUARANTINED && operation != Operation.BROADCAST_SETTLEMENT_STAGE) {
            requireExactKeys(object, "quarantineCode", "evidenceDigest");
            return new NonBroadcastQuarantinedResult(
                    parseNonBroadcastQuarantine(requireString(object, "quarantineCode")),
                    requireHex64(requireString(object, "evidenceDigest")));
        }
        return switch (operation) {
            case RESERVE_WALLET_INPUTS -> decodeReservationResult(status, object);
            case PROVE_RESERVED_UTXO_OWNERSHIP -> decodeProofResult(status, object);
            case CHECK_OWNED_ADDRESS -> decodeOwnedAddressResult(status, object);
            case SIGN_SETTLEMENT_STAGE -> decodeSignedResult(status, object);
            case BROADCAST_SETTLEMENT_STAGE -> decodeBroadcastResult(status, object);
            case GET_SETTLEMENT_OPERATION_RESULT -> throw invalid();
        };
    }

    private static ResultBody decodeReservationResult(Status status, JsonObject object) {
        if (status != Status.RESERVED) {
            throw invalid();
        }
        requireExactKeys(object, "reservationId", "reservationDigest", "outpoints", "deduplicated");
        if (requireBoolean(object, "deduplicated")) {
            throw invalid();
        }
        JsonArray array = requireArray(object, "outpoints");
        requireArraySize(array, 1, 10);
        List<OutpointIdentity> outpoints = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement element : array) {
            OutpointIdentity outpoint = decodeOutpoint(requireObject(element));
            if (!seen.add(outpoint.txid() + ':' + outpoint.vout())) {
                throw invalid();
            }
            outpoints.add(outpoint);
        }
        return new ReservationResult(
                requireHex64(requireString(object, "reservationId")),
                requireHex64(requireString(object, "reservationDigest")),
                outpoints,
                false);
    }

    private static ResultBody decodeProofResult(Status status, JsonObject object) {
        if (status != Status.PROOF_CREATED) {
            throw invalid();
        }
        requireExactKeys(object, "messageDigest", "compactProofBase64", "proofDigest");
        return new OwnershipProofResult(
                requireHex64(requireString(object, "messageDigest")),
                requireCanonicalBase64(requireString(object, "compactProofBase64")),
                requireHex64(requireString(object, "proofDigest")));
    }

    private static ResultBody decodeOwnedAddressResult(Status status, JsonObject object) {
        if (status != Status.OWNERSHIP_CHECKED) {
            throw invalid();
        }
        requireExactKeys(object, "owned");
        return new OwnedAddressResult(requireBoolean(object, "owned"));
    }

    private static ResultBody decodeSignedResult(Status status, JsonObject object) {
        if (status != Status.SIGNED) {
            throw invalid();
        }
        requireExactKeys(
                object,
                "finalTransactionHex",
                "finalBytesDigest",
                "finalTxid",
                "finalWtxid",
                "signedInputIndexes");
        List<String> indexes =
                decodeAscendingUniqueU32Array(requireArray(object, "signedInputIndexes"), 1, 10);
        return new SignedStageResult(
                requireEvenHex(requireString(object, "finalTransactionHex")),
                requireHex64(requireString(object, "finalBytesDigest")),
                requireHex64(requireString(object, "finalTxid")),
                requireHex64(requireString(object, "finalWtxid")),
                indexes);
    }

    private static ResultBody decodeBroadcastResult(Status status, JsonObject object) {
        return switch (status) {
            case BROADCAST_DISPATCH_RECORDED -> {
                requireExactKeys(
                        object,
                        "finalBytesDigest",
                        "finalTxid",
                        "finalWtxid",
                        "dispatchRecordDigest");
                yield new BroadcastDispatchRecordedResult(
                        requireHex64(requireString(object, "finalBytesDigest")),
                        requireHex64(requireString(object, "finalTxid")),
                        requireHex64(requireString(object, "finalWtxid")),
                        requireHex64(requireString(object, "dispatchRecordDigest")));
            }
            case BROADCAST_SUCCEEDED -> {
                requireExactKeys(
                        object, "finalBytesDigest", "finalTxid", "finalWtxid", "returnedTxid");
                yield new BroadcastSucceededResult(
                        requireHex64(requireString(object, "finalBytesDigest")),
                        requireHex64(requireString(object, "finalTxid")),
                        requireHex64(requireString(object, "finalWtxid")),
                        requireHex64(requireString(object, "returnedTxid")));
            }
            case BROADCAST_REJECTED -> {
                requireExactKeys(
                        object,
                        "finalBytesDigest",
                        "finalTxid",
                        "finalWtxid",
                        "rejectionCode",
                        "evidenceDigest");
                yield new BroadcastRejectedResult(
                        requireHex64(requireString(object, "finalBytesDigest")),
                        requireHex64(requireString(object, "finalTxid")),
                        requireHex64(requireString(object, "finalWtxid")),
                        parseBroadcastRejection(requireString(object, "rejectionCode")),
                        requireHex64(requireString(object, "evidenceDigest")));
            }
            case QUARANTINED -> {
                requireExactKeys(
                        object,
                        "finalBytesDigest",
                        "finalTxid",
                        "finalWtxid",
                        "quarantineCode",
                        "evidenceDigest");
                yield new BroadcastQuarantinedResult(
                        requireHex64(requireString(object, "finalBytesDigest")),
                        requireHex64(requireString(object, "finalTxid")),
                        requireHex64(requireString(object, "finalWtxid")),
                        parseBroadcastQuarantine(requireString(object, "quarantineCode")),
                        requireHex64(requireString(object, "evidenceDigest")));
            }
            default -> throw invalid();
        };
    }

    private static JsonObject encodeResultObject(ResultEnvelope envelope, boolean includeDigest) {
        if (envelope.schema() != Schema.RESULT
                || envelope.operation() == Operation.GET_SETTLEMENT_OPERATION_RESULT) {
            throw invalid();
        }
        requireHex64(envelope.semanticOperationId());
        requireHex64(envelope.payloadDigest());
        requireHex64(envelope.resultDigest());
        requireU64(envelope.recordedAtUnixMillis());
        JsonObject object = new JsonObject();
        object.addProperty("schema", envelope.schema().wireValue());
        object.addProperty("operation", envelope.operation().wireValue());
        object.addProperty("semanticOperationId", envelope.semanticOperationId());
        object.addProperty("payloadDigest", envelope.payloadDigest());
        object.addProperty("status", envelope.status().wireValue());
        if (includeDigest) {
            object.addProperty("resultDigest", envelope.resultDigest());
        }
        object.addProperty("recordedAtUnixMillis", envelope.recordedAtUnixMillis());
        object.add("body", encodeResultBody(envelope.operation(), envelope.status(), envelope.body()));
        return object;
    }

    private static JsonObject encodeResultBody(
            Operation operation, Status status, ResultBody body) {
        JsonObject object = new JsonObject();
        if (body instanceof ReservationResult value) {
            requirePair(operation, status, Operation.RESERVE_WALLET_INPUTS, Status.RESERVED);
            object.addProperty("reservationId", requireHex64(value.reservationId()));
            object.addProperty("reservationDigest", requireHex64(value.reservationDigest()));
            if (value.deduplicated() || value.outpoints().size() < 1 || value.outpoints().size() > 10) {
                throw invalid();
            }
            JsonArray array = new JsonArray();
            Set<String> seen = new HashSet<>();
            for (OutpointIdentity outpoint : value.outpoints()) {
                JsonObject item = encodeOutpoint(outpoint);
                if (!seen.add(outpoint.txid() + ':' + outpoint.vout())) {
                    throw invalid();
                }
                array.add(item);
            }
            object.add("outpoints", array);
            object.addProperty("deduplicated", false);
        } else if (body instanceof OwnershipProofResult value) {
            requirePair(
                    operation,
                    status,
                    Operation.PROVE_RESERVED_UTXO_OWNERSHIP,
                    Status.PROOF_CREATED);
            object.addProperty("messageDigest", requireHex64(value.messageDigest()));
            object.addProperty(
                    "compactProofBase64", requireCanonicalBase64(value.compactProofBase64()));
            object.addProperty("proofDigest", requireHex64(value.proofDigest()));
        } else if (body instanceof OwnedAddressResult value) {
            requirePair(
                    operation, status, Operation.CHECK_OWNED_ADDRESS, Status.OWNERSHIP_CHECKED);
            object.addProperty("owned", value.owned());
        } else if (body instanceof SignedStageResult value) {
            requirePair(operation, status, Operation.SIGN_SETTLEMENT_STAGE, Status.SIGNED);
            object.addProperty(
                    "finalTransactionHex", requireEvenHex(value.finalTransactionHex()));
            object.addProperty("finalBytesDigest", requireHex64(value.finalBytesDigest()));
            object.addProperty("finalTxid", requireHex64(value.finalTxid()));
            object.addProperty("finalWtxid", requireHex64(value.finalWtxid()));
            object.add(
                    "signedInputIndexes",
                    encodeAscendingUniqueU32(value.signedInputIndexes(), 1, 10));
        } else if (body instanceof NonBroadcastQuarantinedResult value) {
            if (operation == Operation.BROADCAST_SETTLEMENT_STAGE
                    || status != Status.QUARANTINED
                    || value.quarantineCode()
                            != QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED) {
                throw invalid();
            }
            object.addProperty("quarantineCode", value.quarantineCode().wireValue());
            object.addProperty("evidenceDigest", requireHex64(value.evidenceDigest()));
        } else if (body instanceof BroadcastDispatchRecordedResult value) {
            requirePair(
                    operation,
                    status,
                    Operation.BROADCAST_SETTLEMENT_STAGE,
                    Status.BROADCAST_DISPATCH_RECORDED);
            addBroadcastCommon(object, value.finalBytesDigest(), value.finalTxid(), value.finalWtxid());
            object.addProperty("dispatchRecordDigest", requireHex64(value.dispatchRecordDigest()));
        } else if (body instanceof BroadcastSucceededResult value) {
            requirePair(
                    operation,
                    status,
                    Operation.BROADCAST_SETTLEMENT_STAGE,
                    Status.BROADCAST_SUCCEEDED);
            addBroadcastCommon(object, value.finalBytesDigest(), value.finalTxid(), value.finalWtxid());
            String returned = requireHex64(value.returnedTxid());
            if (!returned.equals(value.finalTxid())) {
                throw invalid();
            }
            object.addProperty("returnedTxid", returned);
        } else if (body instanceof BroadcastRejectedResult value) {
            requirePair(
                    operation,
                    status,
                    Operation.BROADCAST_SETTLEMENT_STAGE,
                    Status.BROADCAST_REJECTED);
            addBroadcastCommon(object, value.finalBytesDigest(), value.finalTxid(), value.finalWtxid());
            object.addProperty("rejectionCode", value.rejectionCode().wireValue());
            object.addProperty("evidenceDigest", requireHex64(value.evidenceDigest()));
        } else if (body instanceof BroadcastQuarantinedResult value) {
            requirePair(
                    operation,
                    status,
                    Operation.BROADCAST_SETTLEMENT_STAGE,
                    Status.QUARANTINED);
            addBroadcastCommon(object, value.finalBytesDigest(), value.finalTxid(), value.finalWtxid());
            object.addProperty("quarantineCode", value.quarantineCode().wireValue());
            object.addProperty("evidenceDigest", requireHex64(value.evidenceDigest()));
        } else {
            throw invalid();
        }
        return object;
    }

    private static void addBroadcastCommon(
            JsonObject object, String digest, String txid, String wtxid) {
        object.addProperty("finalBytesDigest", requireHex64(digest));
        object.addProperty("finalTxid", requireHex64(txid));
        object.addProperty("finalWtxid", requireHex64(wtxid));
    }

    private static JsonObject encodeOutpoint(OutpointIdentity value) {
        JsonObject object = new JsonObject();
        object.addProperty("txid", requireHex64(value.txid()));
        object.addProperty("vout", requireU32(value.vout()));
        return object;
    }

    private static void requireRequestIdentity(RequestEnvelope envelope) {
        if (envelope instanceof LookupRequestEnvelope lookup) {
            if (!lookup.semanticOperationId().equals(lookup.body().targetSemanticOperationId())
                    || !lookup.payloadDigest().equals(lookup.body().expectedPayloadDigest())) {
                throw invalid();
            }
            return;
        }
        Operation operation = requestOperation(envelope);
        String profile;
        String parent;
        String child;
        String role;
        String stage;
        Asset asset;
        ChainIdentity chain;
        String policy;
        String deadline;
        RequestBody body;
        String suppliedSemantic;
        String suppliedPayload;
        if (envelope instanceof ContextualRequestEnvelope contextual) {
            profile = contextual.profileId();
            parent = contextual.parentIntentId();
            child = contextual.childStageId();
            role = contextual.role();
            stage = contextual.stage();
            asset = contextual.asset();
            chain = contextual.chainIdentity();
            policy = contextual.policyDigest();
            deadline = contextual.deadlineUnixMillis();
            body = contextual.body();
            suppliedSemantic = contextual.semanticOperationId();
            suppliedPayload = contextual.payloadDigest();
        } else if (envelope instanceof OwnedAddressRequestEnvelope owned) {
            profile = owned.profileId();
            parent = "";
            child = "";
            role = "";
            stage = "";
            asset = owned.asset();
            chain = owned.chainIdentity();
            policy = owned.policyDigest();
            deadline = owned.deadlineUnixMillis();
            body = owned.body();
            suppliedSemantic = owned.semanticOperationId();
            suppliedPayload = owned.payloadDigest();
        } else {
            throw invalid();
        }
        JsonObject chainJson = encodeChainIdentity(chain);
        JsonObject bodyJson = encodeRequestBody(body);
        String operationObjectId = operationObjectId(operation, child, asset, body);
        String semantic =
                SettlementIdentityV1.semanticOperationId(
                        new IdentityInputs(
                                operation,
                                profile,
                                parent,
                                child,
                                role,
                                stage,
                                asset,
                                chain.chainPolicyDigest(),
                                policy,
                                operationObjectId));
        String payload =
                SettlementIdentityV1.payloadDigest(
                        new PayloadInputs(
                                operation,
                                profile,
                                parent,
                                child,
                                role,
                                stage,
                                asset,
                                sha256(SettlementCanonicalJsonV1.encode(chainJson)),
                                chain.chainPolicyDigest(),
                                policy,
                                deadline,
                                operationObjectId,
                                sha256(SettlementCanonicalJsonV1.encode(bodyJson))));
        if (!semantic.equals(suppliedSemantic) || !payload.equals(suppliedPayload)) {
            throw invalid();
        }
    }

    private static String operationObjectId(
            Operation operation, String child, Asset asset, RequestBody body) {
        return switch (operation) {
            case RESERVE_WALLET_INPUTS,
                    SIGN_SETTLEMENT_STAGE,
                    BROADCAST_SETTLEMENT_STAGE -> child;
            case PROVE_RESERVED_UTXO_OWNERSHIP -> {
                ProveReservedUtxoOwnershipRequest proof =
                        (ProveReservedUtxoOwnershipRequest) body;
                yield sha256(SettlementCanonicalJsonV1.encode(encodeOutpoint(proof.outpoint())));
            }
            case CHECK_OWNED_ADDRESS -> {
                CheckOwnedAddressRequest owned = (CheckOwnedAddressRequest) body;
                JsonObject identity = new JsonObject();
                identity.addProperty("address", owned.address());
                identity.addProperty("asset", asset.wireValue());
                yield sha256(SettlementCanonicalJsonV1.encode(identity));
            }
            case GET_SETTLEMENT_OPERATION_RESULT -> throw invalid();
        };
    }

    private static RequestEnvelope revalidateRequest(RequestEnvelope request) {
        byte[] encoded = SettlementCanonicalJsonV1.encode(encodeRequestObject(request));
        RequestEnvelope decoded = decodeRequest(encoded);
        if (!decoded.equals(request)) {
            throw invalid();
        }
        return decoded;
    }

    private static JsonObject encodeRequestObject(RequestEnvelope envelope) {
        if (envelope == null) {
            throw invalid();
        }
        JsonObject object = new JsonObject();
        object.addProperty("schema", Schema.REQUEST.wireValue());
        object.addProperty("operation", requestOperation(envelope).wireValue());
        if (envelope instanceof ContextualRequestEnvelope value) {
            object.addProperty("profileId", value.profileId());
            object.addProperty("runtimeGenerationId", value.runtimeGenerationId());
            object.addProperty("parentIntentId", value.parentIntentId());
            object.addProperty("childStageId", value.childStageId());
            object.addProperty("semanticOperationId", value.semanticOperationId());
            object.addProperty("payloadDigest", value.payloadDigest());
            object.addProperty("role", value.role());
            object.addProperty("stage", value.stage());
            object.addProperty("asset", value.asset().wireValue());
            object.add("chainIdentity", encodeChainIdentity(value.chainIdentity()));
            object.addProperty("policyDigest", value.policyDigest());
            object.addProperty("issuedAtUnixMillis", value.issuedAtUnixMillis());
            object.addProperty("deadlineUnixMillis", value.deadlineUnixMillis());
            object.add("body", encodeRequestBody(value.body()));
        } else if (envelope instanceof OwnedAddressRequestEnvelope value) {
            object.addProperty("profileId", value.profileId());
            object.addProperty("runtimeGenerationId", value.runtimeGenerationId());
            object.addProperty("semanticOperationId", value.semanticOperationId());
            object.addProperty("payloadDigest", value.payloadDigest());
            object.addProperty("asset", value.asset().wireValue());
            object.add("chainIdentity", encodeChainIdentity(value.chainIdentity()));
            object.addProperty("policyDigest", value.policyDigest());
            object.addProperty("issuedAtUnixMillis", value.issuedAtUnixMillis());
            object.addProperty("deadlineUnixMillis", value.deadlineUnixMillis());
            object.add("body", encodeRequestBody(value.body()));
        } else if (envelope instanceof LookupRequestEnvelope value) {
            object.addProperty("profileId", value.profileId());
            object.addProperty("runtimeGenerationId", value.runtimeGenerationId());
            object.addProperty("parentIntentId", value.parentIntentId());
            object.addProperty("childStageId", value.childStageId());
            object.addProperty("semanticOperationId", value.semanticOperationId());
            object.addProperty("payloadDigest", value.payloadDigest());
            object.addProperty("role", value.role());
            object.addProperty("stage", value.stage());
            object.addProperty("asset", value.asset().wireValue());
            object.add("chainIdentity", encodeChainIdentity(value.chainIdentity()));
            object.addProperty("policyDigest", value.policyDigest());
            object.addProperty("issuedAtUnixMillis", value.issuedAtUnixMillis());
            object.addProperty("deadlineUnixMillis", value.deadlineUnixMillis());
            object.add("body", encodeRequestBody(value.body()));
        } else {
            throw invalid();
        }
        return object;
    }

    private static JsonObject encodeChainIdentity(ChainIdentity value) {
        JsonObject object = new JsonObject();
        object.addProperty("asset", value.asset().wireValue());
        object.addProperty("network", value.network());
        object.addProperty("genesisHash", value.genesisHash());
        object.addProperty("networkMagicHex", value.networkMagicHex());
        object.addProperty("p2pkhVersion", value.p2pkhVersion());
        JsonArray versions = new JsonArray();
        value.p2shVersions().forEach(versions::add);
        object.add("p2shVersions", versions);
        object.addProperty("signedMessagePrefixHex", value.signedMessagePrefixHex());
        object.addProperty("baseUnitsPerCoin", value.baseUnitsPerCoin());
        object.addProperty("minimumConfirmations", value.minimumConfirmations());
        object.addProperty("chainPolicyDigest", value.chainPolicyDigest());
        return object;
    }

    private static JsonObject encodeRequestBody(RequestBody body) {
        JsonObject object = new JsonObject();
        if (body instanceof ReserveWalletInputsRequest value) {
            object.addProperty("purpose", value.purpose().wireValue());
            object.addProperty("walletAddress", value.walletAddress());
            object.addProperty("baseAmountBlockUnits", value.baseAmountBlockUnits());
            object.addProperty("outgoingAmountUnits", value.outgoingAmountUnits());
            object.addProperty("fixedTakerFeeBlockUnits", value.fixedTakerFeeBlockUnits());
            object.addProperty("maxStageNativeFeeUnits", value.maxStageNativeFeeUnits());
            object.addProperty("maxGraphNativeFeeUnits", value.maxGraphNativeFeeUnits());
            object.addProperty("recoveryReserveUnits", value.recoveryReserveUnits());
            JsonArray array = new JsonArray();
            value.outpoints().forEach(item -> array.add(encodePrevout(item)));
            object.add("outpoints", array);
        } else if (body instanceof ProveReservedUtxoOwnershipRequest value) {
            object.addProperty("reservationId", value.reservationId());
            object.add("outpoint", encodeOutpoint(value.outpoint()));
            object.addProperty("messageHex", value.messageHex());
            object.addProperty("messageDigest", value.messageDigest());
            object.addProperty("purpose", value.purpose().wireValue());
        } else if (body instanceof CheckOwnedAddressRequest value) {
            object.addProperty("address", value.address());
            object.addProperty("purpose", value.purpose().wireValue());
        } else if (body instanceof SignSettlementStageRequest value) {
            object.addProperty("reservationId", value.reservationId());
            object.addProperty("unsignedTransactionHex", value.unsignedTransactionHex());
            object.addProperty("unsignedBytesDigest", value.unsignedBytesDigest());
            object.addProperty("unsignedTxid", value.unsignedTxid());
            object.addProperty("unsignedWtxid", value.unsignedWtxid());
            JsonArray previous = new JsonArray();
            value.previousOutputs().forEach(item -> previous.add(encodePrevout(item)));
            object.add("previousOutputs", previous);
            JsonArray expected = new JsonArray();
            value.expectedOutputs().forEach(item -> expected.add(encodeExpectedOutput(item)));
            object.add("expectedOutputs", expected);
            object.addProperty("changeAddress", value.changeAddress());
            JsonArray sequences = new JsonArray();
            value.inputSequences().forEach(sequences::add);
            object.add("inputSequences", sequences);
            object.addProperty("lockTime", value.lockTime());
            object.addProperty("sighashPolicy", value.sighashPolicy().wireValue());
            object.addProperty("maxStageNativeFeeUnits", value.maxStageNativeFeeUnits());
            object.addProperty("contractPolicyDigest", value.contractPolicyDigest());
        } else if (body instanceof BroadcastSettlementStageRequest value) {
            object.addProperty("finalTransactionHex", value.finalTransactionHex());
            object.addProperty("finalBytesDigest", value.finalBytesDigest());
            object.addProperty("finalTxid", value.finalTxid());
            object.addProperty("finalWtxid", value.finalWtxid());
            object.addProperty("producer", value.producer().wireValue());
            object.addProperty("predecessorOperationId", value.predecessorOperationId());
            object.addProperty("contractPolicyDigest", value.contractPolicyDigest());
            object.add(
                    "recoveryBinding",
                    value.recoveryBinding() == null
                            ? JsonNull.INSTANCE
                            : encodeRecoveryBinding(value.recoveryBinding()));
        } else if (body instanceof GetSettlementOperationResultRequest value) {
            object.addProperty("targetSemanticOperationId", value.targetSemanticOperationId());
            object.addProperty("expectedPayloadDigest", value.expectedPayloadDigest());
        } else {
            throw invalid();
        }
        return object;
    }

    private static JsonObject encodePrevout(PrevoutEvidence value) {
        JsonObject object = new JsonObject();
        object.addProperty("txid", value.txid());
        object.addProperty("vout", value.vout());
        object.addProperty("valueBaseUnits", value.valueBaseUnits());
        object.addProperty("scriptPubKeyHex", value.scriptPubKeyHex());
        object.addProperty("ownedAddress", value.ownedAddress());
        object.addProperty("confirmationBlockHash", value.confirmationBlockHash());
        object.addProperty("confirmationHeight", value.confirmationHeight());
        object.addProperty("observedConfirmations", value.observedConfirmations());
        return object;
    }

    private static JsonObject encodeExpectedOutput(ExpectedOutput value) {
        JsonObject object = new JsonObject();
        object.addProperty("index", value.index());
        object.addProperty("valueBaseUnits", value.valueBaseUnits());
        object.addProperty("scriptPubKeyHex", value.scriptPubKeyHex());
        return object;
    }

    private static JsonObject encodeRecoveryBinding(RecoveryBinding value) {
        JsonObject object = new JsonObject();
        object.addProperty("recoveryRecordId", value.recoveryRecordId());
        object.addProperty("fundingTxid", value.fundingTxid());
        object.addProperty("refundTxid", value.refundTxid());
        object.addProperty("recoveryPayloadDigest", value.recoveryPayloadDigest());
        object.addProperty("persistedAtUnixMillis", value.persistedAtUnixMillis());
        return object;
    }

    private static String expectedSemanticOperationId(RequestEnvelope request) {
        if (request instanceof ContextualRequestEnvelope value) {
            return value.semanticOperationId();
        }
        if (request instanceof OwnedAddressRequestEnvelope value) {
            return value.semanticOperationId();
        }
        if (request instanceof LookupRequestEnvelope value) {
            return value.body().targetSemanticOperationId();
        }
        throw invalid();
    }

    private static Operation requestOperation(RequestEnvelope request) {
        if (request instanceof ContextualRequestEnvelope value) {
            return value.operation();
        }
        if (request instanceof OwnedAddressRequestEnvelope value) {
            return value.operation();
        }
        if (request instanceof LookupRequestEnvelope value) {
            return value.operation();
        }
        throw invalid();
    }

    private static List<String> decodeU32Array(JsonArray array, int minimum, int maximum) {
        requireArraySize(array, minimum, maximum);
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) {
            result.add(requireU32(requireString(element)));
        }
        return List.copyOf(result);
    }

    private static List<String> decodeAscendingUniqueU32Array(
            JsonArray array, int minimum, int maximum) {
        List<String> result = decodeU32Array(array, minimum, maximum);
        BigInteger previous = BigInteger.valueOf(-1);
        for (String value : result) {
            BigInteger current = new BigInteger(value);
            if (current.compareTo(previous) <= 0) {
                throw invalid();
            }
            previous = current;
        }
        return result;
    }

    private static JsonArray encodeAscendingUniqueU32(
            List<String> values, int minimum, int maximum) {
        if (values == null || values.size() < minimum || values.size() > maximum) {
            throw invalid();
        }
        JsonArray result = new JsonArray();
        BigInteger previous = BigInteger.valueOf(-1);
        for (String value : values) {
            String valid = requireU32(value);
            BigInteger current = new BigInteger(valid);
            if (current.compareTo(previous) <= 0) {
                throw invalid();
            }
            previous = current;
            result.add(valid);
        }
        return result;
    }

    private static void requireUniquePrevouts(List<PrevoutEvidence> values) {
        Set<String> seen = new HashSet<>();
        for (PrevoutEvidence value : values) {
            if (!seen.add(value.txid() + ':' + value.vout())) {
                throw invalid();
            }
        }
    }

    private static void requirePair(
            Operation operation,
            Status status,
            Operation expectedOperation,
            Status expectedStatus) {
        if (operation != expectedOperation || status != expectedStatus) {
            throw invalid();
        }
    }

    private static void requireDeadlineOrder(String issuedAt, String deadline) {
        if (new BigInteger(issuedAt).compareTo(new BigInteger(deadline)) > 0) {
            throw invalid();
        }
    }

    private static void requireExactKeys(JsonObject object, String... keys) {
        if (!object.keySet().equals(Set.of(keys))) {
            throw invalid();
        }
    }

    private static JsonObject requireObject(JsonElement value) {
        if (value == null || !value.isJsonObject()) {
            throw invalid();
        }
        return value.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) {
            throw invalid();
        }
        return value.getAsJsonArray();
    }

    private static String requireString(JsonObject object, String key) {
        return requireString(object.get(key));
    }

    private static String requireString(JsonElement value) {
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw invalid();
        }
        return value.getAsString();
    }

    private static boolean requireBoolean(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw invalid();
        }
        return value.getAsBoolean();
    }

    private static void requireLiteral(String value, String expected) {
        if (!expected.equals(value)) {
            throw invalid();
        }
    }

    private static String requireHex8(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}")) {
            throw invalid();
        }
        return value;
    }

    private static String requireHex64(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw invalid();
        }
        return value;
    }

    private static String requireEvenHex(String value) {
        if (value == null || value.isEmpty() || (value.length() & 1) != 0
                || !value.matches("[0-9a-f]+")) {
            throw invalid();
        }
        return value;
    }

    private static String requireCanonicalBase64(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (!Base64.getEncoder().encodeToString(decoded).equals(value)) {
                throw invalid();
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static String requireAddress(String value) {
        if (value == null || value.isEmpty() || value.length() > 128) {
            throw invalid();
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x21 || character > 0x7e || Character.isWhitespace(character)) {
                throw invalid();
            }
        }
        return value;
    }

    private static String requireU8(String value) {
        return requireUnsigned(value, U8_MAX, false);
    }

    private static String requireU32(String value) {
        return requireUnsigned(value, U32_MAX, false);
    }

    private static String requireU64(String value) {
        return requireUnsigned(value, U64_MAX, false);
    }

    private static String requirePositiveU32(String value) {
        return requireUnsigned(value, U32_MAX, true);
    }

    private static String requirePositiveU64(String value) {
        return requireUnsigned(value, U64_MAX, true);
    }

    private static String requireUnsigned(String value, BigInteger maximum, boolean positive) {
        if (value == null || !value.matches("0|[1-9][0-9]*")) {
            throw invalid();
        }
        BigInteger parsed;
        try {
            parsed = new BigInteger(value);
        } catch (NumberFormatException exception) {
            throw invalid();
        }
        if (parsed.compareTo(maximum) > 0 || (positive && parsed.signum() == 0)) {
            throw invalid();
        }
        return value;
    }

    private static void requireArraySize(JsonArray array, int minimum, int maximum) {
        if (array.size() < minimum || array.size() > maximum) {
            throw invalid();
        }
    }

    private static Operation parseOperation(String value) {
        return switch (value) {
            case "reserveWalletInputs" -> Operation.RESERVE_WALLET_INPUTS;
            case "proveReservedUtxoOwnership" -> Operation.PROVE_RESERVED_UTXO_OWNERSHIP;
            case "checkOwnedAddress" -> Operation.CHECK_OWNED_ADDRESS;
            case "signSettlementStage" -> Operation.SIGN_SETTLEMENT_STAGE;
            case "broadcastSettlementStage" -> Operation.BROADCAST_SETTLEMENT_STAGE;
            case "getSettlementOperationResult" -> Operation.GET_SETTLEMENT_OPERATION_RESULT;
            default -> throw invalid();
        };
    }

    private static Status parseStatus(String value) {
        return switch (value) {
            case "reserved" -> Status.RESERVED;
            case "proof-created" -> Status.PROOF_CREATED;
            case "ownership-checked" -> Status.OWNERSHIP_CHECKED;
            case "signed" -> Status.SIGNED;
            case "broadcast-dispatch-recorded" -> Status.BROADCAST_DISPATCH_RECORDED;
            case "broadcast-succeeded" -> Status.BROADCAST_SUCCEEDED;
            case "broadcast-rejected" -> Status.BROADCAST_REJECTED;
            case "quarantined" -> Status.QUARANTINED;
            default -> throw invalid();
        };
    }

    private static ErrorCode parseErrorCode(String value) {
        try {
            return ErrorCode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static Role parseRole(String value) {
        return switch (value) {
            case "maker" -> Role.MAKER;
            case "taker" -> Role.TAKER;
            default -> throw invalid();
        };
    }

    private static Stage parseStage(String value) {
        return switch (value) {
            case "service-fee" -> Stage.SERVICE_FEE;
            case "maker-deposit" -> Stage.MAKER_DEPOSIT;
            case "taker-deposit" -> Stage.TAKER_DEPOSIT;
            case "maker-redeem" -> Stage.MAKER_REDEEM;
            case "taker-redeem" -> Stage.TAKER_REDEEM;
            case "maker-refund" -> Stage.MAKER_REFUND;
            case "taker-refund" -> Stage.TAKER_REFUND;
            default -> throw invalid();
        };
    }

    private static Asset parseAsset(String value) {
        return switch (value) {
            case "BLOCK" -> Asset.BLOCK;
            case "LTC" -> Asset.LTC;
            default -> throw invalid();
        };
    }

    private static ReservationPurpose parseReservationPurpose(String value) {
        return switch (value) {
            case "service-fee" -> ReservationPurpose.SERVICE_FEE;
            case "deposit" -> ReservationPurpose.DEPOSIT;
            default -> throw invalid();
        };
    }

    private static ProofPurpose parseProofPurpose(String value) {
        if (!ProofPurpose.XBRIDGE_RESERVED_UTXO_PROOF_V1.wireValue().equals(value)) {
            throw invalid();
        }
        return ProofPurpose.XBRIDGE_RESERVED_UTXO_PROOF_V1;
    }

    private static AddressProbePurpose parseAddressProbePurpose(String value) {
        if (!AddressProbePurpose.XBRIDGE_OWNED_ADDRESS_PROBE_V1.wireValue().equals(value)) {
            throw invalid();
        }
        return AddressProbePurpose.XBRIDGE_OWNED_ADDRESS_PROBE_V1;
    }

    private static SighashPolicy parseSighashPolicy(String value) {
        if (!SighashPolicy.ALL.wireValue().equals(value)) {
            throw invalid();
        }
        return SighashPolicy.ALL;
    }

    private static Producer parseProducer(String value) {
        return switch (value) {
            case "xlite-wallet-input-signer-v1" -> Producer.XLITE_WALLET_INPUT_SIGNER_V1;
            case "admitted-core-protocol-signer-v1" ->
                    Producer.ADMITTED_CORE_PROTOCOL_SIGNER_V1;
            default -> throw invalid();
        };
    }

    private static BroadcastRejectionCode parseBroadcastRejection(String value) {
        if (!BroadcastRejectionCode.BACKEND_DEFINITIVE_REJECTION.wireValue().equals(value)) {
            throw invalid();
        }
        return BroadcastRejectionCode.BACKEND_DEFINITIVE_REJECTION;
    }

    private static QuarantineCode parseNonBroadcastQuarantine(String value) {
        if (!QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED.wireValue().equals(value)) {
            throw invalid();
        }
        return QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED;
    }

    private static QuarantineCode parseBroadcastQuarantine(String value) {
        return switch (value) {
            case "BROADCAST_IDENTITY_MISMATCH" -> QuarantineCode.BROADCAST_IDENTITY_MISMATCH;
            case "AMBIGUOUS_OUTCOME_QUARANTINED" ->
                    QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED;
            default -> throw invalid();
        };
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(INVALID);
    }
}
