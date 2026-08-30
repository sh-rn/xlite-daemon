package io.cloudchains.app.net.settlement;

import static io.cloudchains.app.net.settlement.SettlementContractV1.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class InertSettlementOperationStoreV1Test {
    private static final byte[] KEY = "synthetic-inert-key-32-bytes!!!!".getBytes();
    private static final byte[] FRAME_DOMAIN =
            "BLOCKNET-XLITE-SETTLEMENT-INERT-FRAME-V1\n".getBytes();
    private static final StoreLimits LIMITS = new StoreLimits(1024, 16_777_216);
    private static final String GENERATION_2 = "99".repeat(32);

    @TempDir Path temporaryDirectory;

    @BeforeEach
    void resolveTemporaryDirectoryWithoutSymlinks() throws IOException {
        temporaryDirectory = temporaryDirectory.toRealPath();
    }

    @Test
    void appendsReplaysLooksUpHandsOverAndReopensWithoutRedispatch() throws Exception {
        InertSettlementOperationStoreV1 store = fresh(temporaryDirectory);
        OperationAppend reservation =
                append(
                        Operation.RESERVE_WALLET_INPUTS,
                        Status.RESERVED,
                        new ReservationResult(
                                "12".repeat(32),
                                "13".repeat(32),
                                List.of(new OutpointIdentity("14".repeat(32), "0")),
                                false),
                        SettlementIdentityV1Test.GENERATION,
                        SettlementIdentityV1Test.validTarget(Operation.RESERVE_WALLET_INPUTS),
                        "15".repeat(32),
                        "16".repeat(32));

        assertInstanceOf(Appended.class, store.append(reservation));
        StoreSnapshot afterAppend = ((SnapshotAvailable) store.snapshot()).snapshot();
        assertEquals(2, afterAppend.authenticatedFrameCount());
        assertEquals(1, afterAppend.retainedOperationCount());
        assertInstanceOf(IdenticalReplay.class, store.append(reservation));
        assertEquals(afterAppend, ((SnapshotAvailable) store.snapshot()).snapshot());

        OperationLookup lookup =
                new OperationLookup(
                        SettlementIdentityV1Test.GENERATION,
                        reservation.targetContext(),
                        reservation.semanticOperationId(),
                        reservation.payloadDigest());
        assertArrayEquals(
                reservation.canonicalResultEnvelope(),
                ((Found) store.lookup(lookup)).canonicalResultEnvelope());
        assertEquals(
                StoreHold.NOT_FOUND,
                ((LookupHeld)
                                store.lookup(
                                        new OperationLookup(
                                                lookup.runtimeGenerationId(),
                                                lookup.targetContext(),
                                                "17".repeat(32),
                                                lookup.payloadDigest())))
                        .reason());

        OperationAppend conflict =
                append(
                        Operation.RESERVE_WALLET_INPUTS,
                        Status.RESERVED,
                        (ReservationResult) resultBody(reservation),
                        reservation.runtimeGenerationId(),
                        reservation.targetContext(),
                        reservation.semanticOperationId(),
                        "18".repeat(32));
        assertEquals(StoreHold.PAYLOAD_CONFLICT, ((AppendHeld) store.append(conflict)).reason());

        GenerationHandover handover = handover(SettlementIdentityV1Test.GENERATION, GENERATION_2);
        assertEquals(GENERATION_2, ((HandoverAccepted) store.acceptHandover(handover)).nextGenerationId());
        assertEquals(
                StoreHold.STALE_GENERATION,
                ((LookupHeld) store.lookup(lookup)).reason());
        OperationLookup currentLookup =
                new OperationLookup(
                        GENERATION_2,
                        lookup.targetContext(),
                        lookup.semanticOperationId(),
                        lookup.payloadDigest());
        assertArrayEquals(
                reservation.canonicalResultEnvelope(),
                ((Found) store.lookup(currentLookup)).canonicalResultEnvelope());
        assertEquals(
                StoreHold.STALE_OR_CONFLICTING_HANDOVER,
                ((HandoverHeld) store.acceptHandover(handover)).reason());
        store.close();

        assertEquals(
                StoreHold.PERSISTENCE_UNAVAILABLE,
                ((LookupHeld) store.lookup(currentLookup)).reason());
        assertEquals(
                StoreHold.PERSISTENCE_UNAVAILABLE,
                ((AppendHeld) store.append(reservation)).reason());
        assertInstanceOf(SnapshotHeld.class, store.snapshot());
        assertDoesNotThrow(store::close);

        InertSettlementOperationStoreV1 reopened =
                InertSettlementOperationStoreV1.reopenTemporaryProfile(
                        temporaryDirectory, KEY, LIMITS, List.of());
        assertArrayEquals(
                reservation.canonicalResultEnvelope(),
                ((Found) reopened.lookup(currentLookup)).canonicalResultEnvelope());
        reopened.close();
    }

    @Test
    void quarantinesLatestDispatchBeforeReopenReturnsAndNeverProvidesRedispatch() throws Exception {
        InertSettlementOperationStoreV1 store = fresh(temporaryDirectory);
        TargetContext target =
                SettlementIdentityV1Test.validTarget(Operation.BROADCAST_SETTLEMENT_STAGE);
        OperationAppend dispatch =
                append(
                        Operation.BROADCAST_SETTLEMENT_STAGE,
                        Status.BROADCAST_DISPATCH_RECORDED,
                        new BroadcastDispatchRecordedResult(
                                "21".repeat(32),
                                "22".repeat(32),
                                "23".repeat(32),
                                "24".repeat(32)),
                        SettlementIdentityV1Test.GENERATION,
                        target,
                        "25".repeat(32),
                        "26".repeat(32));
        assertInstanceOf(Appended.class, store.append(dispatch));
        store.close();

        StoreOpenException missing =
                assertThrows(
                        StoreOpenException.class,
                        () ->
                                InertSettlementOperationStoreV1.reopenTemporaryProfile(
                                        temporaryDirectory, KEY, LIMITS, List.of()));
        assertEquals(FactoryFailure.PREPARED_QUARANTINE_MISMATCH, missing.reason());

        OperationAppend quarantine =
                append(
                        Operation.BROADCAST_SETTLEMENT_STAGE,
                        Status.QUARANTINED,
                        new BroadcastQuarantinedResult(
                                "21".repeat(32),
                                "22".repeat(32),
                                "23".repeat(32),
                                QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED,
                                "27".repeat(32)),
                        SettlementIdentityV1Test.GENERATION,
                        target,
                        dispatch.semanticOperationId(),
                        dispatch.payloadDigest());
        InertSettlementOperationStoreV1 reopened =
                InertSettlementOperationStoreV1.reopenTemporaryProfile(
                        temporaryDirectory, KEY, LIMITS, List.of(quarantine));
        Found found =
                (Found)
                        reopened.lookup(
                                new OperationLookup(
                                        SettlementIdentityV1Test.GENERATION,
                                        target,
                                        dispatch.semanticOperationId(),
                                        dispatch.payloadDigest()));
        assertEquals(
                Status.QUARANTINED,
                SettlementEnvelopeCodecV1.decodeResult(found.canonicalResultEnvelope()).status());
        assertEquals(3, ((SnapshotAvailable) reopened.snapshot()).snapshot().authenticatedFrameCount());
        assertTrue(
                java.util.Arrays.stream(InertSettlementOperationStoreV1.class.getDeclaredMethods())
                        .noneMatch(method -> method.getName().toLowerCase().contains("dispatch")));
        reopened.close();
    }

    @Test
    void locksAuthenticatesEveryFrameAndFailsClosedOnCorruption() throws Exception {
        InertSettlementOperationStoreV1 store = fresh(temporaryDirectory);
        StoreOpenException locked =
                assertThrows(
                        StoreOpenException.class,
                        () ->
                                InertSettlementOperationStoreV1.reopenTemporaryProfile(
                                        temporaryDirectory, KEY, LIMITS, List.of()));
        assertEquals(FactoryFailure.LOCK_UNAVAILABLE, locked.reason());
        StoreSnapshot initial = ((SnapshotAvailable) store.snapshot()).snapshot();
        assertEquals(1, initial.authenticatedFrameCount());
        assertTrue(initial.authenticatedByteCount() > 36);
        assertEquals(64, initial.latestFrameMacHex().length());
        store.close();

        Path log = temporaryDirectory.resolve("xlite-settlement-inert-test-v1.log");
        byte[] bytes = Files.readAllBytes(log);
        bytes[bytes.length - 1] ^= 1;
        Files.write(log, bytes);
        StoreOpenException corrupted =
                assertThrows(
                        StoreOpenException.class,
                        () ->
                                InertSettlementOperationStoreV1.reopenTemporaryProfile(
                                        temporaryDirectory, KEY, LIMITS, List.of()));
        assertEquals(FactoryFailure.AUTHENTICATION_FAILED, corrupted.reason());
        assertEquals("Settlement store unavailable.", corrupted.getMessage());
        assertNull(corrupted.getCause());
    }

    @Test
    void factoryAloneValidatesRawLimitsAndCleansEveryFailure() throws Exception {
        assertFactoryFailure(new StoreLimits(0, 1), FactoryFailure.INVALID_LIMITS, "zero");
        assertFactoryFailure(new StoreLimits(-1, 1), FactoryFailure.INVALID_LIMITS, "negative");
        assertFactoryFailure(
                new StoreLimits(1025, 16_777_216), FactoryFailure.INVALID_LIMITS, "frames");
        assertFactoryFailure(
                new StoreLimits(1024, 16_777_217), FactoryFailure.INVALID_LIMITS, "bytes");
        assertFactoryFailure(new StoreLimits(1, 1), FactoryFailure.CAPACITY_UNAVAILABLE, "small");

        Path badKeyRoot = Files.createDirectory(temporaryDirectory.resolve("bad-key"));
        StoreOpenException badKey =
                assertThrows(
                        StoreOpenException.class,
                        () ->
                                InertSettlementOperationStoreV1.createFreshTemporaryProfile(
                                        badKeyRoot,
                                        new byte[31],
                                        LIMITS,
                                        SettlementIdentityV1Test.PROFILE,
                                        SettlementIdentityV1Test.GENERATION));
        assertEquals(FactoryFailure.INVALID_KEY, badKey.reason());

        Path badIdentityRoot = Files.createDirectory(temporaryDirectory.resolve("bad-id"));
        StoreOpenException badIdentity =
                assertThrows(
                        StoreOpenException.class,
                        () ->
                                InertSettlementOperationStoreV1.createFreshTemporaryProfile(
                                        badIdentityRoot,
                                        KEY,
                                        LIMITS,
                                        "bad",
                                        SettlementIdentityV1Test.GENERATION));
        assertEquals(FactoryFailure.INVALID_IDENTITY, badIdentity.reason());
        assertEquals(0, Files.list(badIdentityRoot).count());
    }

    @Test
    void rejectsTargetContextAndGenerationBeforeReturningStoredBytes() throws Exception {
        InertSettlementOperationStoreV1 store = fresh(temporaryDirectory);
        OperationAppend reservation =
                append(
                        Operation.RESERVE_WALLET_INPUTS,
                        Status.RESERVED,
                        new ReservationResult(
                                "31".repeat(32),
                                "32".repeat(32),
                                List.of(new OutpointIdentity("33".repeat(32), "0")),
                                false),
                        SettlementIdentityV1Test.GENERATION,
                        SettlementIdentityV1Test.validTarget(Operation.RESERVE_WALLET_INPUTS),
                        "34".repeat(32),
                        "35".repeat(32));
        assertInstanceOf(Appended.class, store.append(reservation));
        TargetContext wrong =
                new TargetContext(
                        reservation.targetContext().profileId(),
                        reservation.targetContext().parentIntentId(),
                        reservation.targetContext().childStageId(),
                        reservation.targetContext().role(),
                        "maker-deposit",
                        reservation.targetContext().asset(),
                        reservation.targetContext().chainIdentity(),
                        reservation.targetContext().policyDigest());
        assertEquals(
                StoreHold.TARGET_CONTEXT_MISMATCH,
                ((LookupHeld)
                                store.lookup(
                                        new OperationLookup(
                                                SettlementIdentityV1Test.GENERATION,
                                                wrong,
                                                reservation.semanticOperationId(),
                                                reservation.payloadDigest())))
                        .reason());
        assertEquals(
                StoreHold.STALE_GENERATION,
                ((LookupHeld)
                                store.lookup(
                                        new OperationLookup(
                                                GENERATION_2,
                                                reservation.targetContext(),
                                                reservation.semanticOperationId(),
                                                reservation.payloadDigest())))
                        .reason());
        store.close();
    }

    @Test
    void enforcesEveryInitialQuarantineSuccessorAndTerminalTransition() throws Exception {
        List<Operation> nonBroadcast =
                List.of(
                        Operation.RESERVE_WALLET_INPUTS,
                        Operation.PROVE_RESERVED_UTXO_OWNERSHIP,
                        Operation.CHECK_OWNED_ADDRESS,
                        Operation.SIGN_SETTLEMENT_STAGE);
        int marker = 50;
        for (Operation operation : nonBroadcast) {
            Status success = successStatus(operation);
            TargetContext target = SettlementIdentityV1Test.validTarget(operation);
            String semantic = hex(marker++);
            String payload = hex(marker++);

            Path directRoot = directory("direct-" + operation.name());
            InertSettlementOperationStoreV1 direct = fresh(directRoot);
            OperationAppend terminal =
                    append(
                            operation,
                            success,
                            resultBody(operation, success, hex(marker++)),
                            SettlementIdentityV1Test.GENERATION,
                            target,
                            semantic,
                            payload);
            assertInstanceOf(Appended.class, direct.append(terminal), operation.name());
            assertInstanceOf(IdenticalReplay.class, direct.append(terminal), operation.name());
            OperationAppend changedTerminal =
                    append(
                            operation,
                            success,
                            operation == Operation.CHECK_OWNED_ADDRESS
                                    ? new OwnedAddressResult(false)
                                    : resultBody(operation, success, hex(marker++)),
                            SettlementIdentityV1Test.GENERATION,
                            target,
                            semantic,
                            payload);
            assertAppendHeld(direct, changedTerminal, StoreHold.TRANSITION_DENIED);
            direct.close();

            Path quarantineRoot = directory("quarantine-" + operation.name());
            InertSettlementOperationStoreV1 quarantined = fresh(quarantineRoot);
            OperationAppend quarantine =
                    append(
                            operation,
                            Status.QUARANTINED,
                            resultBody(operation, Status.QUARANTINED, hex(marker++)),
                            SettlementIdentityV1Test.GENERATION,
                            target,
                            semantic,
                            payload);
            assertInstanceOf(Appended.class, quarantined.append(quarantine), operation.name());
            assertInstanceOf(IdenticalReplay.class, quarantined.append(quarantine), operation.name());
            OperationAppend secondQuarantine =
                    append(
                            operation,
                            Status.QUARANTINED,
                            resultBody(operation, Status.QUARANTINED, hex(marker++)),
                            SettlementIdentityV1Test.GENERATION,
                            target,
                            semantic,
                            payload);
            assertAppendHeld(quarantined, secondQuarantine, StoreHold.TRANSITION_DENIED);
            assertInstanceOf(Appended.class, quarantined.append(terminal), operation.name());
            assertInstanceOf(IdenticalReplay.class, quarantined.append(terminal), operation.name());
            quarantined.close();
        }

        Path crossingRoot = directory("non-broadcast-crossing");
        InertSettlementOperationStoreV1 crossing = fresh(crossingRoot);
        String semantic = hex(90);
        String payload = hex(91);
        OperationAppend reservation =
                append(
                        Operation.RESERVE_WALLET_INPUTS,
                        Status.RESERVED,
                        resultBody(Operation.RESERVE_WALLET_INPUTS, Status.RESERVED, hex(92)),
                        SettlementIdentityV1Test.GENERATION,
                        SettlementIdentityV1Test.validTarget(Operation.RESERVE_WALLET_INPUTS),
                        semantic,
                        payload);
        assertInstanceOf(Appended.class, crossing.append(reservation));
        OperationAppend proofCrossing =
                append(
                        Operation.PROVE_RESERVED_UTXO_OWNERSHIP,
                        Status.PROOF_CREATED,
                        resultBody(
                                Operation.PROVE_RESERVED_UTXO_OWNERSHIP,
                                Status.PROOF_CREATED,
                                hex(93)),
                        SettlementIdentityV1Test.GENERATION,
                        SettlementIdentityV1Test.validTarget(
                                Operation.PROVE_RESERVED_UTXO_OWNERSHIP),
                        semantic,
                        payload);
        assertAppendHeld(crossing, proofCrossing, StoreHold.TRANSITION_DENIED);
        crossing.close();
    }

    @Test
    void enforcesEveryBroadcastTransitionAndExactFinalIdentity() throws Exception {
        int caseNumber = 100;
        for (Status successor :
                List.of(
                        Status.BROADCAST_SUCCEEDED,
                        Status.BROADCAST_REJECTED,
                        Status.QUARANTINED)) {
            Path root = directory("broadcast-dispatch-" + successor.name());
            InertSettlementOperationStoreV1 store = fresh(root);
            String semantic = hex(caseNumber++);
            String payload = hex(caseNumber++);
            OperationAppend dispatch = broadcastAppend(Status.BROADCAST_DISPATCH_RECORDED, semantic, payload, hex(1));
            assertInstanceOf(Appended.class, store.append(dispatch));
            assertInstanceOf(IdenticalReplay.class, store.append(dispatch));
            assertAppendHeld(
                    store,
                    broadcastAppend(
                            Status.BROADCAST_DISPATCH_RECORDED,
                            semantic,
                            payload,
                            hex(caseNumber++)),
                    StoreHold.TRANSITION_DENIED);
            OperationAppend next = broadcastAppend(successor, semantic, payload, hex(caseNumber++));
            assertInstanceOf(Appended.class, store.append(next), successor.name());
            assertInstanceOf(IdenticalReplay.class, store.append(next), successor.name());
            List<Status> deniedSuccessors =
                    successor == Status.QUARANTINED
                            ? List.of(Status.BROADCAST_DISPATCH_RECORDED, Status.QUARANTINED)
                            : List.of(
                                    Status.BROADCAST_DISPATCH_RECORDED,
                                    Status.BROADCAST_SUCCEEDED,
                                    Status.BROADCAST_REJECTED,
                                    Status.QUARANTINED);
            for (Status deniedSuccessor : deniedSuccessors) {
                OperationAppend deniedCandidate =
                        broadcastAppend(
                                deniedSuccessor,
                                semantic,
                                payload,
                                hex(caseNumber++));
                if (deniedSuccessor == successor
                        && successor == Status.BROADCAST_SUCCEEDED) {
                    ResultEnvelope envelope =
                            SettlementEnvelopeCodecV1.decodeResult(
                                    deniedCandidate.canonicalResultEnvelope());
                    deniedCandidate =
                            appendAt(
                                    deniedCandidate.operation(),
                                    deniedCandidate.status(),
                                    envelope.body(),
                                    deniedCandidate.runtimeGenerationId(),
                                    deniedCandidate.targetContext(),
                                    deniedCandidate.semanticOperationId(),
                                    deniedCandidate.payloadDigest(),
                                    "401");
                }
                assertAppendHeld(
                        store,
                        deniedCandidate,
                        StoreHold.TRANSITION_DENIED);
            }
            store.close();
        }

        for (Status recovered :
                List.of(Status.BROADCAST_SUCCEEDED, Status.BROADCAST_REJECTED)) {
            Path root = directory("broadcast-quarantine-" + recovered.name());
            InertSettlementOperationStoreV1 store = fresh(root);
            String semantic = hex(caseNumber++);
            String payload = hex(caseNumber++);
            assertInstanceOf(
                    Appended.class,
                    store.append(
                            broadcastAppend(
                                    Status.BROADCAST_DISPATCH_RECORDED,
                                    semantic,
                                    payload,
                                    hex(caseNumber++))));
            OperationAppend quarantined =
                    broadcastAppend(
                            Status.QUARANTINED,
                            semantic,
                            payload,
                            hex(caseNumber++));
            assertInstanceOf(
                    Appended.class, store.append(quarantined));
            assertInstanceOf(IdenticalReplay.class, store.append(quarantined));
            assertAppendHeld(
                    store,
                    broadcastAppend(
                            Status.BROADCAST_DISPATCH_RECORDED,
                            semantic,
                            payload,
                            hex(caseNumber++)),
                    StoreHold.TRANSITION_DENIED);
            assertInstanceOf(
                    Appended.class,
                    store.append(broadcastAppend(recovered, semantic, payload, hex(caseNumber++))));
            store.close();
        }

        for (Status forbidden :
                List.of(Status.BROADCAST_SUCCEEDED, Status.BROADCAST_REJECTED, Status.QUARANTINED)) {
            Path root = directory("broadcast-direct-" + forbidden.name());
            InertSettlementOperationStoreV1 store = fresh(root);
            assertAppendHeld(
                    store,
                    broadcastAppend(forbidden, hex(caseNumber++), hex(caseNumber++), hex(caseNumber++)),
                    StoreHold.TRANSITION_DENIED);
            store.close();
        }

        Path mismatchRoot = directory("broadcast-identity-mismatch");
        InertSettlementOperationStoreV1 mismatch = fresh(mismatchRoot);
        String semantic = hex(caseNumber++);
        String payload = hex(caseNumber++);
        assertInstanceOf(
                Appended.class,
                mismatch.append(
                        broadcastAppend(
                                Status.BROADCAST_DISPATCH_RECORDED,
                                semantic,
                                payload,
                                hex(caseNumber++))));
        OperationAppend wrongIdentity =
                append(
                        Operation.BROADCAST_SETTLEMENT_STAGE,
                        Status.BROADCAST_SUCCEEDED,
                        new BroadcastSucceededResult(hex(210), hex(211), hex(212), hex(211)),
                        SettlementIdentityV1Test.GENERATION,
                        SettlementIdentityV1Test.validTarget(
                                Operation.BROADCAST_SETTLEMENT_STAGE),
                        semantic,
                        payload);
        assertAppendHeld(mismatch, wrongIdentity, StoreHold.TRANSITION_DENIED);
        mismatch.close();
    }

    @Test
    void identicalReplayIsByteAndFileTimeInvariantAndMalformedCandidateIsTransitionDenied()
            throws Exception {
        Path root = directory("replay");
        InertSettlementOperationStoreV1 store = fresh(root);
        OperationAppend value =
                append(
                        Operation.RESERVE_WALLET_INPUTS,
                        Status.RESERVED,
                        resultBody(Operation.RESERVE_WALLET_INPUTS, Status.RESERVED, hex(10)),
                        SettlementIdentityV1Test.GENERATION,
                        SettlementIdentityV1Test.validTarget(Operation.RESERVE_WALLET_INPUTS),
                        hex(11),
                        hex(12));
        assertInstanceOf(Appended.class, store.append(value));
        Path log = log(root);
        byte[] before = Files.readAllBytes(log);
        FileTime fixed = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(log, fixed);
        StoreSnapshot snapshot = ((SnapshotAvailable) store.snapshot()).snapshot();
        IdenticalReplay replay = (IdenticalReplay) store.append(value);
        assertArrayEquals(value.canonicalResultEnvelope(), replay.canonicalResultEnvelope());
        assertArrayEquals(before, Files.readAllBytes(log));
        assertEquals(fixed, Files.getLastModifiedTime(log));
        assertEquals(snapshot, ((SnapshotAvailable) store.snapshot()).snapshot());

        byte[] malformedBytes = value.canonicalResultEnvelope();
        malformedBytes[0] = '[';
        OperationAppend malformed =
                new OperationAppend(
                        value.runtimeGenerationId(),
                        value.targetContext(),
                        value.operation(),
                        value.semanticOperationId(),
                        value.payloadDigest(),
                        value.status(),
                        value.resultDigest(),
                        malformedBytes);
        assertAppendHeld(store, malformed, StoreHold.TRANSITION_DENIED);
        assertInstanceOf(IdenticalReplay.class, store.append(value));
        store.close();
    }

    @Test
    void authenticatesAllThreeFrameVariantsAndExactFirstAndChainedHmacPreimages()
            throws Exception {
        Path root = directory("hmac");
        InertSettlementOperationStoreV1 store = fresh(root);
        OperationAppend operation =
                append(
                        Operation.CHECK_OWNED_ADDRESS,
                        Status.OWNERSHIP_CHECKED,
                        new OwnedAddressResult(true),
                        SettlementIdentityV1Test.GENERATION,
                        SettlementIdentityV1Test.validTarget(Operation.CHECK_OWNED_ADDRESS),
                        hex(20),
                        hex(21));
        assertInstanceOf(Appended.class, store.append(operation));
        assertInstanceOf(
                HandoverAccepted.class,
                store.acceptHandover(handover(SettlementIdentityV1Test.GENERATION, GENERATION_2)));
        StoreSnapshot snapshot = ((SnapshotAvailable) store.snapshot()).snapshot();
        store.close();

        List<TestFrame> frames = frames(Files.readAllBytes(log(root)));
        assertEquals(3, frames.size());
        assertEquals(
                List.of("profile-initialisation", "operation-append", "generation-handover"),
                frames.stream()
                        .map(frame -> SettlementCanonicalJsonV1.parse(frame.record()).getAsJsonObject())
                        .map(object -> object.get("frameType").getAsString())
                        .toList());
        byte[] previous = new byte[32];
        long physical = 0;
        for (TestFrame frame : frames) {
            byte[] preimage = hmacPreimage(previous, frame.record());
            assertEquals(FRAME_DOMAIN.length + 32 + 4 + frame.record().length, preimage.length);
            byte[] expected = hmac(KEY, preimage);
            assertArrayEquals(expected, frame.mac());
            previous = frame.mac();
            physical += 4L + frame.record().length + 32L;
        }
        assertEquals(frames.size(), snapshot.authenticatedFrameCount());
        assertEquals(physical, snapshot.authenticatedByteCount());
        assertEquals(1, snapshot.retainedOperationCount());
        assertEquals(java.util.HexFormat.of().formatHex(previous), snapshot.latestFrameMacHex());
    }

    @Test
    void rejectsEveryPartialFrameBoundaryLengthMacTrailingAndAuthenticatedFieldFault()
            throws Exception {
        byte[] valid = threeFrameLog(directory("corruption-source"));
        List<TestFrame> frames = frames(valid);
        Set<Integer> cuts = new LinkedHashSet<>();
        for (TestFrame frame : frames) {
            cuts.add(frame.start());
            cuts.add(frame.start() + 1);
            cuts.add(frame.start() + 3);
            cuts.add(frame.recordStart());
            cuts.add(frame.recordEnd() - 1);
            cuts.add(frame.recordEnd());
            cuts.add(frame.end() - 1);
        }
        cuts.remove(0);
        cuts.remove(valid.length);
        for (TestFrame frame : frames) {
            cuts.remove(frame.start());
            cuts.remove(frame.end());
        }
        for (int cut : cuts) {
            assertAuthenticationFailure("cut-" + cut, Arrays.copyOf(valid, cut));
        }
        assertAuthenticationFailure("empty", new byte[0]);
        assertAuthenticationFailure("trailing", concatenate(valid, new byte[] {0}));

        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            byte[] badMac = valid.clone();
            badMac[frames.get(frameIndex).end() - 1] ^= 1;
            assertAuthenticationFailure("mac-" + frameIndex, badMac);
        }
        for (int invalidLength : List.of(0, -1, 262_145, Integer.MAX_VALUE)) {
            byte[] badLength = valid.clone();
            ByteBuffer.wrap(badLength).putInt(invalidLength);
            assertAuthenticationFailure("length-" + invalidLength, badLength);
        }

        assertAuthenticatedRecordFailure(
                valid, "initial-extra", 0, object -> object.addProperty("extra", "x"));
        assertAuthenticatedRecordFailure(
                valid,
                "initial-type",
                0,
                object -> object.addProperty("frameType", "operation-append"));
        assertAuthenticatedRecordFailure(
                valid,
                "operation-semantic",
                1,
                object -> object.addProperty("semanticOperationId", hex(222)));
        assertAuthenticatedRecordFailure(
                valid,
                "operation-generation",
                1,
                object -> object.addProperty("runtimeGenerationId", hex(226)));
        assertAuthenticatedRecordFailure(
                valid,
                "operation-name",
                1,
                object -> object.addProperty("operation", "signSettlementStage"));
        assertAuthenticatedRecordFailure(
                valid,
                "operation-payload",
                1,
                object -> object.addProperty("payloadDigest", hex(227)));
        assertAuthenticatedRecordFailure(
                valid,
                "operation-status",
                1,
                object -> object.addProperty("status", "quarantined"));
        assertAuthenticatedRecordFailure(
                valid,
                "operation-result-digest",
                1,
                object -> object.addProperty("resultDigest", hex(223)));
        assertAuthenticatedRecordFailure(
                valid,
                "operation-envelope",
                1,
                object -> object.addProperty("resultEnvelopeBase64", Base64.getEncoder().encodeToString("{}".getBytes())));
        assertAuthenticatedRecordFailure(
                valid,
                "target-profile",
                1,
                object -> object.getAsJsonObject("targetContext").addProperty("profileId", hex(224)));
        assertAuthenticatedRecordFailure(
                valid,
                "handover-digest",
                2,
                object -> object.addProperty("handoverDigest", hex(225)));
    }

    @Test
    void preparedStartupBatchMustBeCompleteOrderedMatchingAndCapacityPreflighted()
            throws Exception {
        Path root = directory("prepared");
        InertSettlementOperationStoreV1 store = fresh(root);
        OperationAppend first = broadcastAppend(Status.BROADCAST_DISPATCH_RECORDED, hex(10), hex(11), hex(12));
        OperationAppend second = broadcastAppend(Status.BROADCAST_DISPATCH_RECORDED, hex(20), hex(21), hex(22));
        assertInstanceOf(Appended.class, store.append(second));
        assertInstanceOf(Appended.class, store.append(first));
        store.close();
        byte[] before = Files.readAllBytes(log(root));
        OperationAppend firstQuarantine = broadcastAppend(Status.QUARANTINED, first.semanticOperationId(), first.payloadDigest(), hex(31));
        OperationAppend secondQuarantine = broadcastAppend(Status.QUARANTINED, second.semanticOperationId(), second.payloadDigest(), hex(32));

        assertPreparedFailure(root, List.of(), FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        assertPreparedFailure(root, List.of(firstQuarantine), FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        assertPreparedFailure(
                root,
                List.of(secondQuarantine, firstQuarantine),
                FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        OperationAppend extra = broadcastAppend(Status.QUARANTINED, hex(30), hex(31), hex(33));
        assertPreparedFailure(
                root,
                List.of(firstQuarantine, secondQuarantine, extra),
                FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        OperationAppend mismatch =
                broadcastAppend(Status.QUARANTINED, first.semanticOperationId(), hex(99), hex(34));
        assertPreparedFailure(
                root,
                List.of(mismatch, secondQuarantine),
                FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        OperationAppend wrongContext =
                append(
                        Operation.BROADCAST_SETTLEMENT_STAGE,
                        Status.QUARANTINED,
                        resultBody(firstQuarantine),
                        firstQuarantine.runtimeGenerationId(),
                        targetVariants(firstQuarantine.targetContext()).get(0),
                        firstQuarantine.semanticOperationId(),
                        firstQuarantine.payloadDigest());
        assertPreparedFailure(
                root,
                List.of(wrongContext, secondQuarantine),
                FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        OperationAppend wrongGeneration =
                append(
                        Operation.BROADCAST_SETTLEMENT_STAGE,
                        Status.QUARANTINED,
                        resultBody(firstQuarantine),
                        GENERATION_2,
                        firstQuarantine.targetContext(),
                        firstQuarantine.semanticOperationId(),
                        firstQuarantine.payloadDigest());
        assertPreparedFailure(
                root,
                List.of(wrongGeneration, secondQuarantine),
                FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        OperationAppend wrongFinalIdentity =
                append(
                        Operation.BROADCAST_SETTLEMENT_STAGE,
                        Status.QUARANTINED,
                        new BroadcastQuarantinedResult(
                                hex(35),
                                hex(62),
                                hex(63),
                                QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED,
                                hex(36)),
                        firstQuarantine.runtimeGenerationId(),
                        firstQuarantine.targetContext(),
                        firstQuarantine.semanticOperationId(),
                        firstQuarantine.payloadDigest());
        assertPreparedFailure(
                root,
                List.of(wrongFinalIdentity, secondQuarantine),
                FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        assertArrayEquals(before, Files.readAllBytes(log(root)));

        assertPreparedFailure(
                root,
                List.of(firstQuarantine, secondQuarantine),
                FactoryFailure.CAPACITY_UNAVAILABLE,
                new StoreLimits(4, LIMITS.maxBytes()));
        assertArrayEquals(before, Files.readAllBytes(log(root)));

        Path measuredRoot = directory("prepared-measured");
        Files.write(log(measuredRoot), before);
        InertSettlementOperationStoreV1 measured =
                InertSettlementOperationStoreV1.reopenTemporaryProfile(
                        measuredRoot,
                        KEY,
                        LIMITS,
                        List.of(firstQuarantine, secondQuarantine));
        measured.close();
        long requiredBytes = Files.size(log(measuredRoot));
        assertPreparedFailure(
                root,
                List.of(firstQuarantine, secondQuarantine),
                FactoryFailure.CAPACITY_UNAVAILABLE,
                new StoreLimits(LIMITS.maxFrames(), requiredBytes - 1));
        assertArrayEquals(before, Files.readAllBytes(log(root)));

        InertSettlementOperationStoreV1 reopened =
                InertSettlementOperationStoreV1.reopenTemporaryProfile(
                        root, KEY, LIMITS, List.of(firstQuarantine, secondQuarantine));
        StoreSnapshot snapshot = ((SnapshotAvailable) reopened.snapshot()).snapshot();
        assertEquals(5, snapshot.authenticatedFrameCount());
        assertEquals(2, snapshot.retainedOperationCount());
        reopened.close();
    }

    @Test
    void startupQuarantineSelectsOnlyChainsWhoseLatestStatusIsDispatchRecorded()
            throws Exception {
        Path root = directory("prepared-latest-only");
        InertSettlementOperationStoreV1 store = fresh(root);
        OperationAppend terminalDispatch =
                broadcastAppend(Status.BROADCAST_DISPATCH_RECORDED, hex(40), hex(41), hex(42));
        OperationAppend unresolvedDispatch =
                broadcastAppend(Status.BROADCAST_DISPATCH_RECORDED, hex(50), hex(51), hex(52));
        assertInstanceOf(Appended.class, store.append(terminalDispatch));
        assertInstanceOf(
                Appended.class,
                store.append(
                        broadcastAppend(
                                Status.BROADCAST_SUCCEEDED,
                                terminalDispatch.semanticOperationId(),
                                terminalDispatch.payloadDigest(),
                                hex(43))));
        assertInstanceOf(Appended.class, store.append(unresolvedDispatch));
        store.close();
        OperationAppend onlyPrepared =
                broadcastAppend(
                        Status.QUARANTINED,
                        unresolvedDispatch.semanticOperationId(),
                        unresolvedDispatch.payloadDigest(),
                        hex(53));
        assertPreparedFailure(
                root,
                List.of(
                        broadcastAppend(
                                Status.QUARANTINED,
                                terminalDispatch.semanticOperationId(),
                                terminalDispatch.payloadDigest(),
                                hex(54)),
                        onlyPrepared),
                FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        InertSettlementOperationStoreV1 reopened =
                InertSettlementOperationStoreV1.reopenTemporaryProfile(
                        root, KEY, LIMITS, List.of(onlyPrepared));
        assertEquals(2, ((SnapshotAvailable) reopened.snapshot()).snapshot().retainedOperationCount());
        reopened.close();
    }

    @Test
    void handoverRejectsStaleSameReusedMalformedAndConflictingGenerations() throws Exception {
        Path root = directory("handovers");
        InertSettlementOperationStoreV1 store = fresh(root);
        String generation3 = hex(103);
        assertInstanceOf(
                HandoverAccepted.class,
                store.acceptHandover(handover(SettlementIdentityV1Test.GENERATION, GENERATION_2)));
        assertHandoverHeld(
                store,
                handover(SettlementIdentityV1Test.GENERATION, generation3),
                StoreHold.STALE_OR_CONFLICTING_HANDOVER);
        assertHandoverHeld(
                store, handover(GENERATION_2, GENERATION_2), StoreHold.STALE_OR_CONFLICTING_HANDOVER);
        assertInstanceOf(HandoverAccepted.class, store.acceptHandover(handover(GENERATION_2, generation3)));
        assertHandoverHeld(
                store,
                handover(generation3, SettlementIdentityV1Test.GENERATION),
                StoreHold.STALE_OR_CONFLICTING_HANDOVER);
        GenerationHandover malformed =
                new GenerationHandover(
                        SettlementIdentityV1Test.PROFILE,
                        generation3,
                        hex(104),
                        hex(105),
                        hex(106));
        assertHandoverHeld(store, malformed, StoreHold.STALE_OR_CONFLICTING_HANDOVER);
        store.close();

        InertSettlementOperationStoreV1 reopened =
                InertSettlementOperationStoreV1.reopenTemporaryProfile(root, KEY, LIMITS, List.of());
        assertEquals(generation3, ((SnapshotAvailable) reopened.snapshot()).snapshot().currentGenerationId());
        reopened.close();
    }

    @Test
    void exclusiveWriterLivesForInstanceLifetimeAndAllRootKeyAndReopenLimitsFailClosed()
            throws Exception {
        Path root = directory("writer");
        InertSettlementOperationStoreV1 first = fresh(root);
        assertFactoryFailure(root, KEY, LIMITS, List.of(), FactoryFailure.LOCK_UNAVAILABLE);
        OperationAppend append =
                append(
                        Operation.RESERVE_WALLET_INPUTS,
                        Status.RESERVED,
                        resultBody(Operation.RESERVE_WALLET_INPUTS, Status.RESERVED, hex(110)),
                        SettlementIdentityV1Test.GENERATION,
                        SettlementIdentityV1Test.validTarget(Operation.RESERVE_WALLET_INPUTS),
                        hex(111),
                        hex(112));
        assertInstanceOf(Appended.class, first.append(append));
        assertFactoryFailure(root, KEY, LIMITS, List.of(), FactoryFailure.LOCK_UNAVAILABLE);
        assertInstanceOf(
                HandoverAccepted.class,
                first.acceptHandover(handover(SettlementIdentityV1Test.GENERATION, GENERATION_2)));
        assertFactoryFailure(root, KEY, LIMITS, List.of(), FactoryFailure.LOCK_UNAVAILABLE);
        first.close();
        InertSettlementOperationStoreV1 afterClose =
                InertSettlementOperationStoreV1.reopenTemporaryProfile(root, KEY, LIMITS, List.of());
        afterClose.close();

        Path nonEmpty = directory("non-empty-fresh");
        Files.writeString(nonEmpty.resolve("sibling"), "x");
        assertFreshFailure(nonEmpty, KEY, LIMITS, FactoryFailure.INVALID_ROOT);
        assertFreshFailure(Path.of("relative-root"), KEY, LIMITS, FactoryFailure.INVALID_ROOT);
        assertFreshFailure(
                temporaryDirectory.resolve("missing").resolve(".."),
                KEY,
                LIMITS,
                FactoryFailure.INVALID_ROOT);

        Path rootLinkTarget = directory("root-link-target");
        Path rootLink = temporaryDirectory.resolve("root-link");
        Files.createSymbolicLink(rootLink, rootLinkTarget);
        assertFreshFailure(rootLink, KEY, LIMITS, FactoryFailure.INVALID_ROOT);
        Path ancestorTarget = directory("ancestor-link-target");
        Path childThroughTarget = Files.createDirectory(ancestorTarget.resolve("child"));
        Path ancestorLink = temporaryDirectory.resolve("ancestor-link");
        Files.createSymbolicLink(ancestorLink, ancestorTarget);
        assertTrue(Files.isDirectory(childThroughTarget));
        assertFreshFailure(
                ancestorLink.resolve("child"), KEY, LIMITS, FactoryFailure.INVALID_ROOT);

        Path siblingRoot = directory("reopen-sibling");
        InertSettlementOperationStoreV1 siblingStore = fresh(siblingRoot);
        siblingStore.close();
        Files.writeString(siblingRoot.resolve("sibling"), "x");
        assertFactoryFailure(siblingRoot, KEY, LIMITS, List.of(), FactoryFailure.INVALID_ROOT);

        Path symlinkLogRoot = directory("symlink-log");
        Path external = temporaryDirectory.resolve("external-log");
        Files.writeString(external, "x");
        Files.createSymbolicLink(symlinkLogRoot.resolve("xlite-settlement-inert-test-v1.log"), external);
        assertFactoryFailure(symlinkLogRoot, KEY, LIMITS, List.of(), FactoryFailure.INVALID_ROOT);

        Path keyRoot = directory("wrong-key");
        InertSettlementOperationStoreV1 keyStore = fresh(keyRoot);
        keyStore.close();
        byte[] wrongKey = KEY.clone();
        wrongKey[0] ^= 1;
        assertFactoryFailure(
                keyRoot, wrongKey, LIMITS, List.of(), FactoryFailure.AUTHENTICATION_FAILED);
        long currentBytes = Files.size(log(keyRoot));
        assertFactoryFailure(
                keyRoot,
                KEY,
                new StoreLimits(1024, currentBytes - 1),
                List.of(),
                FactoryFailure.CAPACITY_UNAVAILABLE);

        Path preparedRoot = directory("invalid-prepared-shapes");
        InertSettlementOperationStoreV1 preparedStore = fresh(preparedRoot);
        preparedStore.close();
        assertFactoryFailure(
                preparedRoot,
                KEY,
                LIMITS,
                null,
                FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        assertFactoryFailure(
                preparedRoot,
                KEY,
                LIMITS,
                Arrays.asList((OperationAppend) null),
                FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        InertSettlementOperationStoreV1 afterPreparedFailure =
                InertSettlementOperationStoreV1.reopenTemporaryProfile(
                        preparedRoot, KEY, LIMITS, List.of());
        afterPreparedFailure.close();
    }

    @Test
    void appendAndHandoverWriteAndForceFaultsPoisonImmediatelyAndZeroCopiedKey()
            throws Exception {
        for (Fault fault : List.of(Fault.WRITE, Fault.FORCE)) {
            assertPoisonedByAppend(fault);
            assertPoisonedByHandover(fault);
        }
    }

    @Test
    void normalCloseForceAndChannelFailuresStillCleanAndBecomePermanentHeldState()
            throws Exception {
        for (Fault fault : List.of(Fault.FORCE, Fault.CLOSE)) {
            Path root = directory("close-failure-" + fault.name());
            InertSettlementOperationStoreV1 store = fresh(root);
            CountingFileChannel channel = installChannel(store, fault);
            byte[] copiedKey = internalKey(store);
            StoreCloseException close = assertThrows(StoreCloseException.class, store::close);
            assertEquals("Settlement store unavailable.", close.getMessage());
            assertNull(close.getCause());
            assertTrue(allZero(copiedKey));
            assertFalse(channel.isOpen());
            long accesses = channel.accesses();
            assertInstanceOf(SnapshotHeld.class, store.snapshot());
            assertEquals(accesses, channel.accesses());
            assertDoesNotThrow(store::close);
        }
    }

    @Test
    void staleAndPostCloseOutcomesPerformNoFileAccessAndNeverReturnCachedBytes()
            throws Exception {
        Path root = directory("no-access");
        byte[] suppliedKey = KEY.clone();
        InertSettlementOperationStoreV1 store =
                InertSettlementOperationStoreV1.createFreshTemporaryProfile(
                        root,
                        suppliedKey,
                        LIMITS,
                        SettlementIdentityV1Test.PROFILE,
                        SettlementIdentityV1Test.GENERATION);
        byte[] copiedKey = internalKey(store);
        assertNotSame(suppliedKey, copiedKey);
        assertArrayEquals(KEY, suppliedKey);
        CountingFileChannel counting = installChannel(store, Fault.NONE);
        OperationAppend stale =
                append(
                        Operation.RESERVE_WALLET_INPUTS,
                        Status.RESERVED,
                        resultBody(Operation.RESERVE_WALLET_INPUTS, Status.RESERVED, hex(120)),
                        GENERATION_2,
                        SettlementIdentityV1Test.validTarget(Operation.RESERVE_WALLET_INPUTS),
                        hex(121),
                        hex(122));
        long before = counting.accesses();
        assertAppendHeld(store, stale, StoreHold.STALE_GENERATION);
        assertEquals(
                StoreHold.STALE_GENERATION,
                ((LookupHeld)
                                store.lookup(
                                        new OperationLookup(
                                                GENERATION_2,
                                                stale.targetContext(),
                                                stale.semanticOperationId(),
                                                stale.payloadDigest())))
                        .reason());
        assertEquals(before, counting.accesses());

        store.close();
        assertTrue(allZero(copiedKey));
        assertArrayEquals(KEY, suppliedKey);
        long afterClose = counting.accesses();
        assertAppendHeld(store, stale, StoreHold.PERSISTENCE_UNAVAILABLE);
        assertEquals(
                StoreHold.PERSISTENCE_UNAVAILABLE,
                ((LookupHeld)
                                store.lookup(
                                        new OperationLookup(
                                                SettlementIdentityV1Test.GENERATION,
                                                stale.targetContext(),
                                                stale.semanticOperationId(),
                                                stale.payloadDigest())))
                        .reason());
        assertEquals(
                StoreHold.PERSISTENCE_UNAVAILABLE,
                ((HandoverHeld)
                                store.acceptHandover(
                                        handover(SettlementIdentityV1Test.GENERATION, GENERATION_2)))
                        .reason());
        assertInstanceOf(SnapshotHeld.class, store.snapshot());
        assertEquals(afterClose, counting.accesses());
        assertDoesNotThrow(store::close);
    }

    @Test
    void deepTargetContextEqualityCoversEveryMemberAndOwnershipEmptyContext() throws Exception {
        Path root = directory("target-context-matrix");
        InertSettlementOperationStoreV1 store = fresh(root);
        TargetContext target = SettlementIdentityV1Test.validTarget(Operation.RESERVE_WALLET_INPUTS);
        String semantic = hex(150);
        String payload = hex(151);
        OperationAppend quarantine =
                append(
                        Operation.RESERVE_WALLET_INPUTS,
                        Status.QUARANTINED,
                        resultBody(Operation.RESERVE_WALLET_INPUTS, Status.QUARANTINED, hex(152)),
                        SettlementIdentityV1Test.GENERATION,
                        target,
                        semantic,
                        payload);
        assertInstanceOf(Appended.class, store.append(quarantine));
        List<TargetContext> variants = targetVariants(target);
        for (TargetContext variant : variants) {
            assertEquals(
                    StoreHold.TARGET_CONTEXT_MISMATCH,
                    ((LookupHeld)
                                    store.lookup(
                                            new OperationLookup(
                                                    SettlementIdentityV1Test.GENERATION,
                                                    variant,
                                                    semantic,
                                                    payload)))
                            .reason(),
                    variant.toString());
            OperationAppend successor =
                    append(
                            Operation.RESERVE_WALLET_INPUTS,
                            Status.RESERVED,
                            resultBody(Operation.RESERVE_WALLET_INPUTS, Status.RESERVED, hex(153)),
                            SettlementIdentityV1Test.GENERATION,
                            variant,
                            semantic,
                            payload);
            assertAppendHeld(store, successor, StoreHold.TARGET_CONTEXT_MISMATCH);
        }
        ChainIdentity chain = target.chainIdentity();
        List<TargetContext> invalidNested =
                List.of(
                        new TargetContext(
                                target.profileId(),
                                target.parentIntentId(),
                                target.childStageId(),
                                target.role(),
                                target.stage(),
                                target.asset(),
                                new ChainIdentity(
                                        chain.asset(),
                                        "testnet",
                                        chain.genesisHash(),
                                        chain.networkMagicHex(),
                                        chain.p2pkhVersion(),
                                        chain.p2shVersions(),
                                        chain.signedMessagePrefixHex(),
                                        chain.baseUnitsPerCoin(),
                                        chain.minimumConfirmations(),
                                        chain.chainPolicyDigest()),
                                target.policyDigest()),
                        new TargetContext(
                                target.profileId(),
                                target.parentIntentId(),
                                target.childStageId(),
                                target.role(),
                                target.stage(),
                                target.asset(),
                                SettlementIdentityV1Test.validChain(Asset.BLOCK),
                                target.policyDigest()));
        for (TargetContext invalid : invalidNested) {
            assertEquals(
                    StoreHold.PERSISTENCE_UNAVAILABLE,
                    ((LookupHeld)
                                    store.lookup(
                                            new OperationLookup(
                                                    SettlementIdentityV1Test.GENERATION,
                                                    invalid,
                                                    semantic,
                                                    payload)))
                            .reason());
            assertAppendHeld(
                    store,
                    append(
                            Operation.RESERVE_WALLET_INPUTS,
                            Status.RESERVED,
                            resultBody(Operation.RESERVE_WALLET_INPUTS, Status.RESERVED, hex(157)),
                            SettlementIdentityV1Test.GENERATION,
                            invalid,
                            semantic,
                            payload),
                    StoreHold.TRANSITION_DENIED);
        }
        store.close();

        Path ownershipRoot = directory("ownership-empty-target");
        InertSettlementOperationStoreV1 ownership = fresh(ownershipRoot);
        TargetContext empty = SettlementIdentityV1Test.validTarget(Operation.CHECK_OWNED_ADDRESS);
        OperationAppend owned =
                append(
                        Operation.CHECK_OWNED_ADDRESS,
                        Status.OWNERSHIP_CHECKED,
                        new OwnedAddressResult(true),
                        SettlementIdentityV1Test.GENERATION,
                        empty,
                        hex(154),
                        hex(155));
        assertInstanceOf(Appended.class, ownership.append(owned));
        assertEquals(
                StoreHold.TARGET_CONTEXT_MISMATCH,
                ((LookupHeld)
                                ownership.lookup(
                                        new OperationLookup(
                                                SettlementIdentityV1Test.GENERATION,
                                                target,
                                                owned.semanticOperationId(),
                                                owned.payloadDigest())))
                        .reason());
        ownership.close();
    }

    @Test
    void matchingGenerationAuthenticatesWholeLogBeforeEveryChainDecisionAndSuccessForce()
            throws Exception {
        Path root = directory("authentication-order");
        InertSettlementOperationStoreV1 store = fresh(root);
        OperationAppend quarantine =
                append(
                        Operation.RESERVE_WALLET_INPUTS,
                        Status.QUARANTINED,
                        resultBody(Operation.RESERVE_WALLET_INPUTS, Status.QUARANTINED, hex(160)),
                        SettlementIdentityV1Test.GENERATION,
                        SettlementIdentityV1Test.validTarget(Operation.RESERVE_WALLET_INPUTS),
                        hex(161),
                        hex(162));
        assertInstanceOf(Appended.class, store.append(quarantine));
        CountingFileChannel channel = installChannel(store, Fault.NONE);

        long access = channel.accesses();
        assertInstanceOf(IdenticalReplay.class, store.append(quarantine));
        assertTrue(channel.accesses() > access);

        access = channel.accesses();
        assertInstanceOf(
                Found.class,
                store.lookup(
                        new OperationLookup(
                                quarantine.runtimeGenerationId(),
                                quarantine.targetContext(),
                                quarantine.semanticOperationId(),
                                quarantine.payloadDigest())));
        assertTrue(channel.accesses() > access);

        access = channel.accesses();
        assertEquals(
                StoreHold.PAYLOAD_CONFLICT,
                ((LookupHeld)
                                store.lookup(
                                        new OperationLookup(
                                                quarantine.runtimeGenerationId(),
                                                quarantine.targetContext(),
                                                quarantine.semanticOperationId(),
                                                hex(168))))
                        .reason());
        assertTrue(channel.accesses() > access);

        access = channel.accesses();
        OperationAppend payloadConflict =
                append(
                        quarantine.operation(),
                        Status.RESERVED,
                        resultBody(Operation.RESERVE_WALLET_INPUTS, Status.RESERVED, hex(163)),
                        quarantine.runtimeGenerationId(),
                        quarantine.targetContext(),
                        quarantine.semanticOperationId(),
                        hex(164));
        assertAppendHeld(store, payloadConflict, StoreHold.PAYLOAD_CONFLICT);
        assertTrue(channel.accesses() > access);

        access = channel.accesses();
        OperationAppend deniedQuarantine =
                append(
                        quarantine.operation(),
                        Status.QUARANTINED,
                        resultBody(
                                Operation.RESERVE_WALLET_INPUTS,
                                Status.QUARANTINED,
                                hex(169)),
                        quarantine.runtimeGenerationId(),
                        quarantine.targetContext(),
                        quarantine.semanticOperationId(),
                        quarantine.payloadDigest());
        assertAppendHeld(store, deniedQuarantine, StoreHold.TRANSITION_DENIED);
        assertTrue(channel.accesses() > access);

        access = channel.accesses();
        TargetContext wrong = targetVariants(quarantine.targetContext()).get(0);
        assertEquals(
                StoreHold.TARGET_CONTEXT_MISMATCH,
                ((LookupHeld)
                                store.lookup(
                                        new OperationLookup(
                                                quarantine.runtimeGenerationId(),
                                                wrong,
                                                quarantine.semanticOperationId(),
                                                quarantine.payloadDigest())))
                        .reason());
        assertTrue(channel.accesses() > access);

        access = channel.accesses();
        assertEquals(
                StoreHold.NOT_FOUND,
                ((LookupHeld)
                                store.lookup(
                                        new OperationLookup(
                                                quarantine.runtimeGenerationId(),
                                                quarantine.targetContext(),
                                                hex(165),
                                                quarantine.payloadDigest())))
                        .reason());
        assertTrue(channel.accesses() > access);

        OperationAppend success =
                append(
                        quarantine.operation(),
                        Status.RESERVED,
                        resultBody(Operation.RESERVE_WALLET_INPUTS, Status.RESERVED, hex(166)),
                        quarantine.runtimeGenerationId(),
                        quarantine.targetContext(),
                        quarantine.semanticOperationId(),
                        quarantine.payloadDigest());
        long forces = channel.forces();
        long writes = channel.writes();
        assertInstanceOf(Appended.class, store.append(success));
        assertTrue(channel.writes() > writes);
        assertEquals(forces + 1, channel.forces());
        store.close();
    }

    @Test
    void lockDenialPrecedesAuthenticationAndBothAppendCapacityBoundsHoldWithoutMutation()
            throws Exception {
        Path root = directory("lock-before-auth");
        InertSettlementOperationStoreV1 store = fresh(root);
        byte[] corrupt = Files.readAllBytes(log(root));
        corrupt[corrupt.length - 1] ^= 1;
        Files.write(log(root), corrupt);
        assertFactoryFailure(root, KEY, LIMITS, List.of(), FactoryFailure.LOCK_UNAVAILABLE);
        store.close();
        assertFactoryFailure(root, KEY, LIMITS, List.of(), FactoryFailure.AUTHENTICATION_FAILED);

        Path frameRoot = directory("frame-capacity");
        InertSettlementOperationStoreV1 frameLimited =
                InertSettlementOperationStoreV1.createFreshTemporaryProfile(
                        frameRoot,
                        KEY,
                        new StoreLimits(1, LIMITS.maxBytes()),
                        SettlementIdentityV1Test.PROFILE,
                        SettlementIdentityV1Test.GENERATION);
        OperationAppend candidate =
                append(
                        Operation.RESERVE_WALLET_INPUTS,
                        Status.RESERVED,
                        resultBody(Operation.RESERVE_WALLET_INPUTS, Status.RESERVED, hex(170)),
                        SettlementIdentityV1Test.GENERATION,
                        SettlementIdentityV1Test.validTarget(Operation.RESERVE_WALLET_INPUTS),
                        hex(171),
                        hex(172));
        byte[] frameBefore = Files.readAllBytes(log(frameRoot));
        assertAppendHeld(frameLimited, candidate, StoreHold.CAPACITY_UNAVAILABLE);
        assertArrayEquals(frameBefore, Files.readAllBytes(log(frameRoot)));
        frameLimited.close();

        Path byteRoot = directory("byte-capacity");
        InertSettlementOperationStoreV1 initial = fresh(byteRoot);
        initial.close();
        long exactInitialBytes = Files.size(log(byteRoot));
        InertSettlementOperationStoreV1 byteLimited =
                InertSettlementOperationStoreV1.reopenTemporaryProfile(
                        byteRoot, KEY, new StoreLimits(1024, exactInitialBytes), List.of());
        byte[] byteBefore = Files.readAllBytes(log(byteRoot));
        assertAppendHeld(byteLimited, candidate, StoreHold.CAPACITY_UNAVAILABLE);
        assertArrayEquals(byteBefore, Files.readAllBytes(log(byteRoot)));
        byteLimited.close();
    }

    private Path directory(String name) throws IOException {
        return Files.createDirectory(temporaryDirectory.resolve(name));
    }

    private static Path log(Path root) {
        return root.resolve("xlite-settlement-inert-test-v1.log");
    }

    private static String hex(int marker) {
        return "%02x".formatted(marker & 0xff).repeat(32);
    }

    private static Status successStatus(Operation operation) {
        return switch (operation) {
            case RESERVE_WALLET_INPUTS -> Status.RESERVED;
            case PROVE_RESERVED_UTXO_OWNERSHIP -> Status.PROOF_CREATED;
            case CHECK_OWNED_ADDRESS -> Status.OWNERSHIP_CHECKED;
            case SIGN_SETTLEMENT_STAGE -> Status.SIGNED;
            case BROADCAST_SETTLEMENT_STAGE, GET_SETTLEMENT_OPERATION_RESULT ->
                    throw new IllegalArgumentException();
        };
    }

    private static List<TargetContext> targetVariants(TargetContext value) {
        ChainIdentity chain = value.chainIdentity();
        List<ChainIdentity> chainVariants =
                List.of(
                        new ChainIdentity(
                                chain.asset(),
                                chain.network(),
                                hex(181),
                                chain.networkMagicHex(),
                                chain.p2pkhVersion(),
                                chain.p2shVersions(),
                                chain.signedMessagePrefixHex(),
                                chain.baseUnitsPerCoin(),
                                chain.minimumConfirmations(),
                                chain.chainPolicyDigest()),
                        new ChainIdentity(
                                chain.asset(),
                                chain.network(),
                                chain.genesisHash(),
                                "11111111",
                                chain.p2pkhVersion(),
                                chain.p2shVersions(),
                                chain.signedMessagePrefixHex(),
                                chain.baseUnitsPerCoin(),
                                chain.minimumConfirmations(),
                                chain.chainPolicyDigest()),
                        new ChainIdentity(
                                chain.asset(),
                                chain.network(),
                                chain.genesisHash(),
                                chain.networkMagicHex(),
                                "49",
                                chain.p2shVersions(),
                                chain.signedMessagePrefixHex(),
                                chain.baseUnitsPerCoin(),
                                chain.minimumConfirmations(),
                                chain.chainPolicyDigest()),
                        new ChainIdentity(
                                chain.asset(),
                                chain.network(),
                                chain.genesisHash(),
                                chain.networkMagicHex(),
                                chain.p2pkhVersion(),
                                List.of("5", "51"),
                                chain.signedMessagePrefixHex(),
                                chain.baseUnitsPerCoin(),
                                chain.minimumConfirmations(),
                                chain.chainPolicyDigest()),
                        new ChainIdentity(
                                chain.asset(),
                                chain.network(),
                                chain.genesisHash(),
                                chain.networkMagicHex(),
                                chain.p2pkhVersion(),
                                chain.p2shVersions(),
                                "20",
                                chain.baseUnitsPerCoin(),
                                chain.minimumConfirmations(),
                                chain.chainPolicyDigest()),
                        new ChainIdentity(
                                chain.asset(),
                                chain.network(),
                                chain.genesisHash(),
                                chain.networkMagicHex(),
                                chain.p2pkhVersion(),
                                chain.p2shVersions(),
                                chain.signedMessagePrefixHex(),
                                "100000001",
                                chain.minimumConfirmations(),
                                chain.chainPolicyDigest()),
                        new ChainIdentity(
                                chain.asset(),
                                chain.network(),
                                chain.genesisHash(),
                                chain.networkMagicHex(),
                                chain.p2pkhVersion(),
                                chain.p2shVersions(),
                                chain.signedMessagePrefixHex(),
                                chain.baseUnitsPerCoin(),
                                "2",
                                chain.chainPolicyDigest()),
                        new ChainIdentity(
                                chain.asset(),
                                chain.network(),
                                chain.genesisHash(),
                                chain.networkMagicHex(),
                                chain.p2pkhVersion(),
                                chain.p2shVersions(),
                                chain.signedMessagePrefixHex(),
                                chain.baseUnitsPerCoin(),
                                chain.minimumConfirmations(),
                                hex(182)));
        List<TargetContext> result = new ArrayList<>();
        result.add(
                new TargetContext(
                        hex(183),
                        value.parentIntentId(),
                        value.childStageId(),
                        value.role(),
                        value.stage(),
                        value.asset(),
                        chain,
                        value.policyDigest()));
        result.add(
                new TargetContext(
                        value.profileId(),
                        hex(184),
                        value.childStageId(),
                        value.role(),
                        value.stage(),
                        value.asset(),
                        chain,
                        value.policyDigest()));
        result.add(
                new TargetContext(
                        value.profileId(),
                        value.parentIntentId(),
                        hex(185),
                        value.role(),
                        value.stage(),
                        value.asset(),
                        chain,
                        value.policyDigest()));
        result.add(
                new TargetContext(
                        value.profileId(),
                        value.parentIntentId(),
                        value.childStageId(),
                        value.role().equals("maker") ? "taker" : "maker",
                        value.stage(),
                        value.asset(),
                        chain,
                        value.policyDigest()));
        result.add(
                new TargetContext(
                        value.profileId(),
                        value.parentIntentId(),
                        value.childStageId(),
                        value.role(),
                        value.stage().equals("maker-deposit") ? "taker-deposit" : "maker-deposit",
                        value.asset(),
                        chain,
                        value.policyDigest()));
        Asset alternate = value.asset() == Asset.LTC ? Asset.BLOCK : Asset.LTC;
        result.add(
                new TargetContext(
                        value.profileId(),
                        value.parentIntentId(),
                        value.childStageId(),
                        value.role(),
                        value.stage(),
                        alternate,
                        SettlementIdentityV1Test.validChain(alternate),
                        value.policyDigest()));
        for (ChainIdentity variant : chainVariants) {
            result.add(
                    new TargetContext(
                            value.profileId(),
                            value.parentIntentId(),
                            value.childStageId(),
                            value.role(),
                            value.stage(),
                            value.asset(),
                            variant,
                            value.policyDigest()));
        }
        result.add(
                new TargetContext(
                        value.profileId(),
                        value.parentIntentId(),
                        value.childStageId(),
                        value.role(),
                        value.stage(),
                        value.asset(),
                        chain,
                        hex(186)));
        return List.copyOf(result);
    }

    private static ResultBody resultBody(Operation operation, Status status, String marker) {
        if (status == Status.QUARANTINED && operation != Operation.BROADCAST_SETTLEMENT_STAGE) {
            return new NonBroadcastQuarantinedResult(
                    QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED, marker);
        }
        return switch (operation) {
            case RESERVE_WALLET_INPUTS ->
                    new ReservationResult(
                            marker,
                            hex(201),
                            List.of(new OutpointIdentity(hex(202), "0")),
                            false);
            case PROVE_RESERVED_UTXO_OWNERSHIP ->
                    new OwnershipProofResult(hex(203), "AQ==", marker);
            case CHECK_OWNED_ADDRESS -> new OwnedAddressResult(!marker.equals(hex(0)));
            case SIGN_SETTLEMENT_STAGE ->
                    new SignedStageResult("00", marker, hex(205), hex(206), List.of("0"));
            case BROADCAST_SETTLEMENT_STAGE, GET_SETTLEMENT_OPERATION_RESULT ->
                    throw new IllegalArgumentException();
        };
    }

    private static OperationAppend broadcastAppend(
            Status status, String semantic, String payload, String evidence) {
        ResultBody body =
                switch (status) {
                    case BROADCAST_DISPATCH_RECORDED ->
                            new BroadcastDispatchRecordedResult(
                                    hex(61), hex(62), hex(63), evidence);
                    case BROADCAST_SUCCEEDED ->
                            new BroadcastSucceededResult(hex(61), hex(62), hex(63), hex(62));
                    case BROADCAST_REJECTED ->
                            new BroadcastRejectedResult(
                                    hex(61),
                                    hex(62),
                                    hex(63),
                                    BroadcastRejectionCode.BACKEND_DEFINITIVE_REJECTION,
                                    evidence);
                    case QUARANTINED ->
                            new BroadcastQuarantinedResult(
                                    hex(61),
                                    hex(62),
                                    hex(63),
                                    QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED,
                                    evidence);
                    default -> throw new IllegalArgumentException();
                };
        return append(
                Operation.BROADCAST_SETTLEMENT_STAGE,
                status,
                body,
                SettlementIdentityV1Test.GENERATION,
                SettlementIdentityV1Test.validTarget(Operation.BROADCAST_SETTLEMENT_STAGE),
                semantic,
                payload);
    }

    private static void assertAppendHeld(
            InertSettlementOperationStoreV1 store,
            OperationAppend append,
            StoreHold expected) {
        AppendOutcome outcome = store.append(append);
        assertInstanceOf(AppendHeld.class, outcome);
        assertEquals(expected, ((AppendHeld) outcome).reason());
    }

    private static void assertHandoverHeld(
            InertSettlementOperationStoreV1 store,
            GenerationHandover handover,
            StoreHold expected) {
        HandoverOutcome outcome = store.acceptHandover(handover);
        assertInstanceOf(HandoverHeld.class, outcome);
        assertEquals(expected, ((HandoverHeld) outcome).reason());
    }

    private byte[] threeFrameLog(Path root) throws Exception {
        InertSettlementOperationStoreV1 store = fresh(root);
        OperationAppend operation =
                append(
                        Operation.RESERVE_WALLET_INPUTS,
                        Status.RESERVED,
                        resultBody(Operation.RESERVE_WALLET_INPUTS, Status.RESERVED, hex(130)),
                        SettlementIdentityV1Test.GENERATION,
                        SettlementIdentityV1Test.validTarget(Operation.RESERVE_WALLET_INPUTS),
                        hex(131),
                        hex(132));
        assertInstanceOf(Appended.class, store.append(operation));
        assertInstanceOf(
                HandoverAccepted.class,
                store.acceptHandover(handover(SettlementIdentityV1Test.GENERATION, GENERATION_2)));
        store.close();
        return Files.readAllBytes(log(root));
    }

    private static List<TestFrame> frames(byte[] file) {
        List<TestFrame> result = new ArrayList<>();
        int offset = 0;
        while (offset < file.length) {
            int start = offset;
            if (file.length - offset < 4) {
                throw new IllegalArgumentException();
            }
            int length = ByteBuffer.wrap(file, offset, 4).getInt();
            int recordStart = offset + 4;
            int recordEnd = recordStart + length;
            int end = recordEnd + 32;
            if (length <= 0 || end > file.length) {
                throw new IllegalArgumentException();
            }
            result.add(
                    new TestFrame(
                            start,
                            recordStart,
                            recordEnd,
                            end,
                            Arrays.copyOfRange(file, recordStart, recordEnd),
                            Arrays.copyOfRange(file, recordEnd, end)));
            offset = end;
        }
        return List.copyOf(result);
    }

    private static byte[] hmacPreimage(byte[] previous, byte[] record) {
        ByteBuffer value =
                ByteBuffer.allocate(FRAME_DOMAIN.length + previous.length + 4 + record.length);
        value.put(FRAME_DOMAIN).put(previous).putInt(record.length).put(record);
        return value.array();
    }

    private static byte[] hmac(byte[] key, byte[] preimage) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(preimage);
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private void assertAuthenticationFailure(String name, byte[] bytes) throws Exception {
        Path root = directory("auth-" + name.replace('-', '_'));
        Files.write(log(root), bytes);
        assertFactoryFailure(root, KEY, LIMITS, List.of(), FactoryFailure.AUTHENTICATION_FAILED);
    }

    private void assertAuthenticatedRecordFailure(
            byte[] valid,
            String name,
            int frameIndex,
            Consumer<JsonObject> mutation)
            throws Exception {
        List<TestFrame> parsed = frames(valid);
        List<byte[]> records = parsed.stream().map(TestFrame::record).map(byte[]::clone).toList();
        JsonObject object = SettlementCanonicalJsonV1.parse(records.get(frameIndex)).getAsJsonObject();
        mutation.accept(object);
        List<byte[]> changed = new ArrayList<>(records);
        changed.set(frameIndex, SettlementCanonicalJsonV1.encode(object));
        assertAuthenticationFailure(name, authenticatedFile(changed));
    }

    private static byte[] authenticatedFile(List<byte[]> records) throws Exception {
        int size = records.stream().mapToInt(record -> 4 + record.length + 32).sum();
        ByteBuffer file = ByteBuffer.allocate(size);
        byte[] previous = new byte[32];
        for (byte[] record : records) {
            byte[] mac = hmac(KEY, hmacPreimage(previous, record));
            file.putInt(record.length).put(record).put(mac);
            previous = mac;
        }
        return file.array();
    }

    private void assertPreparedFailure(
            Path root, List<OperationAppend> prepared, FactoryFailure expected) throws Exception {
        assertPreparedFailure(root, prepared, expected, LIMITS);
    }

    private void assertPreparedFailure(
            Path root,
            List<OperationAppend> prepared,
            FactoryFailure expected,
            StoreLimits limits)
            throws Exception {
        assertFactoryFailure(root, KEY, limits, prepared, expected);
    }

    private static void assertFactoryFailure(
            Path root,
            byte[] key,
            StoreLimits limits,
            List<OperationAppend> prepared,
            FactoryFailure expected) {
        StoreOpenException exception =
                assertThrows(
                        StoreOpenException.class,
                        () ->
                                InertSettlementOperationStoreV1.reopenTemporaryProfile(
                                        root, key, limits, prepared));
        assertEquals(expected, exception.reason());
        assertEquals("Settlement store unavailable.", exception.getMessage());
        assertNull(exception.getCause());
        assertLockReleasedIfLogExists(root, expected);
    }

    private static void assertFreshFailure(
            Path root, byte[] key, StoreLimits limits, FactoryFailure expected) {
        StoreOpenException exception =
                assertThrows(
                        StoreOpenException.class,
                        () ->
                                InertSettlementOperationStoreV1.createFreshTemporaryProfile(
                                        root,
                                        key,
                                        limits,
                                        SettlementIdentityV1Test.PROFILE,
                                        SettlementIdentityV1Test.GENERATION));
        assertEquals(expected, exception.reason());
        assertEquals("Settlement store unavailable.", exception.getMessage());
        assertNull(exception.getCause());
        assertLockReleasedIfLogExists(root, expected);
    }

    private static void assertLockReleasedIfLogExists(Path root, FactoryFailure expected) {
        if (expected == FactoryFailure.LOCK_UNAVAILABLE || root == null || !root.isAbsolute()) {
            return;
        }
        Path file = root.resolve("xlite-settlement-inert-test-v1.log");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        assertDoesNotThrow(
                () -> {
                    try (FileChannel probe =
                                    FileChannel.open(
                                            file,
                                            StandardOpenOption.READ,
                                            StandardOpenOption.WRITE,
                                            LinkOption.NOFOLLOW_LINKS);
                            FileLock probeLock = probe.tryLock()) {
                        assertNotNull(probeLock);
                    }
                });
    }

    private void assertPoisonedByAppend(Fault fault) throws Exception {
        Path root = directory("poison-append-" + fault.name());
        InertSettlementOperationStoreV1 store = fresh(root);
        CountingFileChannel channel = installChannel(store, fault);
        byte[] copiedKey = internalKey(store);
        OperationAppend candidate =
                append(
                        Operation.RESERVE_WALLET_INPUTS,
                        Status.RESERVED,
                        resultBody(Operation.RESERVE_WALLET_INPUTS, Status.RESERVED, hex(140)),
                        SettlementIdentityV1Test.GENERATION,
                        SettlementIdentityV1Test.validTarget(Operation.RESERVE_WALLET_INPUTS),
                        hex(141),
                        hex(142));
        assertAppendHeld(store, candidate, StoreHold.PERSISTENCE_UNAVAILABLE);
        assertFalse(channel.isOpen());
        assertTrue(allZero(copiedKey));
        long accesses = channel.accesses();
        assertAppendHeld(store, candidate, StoreHold.PERSISTENCE_UNAVAILABLE);
        assertEquals(
                StoreHold.PERSISTENCE_UNAVAILABLE,
                ((LookupHeld)
                                store.lookup(
                                        new OperationLookup(
                                                candidate.runtimeGenerationId(),
                                                candidate.targetContext(),
                                                candidate.semanticOperationId(),
                                                candidate.payloadDigest())))
                        .reason());
        assertEquals(
                StoreHold.PERSISTENCE_UNAVAILABLE,
                ((HandoverHeld)
                                store.acceptHandover(
                                        handover(SettlementIdentityV1Test.GENERATION, GENERATION_2)))
                        .reason());
        assertInstanceOf(SnapshotHeld.class, store.snapshot());
        assertEquals(accesses, channel.accesses());
        StoreCloseException close = assertThrows(StoreCloseException.class, store::close);
        assertEquals("Settlement store unavailable.", close.getMessage());
        assertNull(close.getCause());
        assertDoesNotThrow(store::close);
    }

    private void assertPoisonedByHandover(Fault fault) throws Exception {
        Path root = directory("poison-handover-" + fault.name());
        InertSettlementOperationStoreV1 store = fresh(root);
        CountingFileChannel channel = installChannel(store, fault);
        byte[] copiedKey = internalKey(store);
        assertHandoverHeld(
                store,
                handover(SettlementIdentityV1Test.GENERATION, GENERATION_2),
                StoreHold.PERSISTENCE_UNAVAILABLE);
        assertFalse(channel.isOpen());
        assertTrue(allZero(copiedKey));
        long accesses = channel.accesses();
        assertInstanceOf(SnapshotHeld.class, store.snapshot());
        assertEquals(accesses, channel.accesses());
        assertThrows(StoreCloseException.class, store::close);
        assertDoesNotThrow(store::close);
    }

    private static CountingFileChannel installChannel(
            InertSettlementOperationStoreV1 store, Fault fault) throws Exception {
        Field field = InertSettlementOperationStoreV1.class.getDeclaredField("channel");
        field.setAccessible(true);
        FileChannel original = (FileChannel) field.get(store);
        CountingFileChannel wrapper = new CountingFileChannel(original, fault);
        field.set(store, wrapper);
        return wrapper;
    }

    private static byte[] internalKey(InertSettlementOperationStoreV1 store) throws Exception {
        Field field = InertSettlementOperationStoreV1.class.getDeclaredField("authenticationKey");
        field.setAccessible(true);
        return (byte[]) field.get(store);
    }

    private static boolean allZero(byte[] value) {
        for (byte item : value) {
            if (item != 0) {
                return false;
            }
        }
        return true;
    }

    private InertSettlementOperationStoreV1 fresh(Path root) throws StoreOpenException {
        return InertSettlementOperationStoreV1.createFreshTemporaryProfile(
                root,
                KEY,
                LIMITS,
                SettlementIdentityV1Test.PROFILE,
                SettlementIdentityV1Test.GENERATION);
    }

    private void assertFactoryFailure(StoreLimits limits, FactoryFailure expected, String name)
            throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve(name));
        StoreOpenException exception =
                assertThrows(
                        StoreOpenException.class,
                        () ->
                                InertSettlementOperationStoreV1.createFreshTemporaryProfile(
                                        root,
                                        KEY,
                                        limits,
                                        SettlementIdentityV1Test.PROFILE,
                                        SettlementIdentityV1Test.GENERATION));
        assertEquals(expected, exception.reason());
        assertEquals("Settlement store unavailable.", exception.getMessage());
        assertNull(exception.getCause());
    }

    private static OperationAppend append(
            Operation operation,
            Status status,
            ResultBody body,
            String generation,
            TargetContext target,
            String semantic,
            String payload) {
        return appendAt(operation, status, body, generation, target, semantic, payload, "400");
    }

    private static OperationAppend appendAt(
            Operation operation,
            Status status,
            ResultBody body,
            String generation,
            TargetContext target,
            String semantic,
            String payload,
            String recordedAt) {
        ResultDigestInputs inputs =
                new ResultDigestInputs(operation, semantic, payload, status, recordedAt, body);
        ResultEnvelope envelope =
                new ResultEnvelope(
                        Schema.RESULT,
                        operation,
                        semantic,
                        payload,
                        status,
                        SettlementIdentityV1.resultDigest(inputs),
                        recordedAt,
                        body);
        return new OperationAppend(
                generation,
                target,
                operation,
                semantic,
                payload,
                status,
                envelope.resultDigest(),
                SettlementEnvelopeCodecV1.encodeResult(envelope));
    }

    private static ResultBody resultBody(OperationAppend append) {
        return SettlementEnvelopeCodecV1.decodeResult(append.canonicalResultEnvelope()).body();
    }

    private static GenerationHandover handover(String prior, String next) {
        GenerationHandover unsigned =
                new GenerationHandover(
                        SettlementIdentityV1Test.PROFILE,
                        prior,
                        next,
                        "41".repeat(32),
                        "00".repeat(32));
        return new GenerationHandover(
                unsigned.profileId(),
                prior,
                next,
                unsigned.desktopJournalHeadDigest(),
                SettlementIdentityV1.handoverDigest(unsigned));
    }

    private record TestFrame(
            int start,
            int recordStart,
            int recordEnd,
            int end,
            byte[] record,
            byte[] mac) {
        TestFrame {
            record = record.clone();
            mac = mac.clone();
        }

        @Override
        public byte[] record() {
            return record.clone();
        }

        @Override
        public byte[] mac() {
            return mac.clone();
        }
    }

    private enum Fault {
        NONE,
        WRITE,
        FORCE,
        CLOSE
    }

    private static final class CountingFileChannel extends FileChannel {
        private final FileChannel delegate;
        private final Fault fault;
        private long accesses;
        private long writes;
        private long forces;
        private boolean faultDelivered;

        private CountingFileChannel(FileChannel delegate, Fault fault) {
            this.delegate = delegate;
            this.fault = fault;
        }

        long accesses() {
            return accesses;
        }

        long writes() {
            return writes;
        }

        long forces() {
            return forces;
        }

        private void access() {
            accesses++;
        }

        private void fail(Fault point) throws IOException {
            if (!faultDelivered && fault == point) {
                faultDelivered = true;
                throw new IOException("synthetic");
            }
        }

        @Override
        public int read(ByteBuffer destination) throws IOException {
            access();
            return delegate.read(destination);
        }

        @Override
        public long read(ByteBuffer[] destinations, int offset, int length) throws IOException {
            access();
            return delegate.read(destinations, offset, length);
        }

        @Override
        public int write(ByteBuffer source) throws IOException {
            access();
            writes++;
            fail(Fault.WRITE);
            return delegate.write(source);
        }

        @Override
        public long write(ByteBuffer[] sources, int offset, int length) throws IOException {
            access();
            writes++;
            fail(Fault.WRITE);
            return delegate.write(sources, offset, length);
        }

        @Override
        public long position() throws IOException {
            access();
            return delegate.position();
        }

        @Override
        public FileChannel position(long newPosition) throws IOException {
            access();
            delegate.position(newPosition);
            return this;
        }

        @Override
        public long size() throws IOException {
            access();
            return delegate.size();
        }

        @Override
        public FileChannel truncate(long size) throws IOException {
            access();
            delegate.truncate(size);
            return this;
        }

        @Override
        public void force(boolean metadata) throws IOException {
            access();
            forces++;
            fail(Fault.FORCE);
            delegate.force(metadata);
        }

        @Override
        public long transferTo(long position, long count, WritableByteChannel target)
                throws IOException {
            access();
            return delegate.transferTo(position, count, target);
        }

        @Override
        public long transferFrom(ReadableByteChannel source, long position, long count)
                throws IOException {
            access();
            return delegate.transferFrom(source, position, count);
        }

        @Override
        public int read(ByteBuffer destination, long position) throws IOException {
            access();
            return delegate.read(destination, position);
        }

        @Override
        public int write(ByteBuffer source, long position) throws IOException {
            access();
            writes++;
            fail(Fault.WRITE);
            return delegate.write(source, position);
        }

        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
            access();
            return delegate.map(mode, position, size);
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) throws IOException {
            access();
            return delegate.lock(position, size, shared);
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) throws IOException {
            access();
            return delegate.tryLock(position, size, shared);
        }

        @Override
        protected void implCloseChannel() throws IOException {
            delegate.close();
            if (fault == Fault.CLOSE && !faultDelivered) {
                faultDelivered = true;
                throw new IOException("synthetic");
            }
        }
    }
}
