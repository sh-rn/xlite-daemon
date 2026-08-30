package io.cloudchains.app.net.settlement;

import java.util.List;
import java.util.Objects;

final class SettlementContractV1 {
    private SettlementContractV1() {}

    enum Operation {
        RESERVE_WALLET_INPUTS("reserveWalletInputs"),
        PROVE_RESERVED_UTXO_OWNERSHIP("proveReservedUtxoOwnership"),
        CHECK_OWNED_ADDRESS("checkOwnedAddress"),
        SIGN_SETTLEMENT_STAGE("signSettlementStage"),
        BROADCAST_SETTLEMENT_STAGE("broadcastSettlementStage"),
        GET_SETTLEMENT_OPERATION_RESULT("getSettlementOperationResult");

        private final String wireValue;

        Operation(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    enum Schema {
        REQUEST("blocknet.xlite.settlement.request.v1"),
        RESULT("blocknet.xlite.settlement.result.v1"),
        ERROR("blocknet.xlite.settlement.error.v1");

        private final String wireValue;

        Schema(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    enum Status {
        RESERVED("reserved"),
        PROOF_CREATED("proof-created"),
        OWNERSHIP_CHECKED("ownership-checked"),
        SIGNED("signed"),
        BROADCAST_DISPATCH_RECORDED("broadcast-dispatch-recorded"),
        BROADCAST_SUCCEEDED("broadcast-succeeded"),
        BROADCAST_REJECTED("broadcast-rejected"),
        QUARANTINED("quarantined");

        private final String wireValue;

        Status(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    enum ErrorCode {
        INVALID_REQUEST("INVALID_REQUEST"),
        UNAUTHENTICATED("UNAUTHENTICATED"),
        CAPABILITY_UNAVAILABLE("CAPABILITY_UNAVAILABLE"),
        STALE_GENERATION("STALE_GENERATION"),
        DEADLINE_EXPIRED("DEADLINE_EXPIRED"),
        CHAIN_IDENTITY_MISMATCH("CHAIN_IDENTITY_MISMATCH"),
        POLICY_IDENTITY_MISMATCH("POLICY_IDENTITY_MISMATCH"),
        INTENT_BINDING_MISMATCH("INTENT_BINDING_MISMATCH"),
        OPERATION_PAYLOAD_CONFLICT("OPERATION_PAYLOAD_CONFLICT"),
        OPERATION_NOT_FOUND("OPERATION_NOT_FOUND"),
        ASSET_OR_STAGE_DENIED("ASSET_OR_STAGE_DENIED"),
        WALLET_OWNERSHIP_DENIED("WALLET_OWNERSHIP_DENIED"),
        RESERVATION_CONFLICT("RESERVATION_CONFLICT"),
        PREVIOUS_OUTPUT_MISMATCH("PREVIOUS_OUTPUT_MISMATCH"),
        TRANSACTION_POLICY_DENIED("TRANSACTION_POLICY_DENIED"),
        SIGNING_DENIED("SIGNING_DENIED"),
        BROADCAST_PREREQUISITE_MISSING("BROADCAST_PREREQUISITE_MISSING"),
        BROADCAST_IDENTITY_MISMATCH("BROADCAST_IDENTITY_MISMATCH"),
        BACKEND_DEFINITIVE_REJECTION("BACKEND_DEFINITIVE_REJECTION"),
        AMBIGUOUS_OUTCOME_QUARANTINED("AMBIGUOUS_OUTCOME_QUARANTINED"),
        PERSISTENCE_UNAVAILABLE("PERSISTENCE_UNAVAILABLE");

        private final String wireValue;

        ErrorCode(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    enum Role {
        MAKER("maker"),
        TAKER("taker");

        private final String wireValue;

        Role(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    enum Stage {
        SERVICE_FEE("service-fee"),
        MAKER_DEPOSIT("maker-deposit"),
        TAKER_DEPOSIT("taker-deposit"),
        MAKER_REDEEM("maker-redeem"),
        TAKER_REDEEM("taker-redeem"),
        MAKER_REFUND("maker-refund"),
        TAKER_REFUND("taker-refund");

        private final String wireValue;

        Stage(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    enum Asset {
        BLOCK("BLOCK"),
        LTC("LTC");

        private final String wireValue;

        Asset(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    enum ReservationPurpose {
        SERVICE_FEE("service-fee"),
        DEPOSIT("deposit");

        private final String wireValue;

        ReservationPurpose(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    enum ProofPurpose {
        XBRIDGE_RESERVED_UTXO_PROOF_V1("xbridge-reserved-utxo-proof-v1");

        private final String wireValue;

        ProofPurpose(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    enum AddressProbePurpose {
        XBRIDGE_OWNED_ADDRESS_PROBE_V1("xbridge-owned-address-probe-v1");

        private final String wireValue;

        AddressProbePurpose(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    enum SighashPolicy {
        ALL("all");

        private final String wireValue;

        SighashPolicy(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    enum Producer {
        XLITE_WALLET_INPUT_SIGNER_V1("xlite-wallet-input-signer-v1"),
        ADMITTED_CORE_PROTOCOL_SIGNER_V1("admitted-core-protocol-signer-v1");

        private final String wireValue;

        Producer(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    enum BroadcastRejectionCode {
        BACKEND_DEFINITIVE_REJECTION("BACKEND_DEFINITIVE_REJECTION");

        private final String wireValue;

        BroadcastRejectionCode(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    enum QuarantineCode {
        BROADCAST_IDENTITY_MISMATCH("BROADCAST_IDENTITY_MISMATCH"),
        AMBIGUOUS_OUTCOME_QUARANTINED("AMBIGUOUS_OUTCOME_QUARANTINED");

        private final String wireValue;

        QuarantineCode(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    sealed interface RequestBody
            permits ContextualRequestBody, CheckOwnedAddressRequest,
                    GetSettlementOperationResultRequest {}

    sealed interface ContextualRequestBody extends RequestBody
            permits ReserveWalletInputsRequest, ProveReservedUtxoOwnershipRequest,
                    SignSettlementStageRequest, BroadcastSettlementStageRequest {}

    sealed interface ResultBody
            permits ReservationResult, OwnershipProofResult, OwnedAddressResult,
                    SignedStageResult, NonBroadcastQuarantinedResult,
                    BroadcastStageResult {}

    sealed interface BroadcastStageResult extends ResultBody
            permits BroadcastDispatchRecordedResult, BroadcastSucceededResult,
                    BroadcastRejectedResult, BroadcastQuarantinedResult {}

    sealed interface RequestEnvelope
            permits ContextualRequestEnvelope, OwnedAddressRequestEnvelope,
                    LookupRequestEnvelope {}

    sealed interface ExpectedErrorIdentity
            permits NoSemanticIdentity, ValidatedRequestIdentity {}

    record ChainIdentity(
            Asset asset,
            String network,
            String genesisHash,
            String networkMagicHex,
            String p2pkhVersion,
            List<String> p2shVersions,
            String signedMessagePrefixHex,
            String baseUnitsPerCoin,
            String minimumConfirmations,
            String chainPolicyDigest) {
        ChainIdentity {
            Objects.requireNonNull(asset);
            Objects.requireNonNull(network);
            Objects.requireNonNull(genesisHash);
            Objects.requireNonNull(networkMagicHex);
            Objects.requireNonNull(p2pkhVersion);
            p2shVersions = List.copyOf(Objects.requireNonNull(p2shVersions));
            Objects.requireNonNull(signedMessagePrefixHex);
            Objects.requireNonNull(baseUnitsPerCoin);
            Objects.requireNonNull(minimumConfirmations);
            Objects.requireNonNull(chainPolicyDigest);
        }
    }

    record PrevoutEvidence(
            String txid,
            String vout,
            String valueBaseUnits,
            String scriptPubKeyHex,
            String ownedAddress,
            String confirmationBlockHash,
            String confirmationHeight,
            String observedConfirmations) {
        PrevoutEvidence {
            Objects.requireNonNull(txid);
            Objects.requireNonNull(vout);
            Objects.requireNonNull(valueBaseUnits);
            Objects.requireNonNull(scriptPubKeyHex);
            Objects.requireNonNull(ownedAddress);
            Objects.requireNonNull(confirmationBlockHash);
            Objects.requireNonNull(confirmationHeight);
            Objects.requireNonNull(observedConfirmations);
        }
    }

    record OutpointIdentity(String txid, String vout) {
        OutpointIdentity {
            Objects.requireNonNull(txid);
            Objects.requireNonNull(vout);
        }
    }

    record ExpectedOutput(String index, String valueBaseUnits, String scriptPubKeyHex) {
        ExpectedOutput {
            Objects.requireNonNull(index);
            Objects.requireNonNull(valueBaseUnits);
            Objects.requireNonNull(scriptPubKeyHex);
        }
    }

    record RecoveryBinding(
            String recoveryRecordId,
            String fundingTxid,
            String refundTxid,
            String recoveryPayloadDigest,
            String persistedAtUnixMillis) {
        RecoveryBinding {
            Objects.requireNonNull(recoveryRecordId);
            Objects.requireNonNull(fundingTxid);
            Objects.requireNonNull(refundTxid);
            Objects.requireNonNull(recoveryPayloadDigest);
            Objects.requireNonNull(persistedAtUnixMillis);
        }
    }

    record ContextualRequestEnvelope(
            Schema schema,
            Operation operation,
            String profileId,
            String runtimeGenerationId,
            String parentIntentId,
            String childStageId,
            String semanticOperationId,
            String payloadDigest,
            String role,
            String stage,
            Asset asset,
            ChainIdentity chainIdentity,
            String policyDigest,
            String issuedAtUnixMillis,
            String deadlineUnixMillis,
            ContextualRequestBody body)
            implements RequestEnvelope {
        ContextualRequestEnvelope {
            Objects.requireNonNull(schema);
            Objects.requireNonNull(operation);
            Objects.requireNonNull(profileId);
            Objects.requireNonNull(runtimeGenerationId);
            Objects.requireNonNull(parentIntentId);
            Objects.requireNonNull(childStageId);
            Objects.requireNonNull(semanticOperationId);
            Objects.requireNonNull(payloadDigest);
            Objects.requireNonNull(role);
            Objects.requireNonNull(stage);
            Objects.requireNonNull(asset);
            Objects.requireNonNull(chainIdentity);
            Objects.requireNonNull(policyDigest);
            Objects.requireNonNull(issuedAtUnixMillis);
            Objects.requireNonNull(deadlineUnixMillis);
            Objects.requireNonNull(body);
            if (schema != Schema.REQUEST || !matches(operation, body)) {
                throw new IllegalArgumentException("Invalid settlement contract.");
            }
        }
    }

    record OwnedAddressRequestEnvelope(
            Schema schema,
            Operation operation,
            String profileId,
            String runtimeGenerationId,
            String semanticOperationId,
            String payloadDigest,
            Asset asset,
            ChainIdentity chainIdentity,
            String policyDigest,
            String issuedAtUnixMillis,
            String deadlineUnixMillis,
            CheckOwnedAddressRequest body)
            implements RequestEnvelope {
        OwnedAddressRequestEnvelope {
            Objects.requireNonNull(schema);
            Objects.requireNonNull(operation);
            Objects.requireNonNull(profileId);
            Objects.requireNonNull(runtimeGenerationId);
            Objects.requireNonNull(semanticOperationId);
            Objects.requireNonNull(payloadDigest);
            Objects.requireNonNull(asset);
            Objects.requireNonNull(chainIdentity);
            Objects.requireNonNull(policyDigest);
            Objects.requireNonNull(issuedAtUnixMillis);
            Objects.requireNonNull(deadlineUnixMillis);
            Objects.requireNonNull(body);
            if (schema != Schema.REQUEST || operation != Operation.CHECK_OWNED_ADDRESS) {
                throw new IllegalArgumentException("Invalid settlement contract.");
            }
        }
    }

    record LookupRequestEnvelope(
            Schema schema,
            Operation operation,
            String profileId,
            String runtimeGenerationId,
            String parentIntentId,
            String childStageId,
            String semanticOperationId,
            String payloadDigest,
            String role,
            String stage,
            Asset asset,
            ChainIdentity chainIdentity,
            String policyDigest,
            String issuedAtUnixMillis,
            String deadlineUnixMillis,
            GetSettlementOperationResultRequest body)
            implements RequestEnvelope {
        LookupRequestEnvelope {
            Objects.requireNonNull(schema);
            Objects.requireNonNull(operation);
            Objects.requireNonNull(profileId);
            Objects.requireNonNull(runtimeGenerationId);
            Objects.requireNonNull(parentIntentId);
            Objects.requireNonNull(childStageId);
            Objects.requireNonNull(semanticOperationId);
            Objects.requireNonNull(payloadDigest);
            Objects.requireNonNull(role);
            Objects.requireNonNull(stage);
            Objects.requireNonNull(asset);
            Objects.requireNonNull(chainIdentity);
            Objects.requireNonNull(policyDigest);
            Objects.requireNonNull(issuedAtUnixMillis);
            Objects.requireNonNull(deadlineUnixMillis);
            Objects.requireNonNull(body);
            if (schema != Schema.REQUEST || operation != Operation.GET_SETTLEMENT_OPERATION_RESULT) {
                throw new IllegalArgumentException("Invalid settlement contract.");
            }
        }
    }

    record NoSemanticIdentity(Operation operation) implements ExpectedErrorIdentity {
        NoSemanticIdentity {
            Objects.requireNonNull(operation);
        }
    }

    record ValidatedRequestIdentity(RequestEnvelope request) implements ExpectedErrorIdentity {
        ValidatedRequestIdentity {
            Objects.requireNonNull(request);
        }
    }

    record ResultEnvelope(
            Schema schema,
            Operation operation,
            String semanticOperationId,
            String payloadDigest,
            Status status,
            String resultDigest,
            String recordedAtUnixMillis,
            ResultBody body) {
        ResultEnvelope {
            Objects.requireNonNull(schema);
            Objects.requireNonNull(operation);
            Objects.requireNonNull(semanticOperationId);
            Objects.requireNonNull(payloadDigest);
            Objects.requireNonNull(status);
            Objects.requireNonNull(resultDigest);
            Objects.requireNonNull(recordedAtUnixMillis);
            Objects.requireNonNull(body);
            if (schema != Schema.RESULT || !matches(operation, status, body)) {
                throw new IllegalArgumentException("Invalid settlement contract.");
            }
        }
    }

    record ErrorEnvelope(
            Schema schema,
            Operation operation,
            String semanticOperationId,
            ErrorCode code,
            boolean quarantined,
            String recordedAtUnixMillis) {
        ErrorEnvelope {
            Objects.requireNonNull(schema);
            Objects.requireNonNull(operation);
            Objects.requireNonNull(code);
            Objects.requireNonNull(recordedAtUnixMillis);
            if (schema != Schema.ERROR) {
                throw new IllegalArgumentException("Invalid settlement contract.");
            }
        }
    }

    record ReserveWalletInputsRequest(
            ReservationPurpose purpose,
            String walletAddress,
            String baseAmountBlockUnits,
            String outgoingAmountUnits,
            String fixedTakerFeeBlockUnits,
            String maxStageNativeFeeUnits,
            String maxGraphNativeFeeUnits,
            String recoveryReserveUnits,
            List<PrevoutEvidence> outpoints)
            implements ContextualRequestBody {
        ReserveWalletInputsRequest {
            Objects.requireNonNull(purpose);
            Objects.requireNonNull(walletAddress);
            Objects.requireNonNull(baseAmountBlockUnits);
            Objects.requireNonNull(outgoingAmountUnits);
            Objects.requireNonNull(fixedTakerFeeBlockUnits);
            Objects.requireNonNull(maxStageNativeFeeUnits);
            Objects.requireNonNull(maxGraphNativeFeeUnits);
            Objects.requireNonNull(recoveryReserveUnits);
            outpoints = List.copyOf(Objects.requireNonNull(outpoints));
        }
    }

    record ProveReservedUtxoOwnershipRequest(
            String reservationId,
            OutpointIdentity outpoint,
            String messageHex,
            String messageDigest,
            ProofPurpose purpose)
            implements ContextualRequestBody {
        ProveReservedUtxoOwnershipRequest {
            Objects.requireNonNull(reservationId);
            Objects.requireNonNull(outpoint);
            Objects.requireNonNull(messageHex);
            Objects.requireNonNull(messageDigest);
            Objects.requireNonNull(purpose);
        }
    }

    record CheckOwnedAddressRequest(String address, AddressProbePurpose purpose)
            implements RequestBody {
        CheckOwnedAddressRequest {
            Objects.requireNonNull(address);
            Objects.requireNonNull(purpose);
        }
    }

    record SignSettlementStageRequest(
            String reservationId,
            String unsignedTransactionHex,
            String unsignedBytesDigest,
            String unsignedTxid,
            String unsignedWtxid,
            List<PrevoutEvidence> previousOutputs,
            List<ExpectedOutput> expectedOutputs,
            String changeAddress,
            List<String> inputSequences,
            String lockTime,
            SighashPolicy sighashPolicy,
            String maxStageNativeFeeUnits,
            String contractPolicyDigest)
            implements ContextualRequestBody {
        SignSettlementStageRequest {
            Objects.requireNonNull(reservationId);
            Objects.requireNonNull(unsignedTransactionHex);
            Objects.requireNonNull(unsignedBytesDigest);
            Objects.requireNonNull(unsignedTxid);
            Objects.requireNonNull(unsignedWtxid);
            previousOutputs = List.copyOf(Objects.requireNonNull(previousOutputs));
            expectedOutputs = List.copyOf(Objects.requireNonNull(expectedOutputs));
            Objects.requireNonNull(changeAddress);
            inputSequences = List.copyOf(Objects.requireNonNull(inputSequences));
            Objects.requireNonNull(lockTime);
            Objects.requireNonNull(sighashPolicy);
            Objects.requireNonNull(maxStageNativeFeeUnits);
            Objects.requireNonNull(contractPolicyDigest);
        }
    }

    record BroadcastSettlementStageRequest(
            String finalTransactionHex,
            String finalBytesDigest,
            String finalTxid,
            String finalWtxid,
            Producer producer,
            String predecessorOperationId,
            String contractPolicyDigest,
            RecoveryBinding recoveryBinding)
            implements ContextualRequestBody {
        BroadcastSettlementStageRequest {
            Objects.requireNonNull(finalTransactionHex);
            Objects.requireNonNull(finalBytesDigest);
            Objects.requireNonNull(finalTxid);
            Objects.requireNonNull(finalWtxid);
            Objects.requireNonNull(producer);
            Objects.requireNonNull(predecessorOperationId);
            Objects.requireNonNull(contractPolicyDigest);
        }
    }

    record GetSettlementOperationResultRequest(
            String targetSemanticOperationId, String expectedPayloadDigest)
            implements RequestBody {
        GetSettlementOperationResultRequest {
            Objects.requireNonNull(targetSemanticOperationId);
            Objects.requireNonNull(expectedPayloadDigest);
        }
    }

    record ReservationResult(
            String reservationId,
            String reservationDigest,
            List<OutpointIdentity> outpoints,
            boolean deduplicated)
            implements ResultBody {
        ReservationResult {
            Objects.requireNonNull(reservationId);
            Objects.requireNonNull(reservationDigest);
            outpoints = List.copyOf(Objects.requireNonNull(outpoints));
            if (deduplicated) {
                throw new IllegalArgumentException("Invalid settlement contract.");
            }
        }
    }

    record OwnershipProofResult(
            String messageDigest, String compactProofBase64, String proofDigest)
            implements ResultBody {
        OwnershipProofResult {
            Objects.requireNonNull(messageDigest);
            Objects.requireNonNull(compactProofBase64);
            Objects.requireNonNull(proofDigest);
        }
    }

    record OwnedAddressResult(boolean owned) implements ResultBody {}

    record SignedStageResult(
            String finalTransactionHex,
            String finalBytesDigest,
            String finalTxid,
            String finalWtxid,
            List<String> signedInputIndexes)
            implements ResultBody {
        SignedStageResult {
            Objects.requireNonNull(finalTransactionHex);
            Objects.requireNonNull(finalBytesDigest);
            Objects.requireNonNull(finalTxid);
            Objects.requireNonNull(finalWtxid);
            signedInputIndexes = List.copyOf(Objects.requireNonNull(signedInputIndexes));
        }
    }

    record NonBroadcastQuarantinedResult(
            QuarantineCode quarantineCode, String evidenceDigest)
            implements ResultBody {
        NonBroadcastQuarantinedResult {
            Objects.requireNonNull(quarantineCode);
            Objects.requireNonNull(evidenceDigest);
            if (quarantineCode != QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED) {
                throw new IllegalArgumentException("Invalid settlement contract.");
            }
        }
    }

    record BroadcastDispatchRecordedResult(
            String finalBytesDigest,
            String finalTxid,
            String finalWtxid,
            String dispatchRecordDigest)
            implements BroadcastStageResult {
        BroadcastDispatchRecordedResult {
            Objects.requireNonNull(finalBytesDigest);
            Objects.requireNonNull(finalTxid);
            Objects.requireNonNull(finalWtxid);
            Objects.requireNonNull(dispatchRecordDigest);
        }
    }

    record BroadcastSucceededResult(
            String finalBytesDigest, String finalTxid, String finalWtxid, String returnedTxid)
            implements BroadcastStageResult {
        BroadcastSucceededResult {
            Objects.requireNonNull(finalBytesDigest);
            Objects.requireNonNull(finalTxid);
            Objects.requireNonNull(finalWtxid);
            Objects.requireNonNull(returnedTxid);
            if (!finalTxid.equals(returnedTxid)) {
                throw new IllegalArgumentException("Invalid settlement contract.");
            }
        }
    }

    record BroadcastRejectedResult(
            String finalBytesDigest,
            String finalTxid,
            String finalWtxid,
            BroadcastRejectionCode rejectionCode,
            String evidenceDigest)
            implements BroadcastStageResult {
        BroadcastRejectedResult {
            Objects.requireNonNull(finalBytesDigest);
            Objects.requireNonNull(finalTxid);
            Objects.requireNonNull(finalWtxid);
            Objects.requireNonNull(rejectionCode);
            Objects.requireNonNull(evidenceDigest);
        }
    }

    record BroadcastQuarantinedResult(
            String finalBytesDigest,
            String finalTxid,
            String finalWtxid,
            QuarantineCode quarantineCode,
            String evidenceDigest)
            implements BroadcastStageResult {
        BroadcastQuarantinedResult {
            Objects.requireNonNull(finalBytesDigest);
            Objects.requireNonNull(finalTxid);
            Objects.requireNonNull(finalWtxid);
            Objects.requireNonNull(quarantineCode);
            Objects.requireNonNull(evidenceDigest);
        }
    }

    record IdentityInputs(
            Operation operation,
            String profileId,
            String parentIntentId,
            String childStageId,
            String role,
            String stage,
            Asset asset,
            String chainPolicyDigest,
            String policyDigest,
            String operationObjectId) {
        IdentityInputs {
            Objects.requireNonNull(operation);
            Objects.requireNonNull(profileId);
            Objects.requireNonNull(parentIntentId);
            Objects.requireNonNull(childStageId);
            Objects.requireNonNull(role);
            Objects.requireNonNull(stage);
            Objects.requireNonNull(asset);
            Objects.requireNonNull(chainPolicyDigest);
            Objects.requireNonNull(policyDigest);
            Objects.requireNonNull(operationObjectId);
        }
    }

    record PayloadInputs(
            Operation operation,
            String profileId,
            String parentIntentId,
            String childStageId,
            String role,
            String stage,
            Asset asset,
            String chainIdentityDigest,
            String chainPolicyDigest,
            String policyDigest,
            String deadlineUnixMillis,
            String operationObjectId,
            String bodyDigest) {
        PayloadInputs {
            Objects.requireNonNull(operation);
            Objects.requireNonNull(profileId);
            Objects.requireNonNull(parentIntentId);
            Objects.requireNonNull(childStageId);
            Objects.requireNonNull(role);
            Objects.requireNonNull(stage);
            Objects.requireNonNull(asset);
            Objects.requireNonNull(chainIdentityDigest);
            Objects.requireNonNull(chainPolicyDigest);
            Objects.requireNonNull(policyDigest);
            Objects.requireNonNull(deadlineUnixMillis);
            Objects.requireNonNull(operationObjectId);
            Objects.requireNonNull(bodyDigest);
        }
    }

    record ResultDigestInputs(
            Operation operation,
            String semanticOperationId,
            String payloadDigest,
            Status status,
            String recordedAtUnixMillis,
            ResultBody body) {
        ResultDigestInputs {
            Objects.requireNonNull(operation);
            Objects.requireNonNull(semanticOperationId);
            Objects.requireNonNull(payloadDigest);
            Objects.requireNonNull(status);
            Objects.requireNonNull(recordedAtUnixMillis);
            Objects.requireNonNull(body);
        }
    }

    record GenerationHandover(
            String profileId,
            String priorGenerationId,
            String nextGenerationId,
            String desktopJournalHeadDigest,
            String handoverDigest) {
        GenerationHandover {
            Objects.requireNonNull(profileId);
            Objects.requireNonNull(priorGenerationId);
            Objects.requireNonNull(nextGenerationId);
            Objects.requireNonNull(desktopJournalHeadDigest);
            Objects.requireNonNull(handoverDigest);
        }
    }

    record TargetContext(
            String profileId,
            String parentIntentId,
            String childStageId,
            String role,
            String stage,
            Asset asset,
            ChainIdentity chainIdentity,
            String policyDigest) {
        TargetContext {
            Objects.requireNonNull(profileId);
            Objects.requireNonNull(parentIntentId);
            Objects.requireNonNull(childStageId);
            Objects.requireNonNull(role);
            Objects.requireNonNull(stage);
            Objects.requireNonNull(asset);
            Objects.requireNonNull(chainIdentity);
            Objects.requireNonNull(policyDigest);
        }
    }

    record OperationLookup(
            String runtimeGenerationId,
            TargetContext targetContext,
            String semanticOperationId,
            String payloadDigest) {
        OperationLookup {
            Objects.requireNonNull(runtimeGenerationId);
            Objects.requireNonNull(targetContext);
            Objects.requireNonNull(semanticOperationId);
            Objects.requireNonNull(payloadDigest);
        }
    }

    record OperationAppend(
            String runtimeGenerationId,
            TargetContext targetContext,
            Operation operation,
            String semanticOperationId,
            String payloadDigest,
            Status status,
            String resultDigest,
            byte[] canonicalResultEnvelope) {
        OperationAppend {
            Objects.requireNonNull(runtimeGenerationId);
            Objects.requireNonNull(targetContext);
            Objects.requireNonNull(operation);
            Objects.requireNonNull(semanticOperationId);
            Objects.requireNonNull(payloadDigest);
            Objects.requireNonNull(status);
            Objects.requireNonNull(resultDigest);
            canonicalResultEnvelope = Objects.requireNonNull(canonicalResultEnvelope).clone();
        }

        @Override
        public byte[] canonicalResultEnvelope() {
            return canonicalResultEnvelope.clone();
        }
    }

    record StoreLimits(int maxFrames, long maxBytes) {}

    enum StoreHold {
        PAYLOAD_CONFLICT,
        TRANSITION_DENIED,
        NOT_FOUND,
        TARGET_CONTEXT_MISMATCH,
        STALE_GENERATION,
        STALE_OR_CONFLICTING_HANDOVER,
        CAPACITY_UNAVAILABLE,
        PERSISTENCE_UNAVAILABLE
    }

    sealed interface AppendOutcome permits Appended, IdenticalReplay, AppendHeld {}

    record Appended(byte[] canonicalResultEnvelope) implements AppendOutcome {
        Appended {
            canonicalResultEnvelope = Objects.requireNonNull(canonicalResultEnvelope).clone();
        }

        @Override
        public byte[] canonicalResultEnvelope() {
            return canonicalResultEnvelope.clone();
        }
    }

    record IdenticalReplay(byte[] canonicalResultEnvelope) implements AppendOutcome {
        IdenticalReplay {
            canonicalResultEnvelope = Objects.requireNonNull(canonicalResultEnvelope).clone();
        }

        @Override
        public byte[] canonicalResultEnvelope() {
            return canonicalResultEnvelope.clone();
        }
    }

    record AppendHeld(StoreHold reason) implements AppendOutcome {
        AppendHeld {
            Objects.requireNonNull(reason);
            if (reason != StoreHold.PAYLOAD_CONFLICT
                    && reason != StoreHold.TRANSITION_DENIED
                    && reason != StoreHold.TARGET_CONTEXT_MISMATCH
                    && reason != StoreHold.STALE_GENERATION
                    && reason != StoreHold.CAPACITY_UNAVAILABLE
                    && reason != StoreHold.PERSISTENCE_UNAVAILABLE) {
                throw new IllegalArgumentException("Invalid settlement store outcome.");
            }
        }
    }

    sealed interface LookupOutcome permits Found, LookupHeld {}

    record Found(byte[] canonicalResultEnvelope) implements LookupOutcome {
        Found {
            canonicalResultEnvelope = Objects.requireNonNull(canonicalResultEnvelope).clone();
        }

        @Override
        public byte[] canonicalResultEnvelope() {
            return canonicalResultEnvelope.clone();
        }
    }

    record LookupHeld(StoreHold reason) implements LookupOutcome {
        LookupHeld {
            Objects.requireNonNull(reason);
            if (reason != StoreHold.NOT_FOUND
                    && reason != StoreHold.PAYLOAD_CONFLICT
                    && reason != StoreHold.TARGET_CONTEXT_MISMATCH
                    && reason != StoreHold.STALE_GENERATION
                    && reason != StoreHold.PERSISTENCE_UNAVAILABLE) {
                throw new IllegalArgumentException("Invalid settlement store outcome.");
            }
        }
    }

    sealed interface HandoverOutcome permits HandoverAccepted, HandoverHeld {}

    record HandoverAccepted(String nextGenerationId) implements HandoverOutcome {
        HandoverAccepted {
            Objects.requireNonNull(nextGenerationId);
        }
    }

    record HandoverHeld(StoreHold reason) implements HandoverOutcome {
        HandoverHeld {
            Objects.requireNonNull(reason);
            if (reason != StoreHold.STALE_OR_CONFLICTING_HANDOVER
                    && reason != StoreHold.CAPACITY_UNAVAILABLE
                    && reason != StoreHold.PERSISTENCE_UNAVAILABLE) {
                throw new IllegalArgumentException("Invalid settlement store outcome.");
            }
        }
    }

    sealed interface SnapshotOutcome permits SnapshotAvailable, SnapshotHeld {}

    record SnapshotAvailable(StoreSnapshot snapshot) implements SnapshotOutcome {
        SnapshotAvailable {
            Objects.requireNonNull(snapshot);
        }
    }

    record SnapshotHeld(StoreHold reason) implements SnapshotOutcome {
        SnapshotHeld {
            Objects.requireNonNull(reason);
            if (reason != StoreHold.PERSISTENCE_UNAVAILABLE) {
                throw new IllegalArgumentException("Invalid settlement store outcome.");
            }
        }
    }

    record StoreSnapshot(
            String profileId,
            String currentGenerationId,
            int authenticatedFrameCount,
            long authenticatedByteCount,
            int retainedOperationCount,
            String latestFrameMacHex) {
        StoreSnapshot {
            Objects.requireNonNull(profileId);
            Objects.requireNonNull(currentGenerationId);
            Objects.requireNonNull(latestFrameMacHex);
        }
    }

    enum FactoryFailure {
        INVALID_ROOT,
        INVALID_KEY,
        INVALID_IDENTITY,
        INVALID_LIMITS,
        LOCK_UNAVAILABLE,
        PERSISTENCE_UNAVAILABLE,
        AUTHENTICATION_FAILED,
        PREPARED_QUARANTINE_MISMATCH,
        CAPACITY_UNAVAILABLE
    }

    static final class StoreOpenException extends Exception {
        private static final String MESSAGE = "Settlement store unavailable.";
        private final FactoryFailure reason;

        StoreOpenException(FactoryFailure reason) {
            super(MESSAGE);
            this.reason = Objects.requireNonNull(reason);
        }

        FactoryFailure reason() {
            return reason;
        }
    }

    static final class StoreCloseException extends Exception {
        private static final String MESSAGE = "Settlement store unavailable.";

        StoreCloseException() {
            super(MESSAGE);
        }
    }

    private static boolean matches(Operation operation, ContextualRequestBody body) {
        return switch (operation) {
            case RESERVE_WALLET_INPUTS -> body instanceof ReserveWalletInputsRequest;
            case PROVE_RESERVED_UTXO_OWNERSHIP ->
                    body instanceof ProveReservedUtxoOwnershipRequest;
            case SIGN_SETTLEMENT_STAGE -> body instanceof SignSettlementStageRequest;
            case BROADCAST_SETTLEMENT_STAGE ->
                    body instanceof BroadcastSettlementStageRequest;
            case CHECK_OWNED_ADDRESS, GET_SETTLEMENT_OPERATION_RESULT -> false;
        };
    }

    private static boolean matches(Operation operation, Status status, ResultBody body) {
        return switch (operation) {
            case RESERVE_WALLET_INPUTS ->
                    (status == Status.RESERVED && body instanceof ReservationResult)
                            || (status == Status.QUARANTINED
                                    && body instanceof NonBroadcastQuarantinedResult);
            case PROVE_RESERVED_UTXO_OWNERSHIP ->
                    (status == Status.PROOF_CREATED && body instanceof OwnershipProofResult)
                            || (status == Status.QUARANTINED
                                    && body instanceof NonBroadcastQuarantinedResult);
            case CHECK_OWNED_ADDRESS ->
                    (status == Status.OWNERSHIP_CHECKED && body instanceof OwnedAddressResult)
                            || (status == Status.QUARANTINED
                                    && body instanceof NonBroadcastQuarantinedResult);
            case SIGN_SETTLEMENT_STAGE ->
                    (status == Status.SIGNED && body instanceof SignedStageResult)
                            || (status == Status.QUARANTINED
                                    && body instanceof NonBroadcastQuarantinedResult);
            case BROADCAST_SETTLEMENT_STAGE ->
                    (status == Status.BROADCAST_DISPATCH_RECORDED
                                    && body instanceof BroadcastDispatchRecordedResult)
                            || (status == Status.BROADCAST_SUCCEEDED
                                    && body instanceof BroadcastSucceededResult)
                            || (status == Status.BROADCAST_REJECTED
                                    && body instanceof BroadcastRejectedResult)
                            || (status == Status.QUARANTINED
                                    && body instanceof BroadcastQuarantinedResult);
            case GET_SETTLEMENT_OPERATION_RESULT -> false;
        };
    }
}
