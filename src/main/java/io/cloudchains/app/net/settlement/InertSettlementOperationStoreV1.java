package io.cloudchains.app.net.settlement;

import static io.cloudchains.app.net.settlement.SettlementContractV1.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class InertSettlementOperationStoreV1 implements AutoCloseable {
    private static final String LOG_NAME = "xlite-settlement-inert-test-v1.log";
    private static final String FRAME_SCHEMA = "blocknet.xlite.settlement.inert-frame.v1";
    private static final byte[] FRAME_DOMAIN =
            "BLOCKNET-XLITE-SETTLEMENT-INERT-FRAME-V1\n"
                    .getBytes(StandardCharsets.US_ASCII);
    private static final int MAC_BYTES = 32;
    private static final int MAX_RECORD_BYTES = 262_144;
    private static final int ABSOLUTE_MAX_FRAMES = 1_024;
    private static final long ABSOLUTE_MAX_BYTES = 16_777_216L;

    private final Path root;
    private final Path logPath;
    private final byte[] authenticationKey;
    private final StoreLimits limits;
    private FileChannel channel;
    private FileLock lock;
    private String profileId;
    private String currentGenerationId;
    private boolean poisoned;
    private boolean poisonReported;
    private boolean closed;
    private boolean cleanupDone;
    private boolean keyZeroed;

    private InertSettlementOperationStoreV1(
            Path root,
            Path logPath,
            byte[] authenticationKey,
            StoreLimits limits,
            FileChannel channel,
            FileLock lock,
            String profileId,
            String currentGenerationId) {
        this.root = root;
        this.logPath = logPath;
        this.authenticationKey = authenticationKey;
        this.limits = limits;
        this.channel = channel;
        this.lock = lock;
        this.profileId = profileId;
        this.currentGenerationId = currentGenerationId;
    }

    static InertSettlementOperationStoreV1 createFreshTemporaryProfile(
            Path root,
            byte[] syntheticAuthenticationKey,
            StoreLimits limits,
            String profileId,
            String initialGenerationId)
            throws StoreOpenException {
        byte[] copiedKey = null;
        FileChannel channel = null;
        FileLock lock = null;
        try {
            Path validRoot = validateFreshRoot(root);
            copiedKey = copyAndValidateKey(syntheticAuthenticationKey);
            validateLimits(limits);
            requireHex64(profileId);
            requireHex64(initialGenerationId);
            ProfileInitialisationFrameRecord initial =
                    new ProfileInitialisationFrameRecord(
                            FRAME_SCHEMA,
                            "profile-initialisation",
                            profileId,
                            initialGenerationId);
            byte[] record = encodeFrameRecord(initial);
            long physicalBytes = physicalBytes(record.length);
            if (limits.maxFrames() < 1 || limits.maxBytes() < physicalBytes) {
                throw new OpenFailure(FactoryFailure.CAPACITY_UNAVAILABLE);
            }
            Path logPath = validRoot.resolve(LOG_NAME);
            channel =
                    FileChannel.open(
                            logPath,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS);
            lock = acquireLock(channel);
            byte[] mac = frameMac(copiedKey, new byte[MAC_BYTES], record);
            writeFrame(channel, record, mac);
            channel.force(true);
            return new InertSettlementOperationStoreV1(
                    validRoot,
                    logPath,
                    copiedKey,
                    limits,
                    channel,
                    lock,
                    profileId,
                    initialGenerationId);
        } catch (OpenFailure failure) {
            cleanupFactory(lock, channel, copiedKey);
            throw new StoreOpenException(failure.reason);
        } catch (OverlappingFileLockException exception) {
            cleanupFactory(lock, channel, copiedKey);
            throw new StoreOpenException(FactoryFailure.LOCK_UNAVAILABLE);
        } catch (IOException exception) {
            cleanupFactory(lock, channel, copiedKey);
            throw new StoreOpenException(FactoryFailure.PERSISTENCE_UNAVAILABLE);
        } catch (RuntimeException exception) {
            cleanupFactory(lock, channel, copiedKey);
            throw new StoreOpenException(FactoryFailure.INVALID_IDENTITY);
        }
    }

    static InertSettlementOperationStoreV1 reopenTemporaryProfile(
            Path root,
            byte[] syntheticAuthenticationKey,
            StoreLimits limits,
            List<OperationAppend> preparedStartupQuarantines)
            throws StoreOpenException {
        byte[] copiedKey = null;
        FileChannel channel = null;
        FileLock lock = null;
        try {
            Path validRoot = validateReopenRoot(root);
            copiedKey = copyAndValidateKey(syntheticAuthenticationKey);
            validateLimits(limits);
            List<OperationAppend> prepared = copyAndValidatePrepared(preparedStartupQuarantines);
            Path logPath = validRoot.resolve(LOG_NAME);
            channel =
                    FileChannel.open(
                            logPath,
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS);
            lock = acquireLock(channel);
            AuthenticatedState state = authenticate(channel, copiedKey, limits);
            validatePrepared(state, prepared);
            List<PreparedFrame> frames = preflightPrepared(state, prepared, copiedKey, limits);
            for (PreparedFrame frame : frames) {
                writeFrame(channel, frame.recordBytes, frame.mac);
            }
            if (!frames.isEmpty()) {
                channel.force(true);
            }
            return new InertSettlementOperationStoreV1(
                    validRoot,
                    logPath,
                    copiedKey,
                    limits,
                    channel,
                    lock,
                    state.profileId,
                    state.currentGenerationId);
        } catch (OpenFailure failure) {
            cleanupFactory(lock, channel, copiedKey);
            throw new StoreOpenException(failure.reason);
        } catch (OverlappingFileLockException exception) {
            cleanupFactory(lock, channel, copiedKey);
            throw new StoreOpenException(FactoryFailure.LOCK_UNAVAILABLE);
        } catch (IOException exception) {
            cleanupFactory(lock, channel, copiedKey);
            throw new StoreOpenException(FactoryFailure.PERSISTENCE_UNAVAILABLE);
        } catch (RuntimeException exception) {
            cleanupFactory(lock, channel, copiedKey);
            throw new StoreOpenException(FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        }
    }

    AppendOutcome append(OperationAppend append) {
        if (unavailable()) {
            return new AppendHeld(StoreHold.PERSISTENCE_UNAVAILABLE);
        }
        if (append == null) {
            return new AppendHeld(StoreHold.TRANSITION_DENIED);
        }
        if (!currentGenerationId.equals(append.runtimeGenerationId())) {
            return new AppendHeld(StoreHold.STALE_GENERATION);
        }
        final AuthenticatedState state;
        try {
            state = authenticate(channel, authenticationKey, limits);
        } catch (Exception exception) {
            return new AppendHeld(StoreHold.PERSISTENCE_UNAVAILABLE);
        }
        final ValidatedAppend candidate;
        try {
            candidate = validateAppend(append);
        } catch (RuntimeException exception) {
            return new AppendHeld(StoreHold.TRANSITION_DENIED);
        }
        if (!state.profileId.equals(append.targetContext().profileId())) {
            return new AppendHeld(StoreHold.TARGET_CONTEXT_MISMATCH);
        }
        OperationChain existing = state.operations.get(append.semanticOperationId());
        if (existing != null && !existing.payloadDigest.equals(append.payloadDigest())) {
            return new AppendHeld(StoreHold.PAYLOAD_CONFLICT);
        }
        if (existing != null && !existing.targetContext.equals(append.targetContext())) {
            return new AppendHeld(StoreHold.TARGET_CONTEXT_MISMATCH);
        }
        if (existing != null && Arrays.equals(existing.latestBytes, append.canonicalResultEnvelope())) {
            return new IdenticalReplay(existing.latestBytes);
        }
        if (!isAllowedTransition(existing, candidate.envelope)) {
            return new AppendHeld(StoreHold.TRANSITION_DENIED);
        }
        OperationFrameRecord frame = operationFrame(append);
        byte[] record;
        try {
            record = encodeFrameRecord(frame);
        } catch (RuntimeException exception) {
            return new AppendHeld(StoreHold.TRANSITION_DENIED);
        }
        if (!hasCapacity(state, record.length, limits)) {
            return new AppendHeld(StoreHold.CAPACITY_UNAVAILABLE);
        }
        try {
            byte[] mac = frameMac(authenticationKey, state.latestMac, record);
            writeFrame(channel, record, mac);
            channel.force(true);
            return new Appended(append.canonicalResultEnvelope());
        } catch (Exception exception) {
            poisonImmediately();
            return new AppendHeld(StoreHold.PERSISTENCE_UNAVAILABLE);
        }
    }

    LookupOutcome lookup(OperationLookup lookup) {
        if (unavailable()) {
            return new LookupHeld(StoreHold.PERSISTENCE_UNAVAILABLE);
        }
        if (lookup == null) {
            return new LookupHeld(StoreHold.NOT_FOUND);
        }
        if (!currentGenerationId.equals(lookup.runtimeGenerationId())) {
            return new LookupHeld(StoreHold.STALE_GENERATION);
        }
        final AuthenticatedState state;
        try {
            state = authenticate(channel, authenticationKey, limits);
            validateTargetContext(lookup.targetContext(), null);
            requireHex64(lookup.semanticOperationId());
            requireHex64(lookup.payloadDigest());
        } catch (Exception exception) {
            return new LookupHeld(StoreHold.PERSISTENCE_UNAVAILABLE);
        }
        if (!state.profileId.equals(lookup.targetContext().profileId())) {
            return new LookupHeld(StoreHold.TARGET_CONTEXT_MISMATCH);
        }
        OperationChain chain = state.operations.get(lookup.semanticOperationId());
        if (chain == null) {
            return new LookupHeld(StoreHold.NOT_FOUND);
        }
        if (!chain.payloadDigest.equals(lookup.payloadDigest())) {
            return new LookupHeld(StoreHold.PAYLOAD_CONFLICT);
        }
        if (!chain.targetContext.equals(lookup.targetContext())) {
            return new LookupHeld(StoreHold.TARGET_CONTEXT_MISMATCH);
        }
        return new Found(chain.latestBytes);
    }

    HandoverOutcome acceptHandover(GenerationHandover handover) {
        if (unavailable()) {
            return new HandoverHeld(StoreHold.PERSISTENCE_UNAVAILABLE);
        }
        if (handover == null) {
            return new HandoverHeld(StoreHold.STALE_OR_CONFLICTING_HANDOVER);
        }
        final AuthenticatedState state;
        try {
            state = authenticate(channel, authenticationKey, limits);
        } catch (Exception exception) {
            return new HandoverHeld(StoreHold.PERSISTENCE_UNAVAILABLE);
        }
        try {
            validateHandover(handover);
        } catch (RuntimeException exception) {
            return new HandoverHeld(StoreHold.STALE_OR_CONFLICTING_HANDOVER);
        }
        if (!profileId.equals(handover.profileId())
                || !currentGenerationId.equals(handover.priorGenerationId())
                || currentGenerationId.equals(handover.nextGenerationId())
                || state.seenGenerations.contains(handover.nextGenerationId())) {
            return new HandoverHeld(StoreHold.STALE_OR_CONFLICTING_HANDOVER);
        }
        GenerationHandoverFrameRecord frame =
                new GenerationHandoverFrameRecord(
                        FRAME_SCHEMA,
                        "generation-handover",
                        handover.profileId(),
                        handover.priorGenerationId(),
                        handover.nextGenerationId(),
                        handover.desktopJournalHeadDigest(),
                        handover.handoverDigest());
        byte[] record = encodeFrameRecord(frame);
        if (!hasCapacity(state, record.length, limits)) {
            return new HandoverHeld(StoreHold.CAPACITY_UNAVAILABLE);
        }
        try {
            byte[] mac = frameMac(authenticationKey, state.latestMac, record);
            writeFrame(channel, record, mac);
            channel.force(true);
            currentGenerationId = handover.nextGenerationId();
            return new HandoverAccepted(currentGenerationId);
        } catch (Exception exception) {
            poisonImmediately();
            return new HandoverHeld(StoreHold.PERSISTENCE_UNAVAILABLE);
        }
    }

    SnapshotOutcome snapshot() {
        if (unavailable()) {
            return new SnapshotHeld(StoreHold.PERSISTENCE_UNAVAILABLE);
        }
        try {
            AuthenticatedState state = authenticate(channel, authenticationKey, limits);
            return new SnapshotAvailable(
                    new StoreSnapshot(
                            state.profileId,
                            state.currentGenerationId,
                            state.frameCount,
                            state.byteCount,
                            state.operations.size(),
                            HexFormat.of().formatHex(state.latestMac)));
        } catch (Exception exception) {
            return new SnapshotHeld(StoreHold.PERSISTENCE_UNAVAILABLE);
        }
    }

    @Override
    public void close() throws StoreCloseException {
        if (poisoned) {
            if (!poisonReported) {
                poisonReported = true;
                closed = true;
                throw new StoreCloseException();
            }
            return;
        }
        if (closed) {
            return;
        }
        boolean failed = false;
        try {
            channel.force(true);
        } catch (Exception exception) {
            failed = true;
        }
        try {
            cleanupResources();
        } catch (RuntimeException exception) {
            failed = true;
        } finally {
            closed = true;
        }
        if (failed) {
            throw new StoreCloseException();
        }
    }

    private boolean unavailable() {
        return poisoned || closed || cleanupDone;
    }

    private void poisonImmediately() {
        poisoned = true;
        try {
            cleanupResources();
        } catch (RuntimeException ignored) {
            // Poison remains permanent and the public outcome is constant.
        }
    }

    private void cleanupResources() {
        if (cleanupDone) {
            return;
        }
        cleanupDone = true;
        RuntimeException failure = null;
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException exception) {
            failure = new IllegalStateException();
        }
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException exception) {
            failure = new IllegalStateException();
        } finally {
            zeroKey();
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void zeroKey() {
        if (!keyZeroed) {
            Arrays.fill(authenticationKey, (byte) 0);
            keyZeroed = true;
        }
    }

    private static Path validateFreshRoot(Path root) throws OpenFailure {
        Path valid = validateRoot(root);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(valid)) {
            if (entries.iterator().hasNext()) {
                throw new OpenFailure(FactoryFailure.INVALID_ROOT);
            }
        } catch (IOException exception) {
            throw new OpenFailure(FactoryFailure.INVALID_ROOT);
        }
        if (Files.exists(valid.resolve(LOG_NAME), LinkOption.NOFOLLOW_LINKS)) {
            throw new OpenFailure(FactoryFailure.INVALID_ROOT);
        }
        return valid;
    }

    private static Path validateReopenRoot(Path root) throws OpenFailure {
        Path valid = validateRoot(root);
        Path expected = valid.resolve(LOG_NAME);
        int count = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(valid)) {
            for (Path entry : entries) {
                count++;
                if (!entry.getFileName().equals(Path.of(LOG_NAME))) {
                    throw new OpenFailure(FactoryFailure.INVALID_ROOT);
                }
            }
        } catch (IOException exception) {
            throw new OpenFailure(FactoryFailure.INVALID_ROOT);
        }
        if (count != 1
                || Files.isSymbolicLink(expected)
                || !Files.isRegularFile(expected, LinkOption.NOFOLLOW_LINKS)) {
            throw new OpenFailure(FactoryFailure.INVALID_ROOT);
        }
        return valid;
    }

    private static Path validateRoot(Path root) throws OpenFailure {
        if (root == null || !root.isAbsolute()) {
            throw new OpenFailure(FactoryFailure.INVALID_ROOT);
        }
        Path normal = root.normalize();
        if (!normal.equals(root) || !Files.isDirectory(normal, LinkOption.NOFOLLOW_LINKS)) {
            throw new OpenFailure(FactoryFailure.INVALID_ROOT);
        }
        Path current = normal.getRoot();
        for (Path component : normal) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new OpenFailure(FactoryFailure.INVALID_ROOT);
            }
        }
        return normal;
    }

    private static byte[] copyAndValidateKey(byte[] key) throws OpenFailure {
        if (key == null || key.length != 32) {
            throw new OpenFailure(FactoryFailure.INVALID_KEY);
        }
        return key.clone();
    }

    private static void validateLimits(StoreLimits limits) throws OpenFailure {
        if (limits == null
                || limits.maxFrames() < 1
                || limits.maxFrames() > ABSOLUTE_MAX_FRAMES
                || limits.maxBytes() < 1
                || limits.maxBytes() > ABSOLUTE_MAX_BYTES) {
            throw new OpenFailure(FactoryFailure.INVALID_LIMITS);
        }
    }

    private static FileLock acquireLock(FileChannel channel) throws IOException, OpenFailure {
        FileLock lock = channel.tryLock();
        if (lock == null) {
            throw new OpenFailure(FactoryFailure.LOCK_UNAVAILABLE);
        }
        return lock;
    }

    private static void cleanupFactory(FileLock lock, FileChannel channel, byte[] key) {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException ignored) {
            // The checked factory result is intentionally constant.
        }
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException ignored) {
            // The checked factory result is intentionally constant.
        }
        if (key != null) {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static List<OperationAppend> copyAndValidatePrepared(List<OperationAppend> input)
            throws OpenFailure {
        if (input == null) {
            throw new OpenFailure(FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        }
        final List<OperationAppend> copied;
        try {
            copied = List.copyOf(input);
            for (OperationAppend append : copied) {
                ValidatedAppend validated = validateAppend(append);
                if (append.operation() != Operation.BROADCAST_SETTLEMENT_STAGE
                        || append.status() != Status.QUARANTINED
                        || !(validated.envelope.body()
                                instanceof BroadcastQuarantinedResult quarantine)
                        || quarantine.quarantineCode()
                                != QuarantineCode.AMBIGUOUS_OUTCOME_QUARANTINED) {
                    throw new IllegalArgumentException();
                }
            }
        } catch (RuntimeException exception) {
            throw new OpenFailure(FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        }
        return copied;
    }

    private static void validatePrepared(
            AuthenticatedState state, List<OperationAppend> prepared) throws OpenFailure {
        List<OperationChain> expected =
                state.operations.values().stream()
                        .filter(
                                chain ->
                                        chain.operation == Operation.BROADCAST_SETTLEMENT_STAGE
                                                && chain.latestStatus
                                                        == Status.BROADCAST_DISPATCH_RECORDED)
                        .sorted(
                                Comparator.comparing((OperationChain value) -> value.semanticOperationId)
                                        .thenComparing(value -> value.payloadDigest))
                        .toList();
        if (expected.size() != prepared.size()) {
            throw new OpenFailure(FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
        }
        for (int index = 0; index < expected.size(); index++) {
            OperationChain chain = expected.get(index);
            OperationAppend append = prepared.get(index);
            ValidatedAppend validated = validateAppend(append);
            if (!append.runtimeGenerationId().equals(state.currentGenerationId)
                    || !append.semanticOperationId().equals(chain.semanticOperationId)
                    || !append.payloadDigest().equals(chain.payloadDigest)
                    || !append.targetContext().equals(chain.targetContext)
                    || !sameBroadcastIdentity(chain.latestEnvelope.body(), validated.envelope.body())
                    || !isAllowedTransition(chain, validated.envelope)) {
                throw new OpenFailure(FactoryFailure.PREPARED_QUARANTINE_MISMATCH);
            }
        }
    }

    private static List<PreparedFrame> preflightPrepared(
            AuthenticatedState state,
            List<OperationAppend> prepared,
            byte[] key,
            StoreLimits limits)
            throws OpenFailure {
        List<PreparedFrame> frames = new ArrayList<>();
        byte[] previous = state.latestMac.clone();
        long bytes = state.byteCount;
        int count = state.frameCount;
        for (OperationAppend append : prepared) {
            byte[] record = encodeFrameRecord(operationFrame(append));
            count++;
            bytes += physicalBytes(record.length);
            if (count > limits.maxFrames()
                    || count > ABSOLUTE_MAX_FRAMES
                    || bytes > limits.maxBytes()
                    || bytes > ABSOLUTE_MAX_BYTES) {
                throw new OpenFailure(FactoryFailure.CAPACITY_UNAVAILABLE);
            }
            byte[] mac = frameMac(key, previous, record);
            frames.add(new PreparedFrame(record, mac));
            previous = mac;
        }
        return List.copyOf(frames);
    }

    private static AuthenticatedState authenticate(
            FileChannel channel, byte[] key, StoreLimits limits) throws OpenFailure {
        final long size;
        try {
            size = channel.size();
        } catch (IOException exception) {
            throw new OpenFailure(FactoryFailure.PERSISTENCE_UNAVAILABLE);
        }
        if (size <= 0 || size > ABSOLUTE_MAX_BYTES || size > Integer.MAX_VALUE) {
            throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
        }
        byte[] file = new byte[(int) size];
        try {
            ByteBuffer buffer = ByteBuffer.wrap(file);
            channel.position(0);
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer);
                if (read < 0) {
                    throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
                }
            }
        } catch (IOException exception) {
            throw new OpenFailure(FactoryFailure.PERSISTENCE_UNAVAILABLE);
        }
        int offset = 0;
        int frameCount = 0;
        byte[] previousMac = new byte[MAC_BYTES];
        String profile = null;
        String generation = null;
        Set<String> seenGenerations = new HashSet<>();
        Map<String, OperationChain> operations = new LinkedHashMap<>();
        while (offset < file.length) {
            if (file.length - offset < 4) {
                throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
            }
            int length = ByteBuffer.wrap(file, offset, 4).getInt();
            offset += 4;
            if (length <= 0 || length > MAX_RECORD_BYTES || file.length - offset < length + MAC_BYTES) {
                throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
            }
            byte[] record = Arrays.copyOfRange(file, offset, offset + length);
            offset += length;
            byte[] storedMac = Arrays.copyOfRange(file, offset, offset + MAC_BYTES);
            offset += MAC_BYTES;
            byte[] expectedMac = frameMac(key, previousMac, record);
            if (!MessageDigest.isEqual(storedMac, expectedMac)) {
                throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
            }
            previousMac = storedMac;
            frameCount++;
            if (frameCount > ABSOLUTE_MAX_FRAMES) {
                throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
            }
            FrameRecord frame = decodeFrameRecord(record);
            if (frameCount == 1) {
                if (!(frame instanceof ProfileInitialisationFrameRecord initial)) {
                    throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
                }
                profile = initial.profileId();
                generation = initial.initialGenerationId();
                seenGenerations.add(generation);
                continue;
            }
            if (frame instanceof ProfileInitialisationFrameRecord) {
                throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
            }
            if (frame instanceof GenerationHandoverFrameRecord handover) {
                GenerationHandover value =
                        new GenerationHandover(
                                handover.profileId(),
                                handover.priorGenerationId(),
                                handover.nextGenerationId(),
                                handover.desktopJournalHeadDigest(),
                                handover.handoverDigest());
                try {
                    validateHandover(value);
                } catch (RuntimeException exception) {
                    throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
                }
                if (!profile.equals(value.profileId())
                        || !generation.equals(value.priorGenerationId())
                        || generation.equals(value.nextGenerationId())
                        || !seenGenerations.add(value.nextGenerationId())) {
                    throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
                }
                generation = value.nextGenerationId();
                continue;
            }
            OperationFrameRecord operationFrame = (OperationFrameRecord) frame;
            if (!generation.equals(operationFrame.runtimeGenerationId())
                    || !profile.equals(operationFrame.targetContext().profileId())) {
                throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
            }
            byte[] envelopeBytes;
            try {
                envelopeBytes = Base64.getDecoder().decode(operationFrame.resultEnvelopeBase64());
                if (!Base64.getEncoder()
                        .encodeToString(envelopeBytes)
                        .equals(operationFrame.resultEnvelopeBase64())) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException exception) {
                throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
            }
            OperationAppend append =
                    new OperationAppend(
                            operationFrame.runtimeGenerationId(),
                            operationFrame.targetContext(),
                            operationFrame.operation(),
                            operationFrame.semanticOperationId(),
                            operationFrame.payloadDigest(),
                            operationFrame.status(),
                            operationFrame.resultDigest(),
                            envelopeBytes);
            final ValidatedAppend validated;
            try {
                validated = validateAppend(append);
            } catch (RuntimeException exception) {
                throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
            }
            OperationChain prior = operations.get(append.semanticOperationId());
            if (prior != null
                    && (!prior.payloadDigest.equals(append.payloadDigest())
                            || !prior.targetContext.equals(append.targetContext()))) {
                throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
            }
            if (!isAllowedTransition(prior, validated.envelope)) {
                throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
            }
            operations.put(
                    append.semanticOperationId(),
                    new OperationChain(
                            append.semanticOperationId(),
                            append.payloadDigest(),
                            append.targetContext(),
                            append.operation(),
                            append.status(),
                            envelopeBytes,
                            validated.envelope));
        }
        if (profile == null || offset != file.length) {
            throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
        }
        if (frameCount > limits.maxFrames() || size > limits.maxBytes()) {
            throw new OpenFailure(FactoryFailure.CAPACITY_UNAVAILABLE);
        }
        return new AuthenticatedState(
                profile,
                generation,
                frameCount,
                size,
                previousMac,
                Map.copyOf(operations),
                Set.copyOf(seenGenerations));
    }

    private static ValidatedAppend validateAppend(OperationAppend append) {
        if (append == null || append.operation() == Operation.GET_SETTLEMENT_OPERATION_RESULT) {
            throw new IllegalArgumentException();
        }
        requireHex64(append.runtimeGenerationId());
        validateTargetContext(append.targetContext(), append.operation());
        requireHex64(append.semanticOperationId());
        requireHex64(append.payloadDigest());
        requireHex64(append.resultDigest());
        ResultEnvelope envelope =
                SettlementEnvelopeCodecV1.decodeResult(append.canonicalResultEnvelope());
        if (envelope.operation() != append.operation()
                || !envelope.semanticOperationId().equals(append.semanticOperationId())
                || !envelope.payloadDigest().equals(append.payloadDigest())
                || envelope.status() != append.status()
                || !envelope.resultDigest().equals(append.resultDigest())) {
            throw new IllegalArgumentException();
        }
        return new ValidatedAppend(envelope);
    }

    private static void validateTargetContext(TargetContext context, Operation operation) {
        if (context == null) {
            throw new IllegalArgumentException();
        }
        requireHex64(context.profileId());
        boolean empty =
                context.parentIntentId().isEmpty()
                        && context.childStageId().isEmpty()
                        && context.role().isEmpty()
                        && context.stage().isEmpty();
        boolean present =
                !context.parentIntentId().isEmpty()
                        && !context.childStageId().isEmpty()
                        && !context.role().isEmpty()
                        && !context.stage().isEmpty();
        if (!empty && !present) {
            throw new IllegalArgumentException();
        }
        if (operation == Operation.CHECK_OWNED_ADDRESS && !empty) {
            throw new IllegalArgumentException();
        }
        if (operation != null && operation != Operation.CHECK_OWNED_ADDRESS && !present) {
            throw new IllegalArgumentException();
        }
        if (present) {
            requireHex64(context.parentIntentId());
            requireHex64(context.childStageId());
            parseRole(context.role());
            parseStage(context.stage());
        }
        validateChainIdentity(context.chainIdentity(), context.asset());
        requireHex64(context.policyDigest());
    }

    private static void validateChainIdentity(ChainIdentity chain, Asset expected) {
        if (chain == null || chain.asset() != expected || !"mainnet".equals(chain.network())) {
            throw new IllegalArgumentException();
        }
        requireHex64(chain.genesisHash());
        requireHex8(chain.networkMagicHex());
        requireU8(chain.p2pkhVersion());
        if (chain.p2shVersions().isEmpty() || chain.p2shVersions().size() > 8) {
            throw new IllegalArgumentException();
        }
        int previous = -1;
        for (String version : chain.p2shVersions()) {
            int current = Integer.parseInt(requireU8(version));
            if (current <= previous) {
                throw new IllegalArgumentException();
            }
            previous = current;
        }
        requireEvenHex(chain.signedMessagePrefixHex());
        requirePositiveU64(chain.baseUnitsPerCoin());
        requirePositiveU32(chain.minimumConfirmations());
        requireHex64(chain.chainPolicyDigest());
    }

    private static void validateHandover(GenerationHandover handover) {
        requireHex64(handover.profileId());
        requireHex64(handover.priorGenerationId());
        requireHex64(handover.nextGenerationId());
        requireHex64(handover.desktopJournalHeadDigest());
        requireHex64(handover.handoverDigest());
        if (!SettlementIdentityV1.handoverDigest(handover).equals(handover.handoverDigest())) {
            throw new IllegalArgumentException();
        }
    }

    private static boolean isAllowedTransition(OperationChain prior, ResultEnvelope candidate) {
        if (prior == null) {
            return switch (candidate.operation()) {
                case RESERVE_WALLET_INPUTS ->
                        candidate.status() == Status.RESERVED
                                || candidate.status() == Status.QUARANTINED;
                case PROVE_RESERVED_UTXO_OWNERSHIP ->
                        candidate.status() == Status.PROOF_CREATED
                                || candidate.status() == Status.QUARANTINED;
                case CHECK_OWNED_ADDRESS ->
                        candidate.status() == Status.OWNERSHIP_CHECKED
                                || candidate.status() == Status.QUARANTINED;
                case SIGN_SETTLEMENT_STAGE ->
                        candidate.status() == Status.SIGNED
                                || candidate.status() == Status.QUARANTINED;
                case BROADCAST_SETTLEMENT_STAGE ->
                        candidate.status() == Status.BROADCAST_DISPATCH_RECORDED;
                case GET_SETTLEMENT_OPERATION_RESULT -> false;
            };
        }
        if (prior.operation != candidate.operation()) {
            return false;
        }
        if (prior.operation == Operation.BROADCAST_SETTLEMENT_STAGE
                && !sameBroadcastIdentity(prior.latestEnvelope.body(), candidate.body())) {
            return false;
        }
        return switch (prior.operation) {
            case RESERVE_WALLET_INPUTS ->
                    prior.latestStatus == Status.QUARANTINED
                            && candidate.status() == Status.RESERVED;
            case PROVE_RESERVED_UTXO_OWNERSHIP ->
                    prior.latestStatus == Status.QUARANTINED
                            && candidate.status() == Status.PROOF_CREATED;
            case CHECK_OWNED_ADDRESS ->
                    prior.latestStatus == Status.QUARANTINED
                            && candidate.status() == Status.OWNERSHIP_CHECKED;
            case SIGN_SETTLEMENT_STAGE ->
                    prior.latestStatus == Status.QUARANTINED
                            && candidate.status() == Status.SIGNED;
            case BROADCAST_SETTLEMENT_STAGE ->
                    (prior.latestStatus == Status.BROADCAST_DISPATCH_RECORDED
                                    && (candidate.status() == Status.BROADCAST_SUCCEEDED
                                            || candidate.status() == Status.BROADCAST_REJECTED
                                            || candidate.status() == Status.QUARANTINED))
                            || (prior.latestStatus == Status.QUARANTINED
                                    && (candidate.status() == Status.BROADCAST_SUCCEEDED
                                            || candidate.status() == Status.BROADCAST_REJECTED));
            case GET_SETTLEMENT_OPERATION_RESULT -> false;
        };
    }

    private static boolean sameBroadcastIdentity(ResultBody left, ResultBody right) {
        BroadcastIdentity leftIdentity = broadcastIdentity(left);
        BroadcastIdentity rightIdentity = broadcastIdentity(right);
        return leftIdentity != null && leftIdentity.equals(rightIdentity);
    }

    private static BroadcastIdentity broadcastIdentity(ResultBody body) {
        if (body instanceof BroadcastDispatchRecordedResult value) {
            return new BroadcastIdentity(
                    value.finalBytesDigest(), value.finalTxid(), value.finalWtxid());
        }
        if (body instanceof BroadcastSucceededResult value) {
            return new BroadcastIdentity(
                    value.finalBytesDigest(), value.finalTxid(), value.finalWtxid());
        }
        if (body instanceof BroadcastRejectedResult value) {
            return new BroadcastIdentity(
                    value.finalBytesDigest(), value.finalTxid(), value.finalWtxid());
        }
        if (body instanceof BroadcastQuarantinedResult value) {
            return new BroadcastIdentity(
                    value.finalBytesDigest(), value.finalTxid(), value.finalWtxid());
        }
        return null;
    }

    private static OperationFrameRecord operationFrame(OperationAppend append) {
        return new OperationFrameRecord(
                FRAME_SCHEMA,
                "operation-append",
                append.runtimeGenerationId(),
                append.targetContext(),
                append.operation(),
                append.semanticOperationId(),
                append.payloadDigest(),
                append.status(),
                append.resultDigest(),
                Base64.getEncoder().encodeToString(append.canonicalResultEnvelope()));
    }

    private static boolean hasCapacity(
            AuthenticatedState state, int nextRecordLength, StoreLimits limits) {
        return state.frameCount + 1 <= limits.maxFrames()
                && state.frameCount + 1 <= ABSOLUTE_MAX_FRAMES
                && state.byteCount + physicalBytes(nextRecordLength) <= limits.maxBytes()
                && state.byteCount + physicalBytes(nextRecordLength) <= ABSOLUTE_MAX_BYTES;
    }

    private static long physicalBytes(int recordLength) {
        if (recordLength <= 0 || recordLength > MAX_RECORD_BYTES) {
            throw new IllegalArgumentException();
        }
        return 4L + recordLength + MAC_BYTES;
    }

    private static void writeFrame(FileChannel channel, byte[] record, byte[] mac)
            throws IOException {
        ByteBuffer length = ByteBuffer.allocate(4).putInt(record.length);
        length.flip();
        channel.position(channel.size());
        writeFully(channel, length);
        writeFully(channel, ByteBuffer.wrap(record));
        writeFully(channel, ByteBuffer.wrap(mac));
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.write(buffer) <= 0) {
                throw new IOException();
            }
        }
    }

    private static byte[] frameMac(byte[] key, byte[] previousMac, byte[] record) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(FRAME_DOMAIN);
            mac.update(previousMac);
            mac.update(ByteBuffer.allocate(4).putInt(record.length).array());
            mac.update(record);
            return mac.doFinal();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] encodeFrameRecord(FrameRecord record) {
        JsonObject object = new JsonObject();
        if (record instanceof ProfileInitialisationFrameRecord value) {
            object.addProperty("schema", value.schema());
            object.addProperty("frameType", value.frameType());
            object.addProperty("profileId", value.profileId());
            object.addProperty("initialGenerationId", value.initialGenerationId());
        } else if (record instanceof OperationFrameRecord value) {
            object.addProperty("schema", value.schema());
            object.addProperty("frameType", value.frameType());
            object.addProperty("runtimeGenerationId", value.runtimeGenerationId());
            object.add("targetContext", encodeTargetContext(value.targetContext()));
            object.addProperty("operation", value.operation().wireValue());
            object.addProperty("semanticOperationId", value.semanticOperationId());
            object.addProperty("payloadDigest", value.payloadDigest());
            object.addProperty("status", value.status().wireValue());
            object.addProperty("resultDigest", value.resultDigest());
            object.addProperty("resultEnvelopeBase64", value.resultEnvelopeBase64());
        } else if (record instanceof GenerationHandoverFrameRecord value) {
            object.addProperty("schema", value.schema());
            object.addProperty("frameType", value.frameType());
            object.addProperty("profileId", value.profileId());
            object.addProperty("priorGenerationId", value.priorGenerationId());
            object.addProperty("nextGenerationId", value.nextGenerationId());
            object.addProperty("desktopJournalHeadDigest", value.desktopJournalHeadDigest());
            object.addProperty("handoverDigest", value.handoverDigest());
        } else {
            throw new IllegalArgumentException();
        }
        return SettlementCanonicalJsonV1.encode(object);
    }

    private static FrameRecord decodeFrameRecord(byte[] record) throws OpenFailure {
        try {
            JsonObject object = requireObject(SettlementCanonicalJsonV1.parse(record));
            String schema = requireString(object, "schema");
            if (!FRAME_SCHEMA.equals(schema)) {
                throw new IllegalArgumentException();
            }
            String type = requireString(object, "frameType");
            return switch (type) {
                case "profile-initialisation" -> {
                    requireExactKeys(
                            object, "schema", "frameType", "profileId", "initialGenerationId");
                    yield new ProfileInitialisationFrameRecord(
                            schema,
                            type,
                            requireHex64(requireString(object, "profileId")),
                            requireHex64(requireString(object, "initialGenerationId")));
                }
                case "operation-append" -> {
                    requireExactKeys(
                            object,
                            "schema",
                            "frameType",
                            "runtimeGenerationId",
                            "targetContext",
                            "operation",
                            "semanticOperationId",
                            "payloadDigest",
                            "status",
                            "resultDigest",
                            "resultEnvelopeBase64");
                    Operation operation = parseOperation(requireString(object, "operation"));
                    if (operation == Operation.GET_SETTLEMENT_OPERATION_RESULT) {
                        throw new IllegalArgumentException();
                    }
                    TargetContext target =
                            decodeTargetContext(requireObject(object.get("targetContext")), operation);
                    String base64 = requireString(object, "resultEnvelopeBase64");
                    byte[] decoded = Base64.getDecoder().decode(base64);
                    if (!Base64.getEncoder().encodeToString(decoded).equals(base64)) {
                        throw new IllegalArgumentException();
                    }
                    yield new OperationFrameRecord(
                            schema,
                            type,
                            requireHex64(requireString(object, "runtimeGenerationId")),
                            target,
                            operation,
                            requireHex64(requireString(object, "semanticOperationId")),
                            requireHex64(requireString(object, "payloadDigest")),
                            parseStatus(requireString(object, "status")),
                            requireHex64(requireString(object, "resultDigest")),
                            base64);
                }
                case "generation-handover" -> {
                    requireExactKeys(
                            object,
                            "schema",
                            "frameType",
                            "profileId",
                            "priorGenerationId",
                            "nextGenerationId",
                            "desktopJournalHeadDigest",
                            "handoverDigest");
                    yield new GenerationHandoverFrameRecord(
                            schema,
                            type,
                            requireHex64(requireString(object, "profileId")),
                            requireHex64(requireString(object, "priorGenerationId")),
                            requireHex64(requireString(object, "nextGenerationId")),
                            requireHex64(requireString(object, "desktopJournalHeadDigest")),
                            requireHex64(requireString(object, "handoverDigest")));
                }
                default -> throw new IllegalArgumentException();
            };
        } catch (RuntimeException exception) {
            throw new OpenFailure(FactoryFailure.AUTHENTICATION_FAILED);
        }
    }

    private static JsonObject encodeTargetContext(TargetContext context) {
        JsonObject object = new JsonObject();
        object.addProperty("profileId", context.profileId());
        object.addProperty("parentIntentId", context.parentIntentId());
        object.addProperty("childStageId", context.childStageId());
        object.addProperty("role", context.role());
        object.addProperty("stage", context.stage());
        object.addProperty("asset", context.asset().wireValue());
        object.add("chainIdentity", encodeChainIdentity(context.chainIdentity()));
        object.addProperty("policyDigest", context.policyDigest());
        return object;
    }

    private static TargetContext decodeTargetContext(JsonObject object, Operation operation) {
        requireExactKeys(
                object,
                "profileId",
                "parentIntentId",
                "childStageId",
                "role",
                "stage",
                "asset",
                "chainIdentity",
                "policyDigest");
        Asset asset = parseAsset(requireString(object, "asset"));
        TargetContext context =
                new TargetContext(
                        requireHex64(requireString(object, "profileId")),
                        requireString(object, "parentIntentId"),
                        requireString(object, "childStageId"),
                        requireString(object, "role"),
                        requireString(object, "stage"),
                        asset,
                        decodeChainIdentity(requireObject(object.get("chainIdentity")), asset),
                        requireHex64(requireString(object, "policyDigest")));
        validateTargetContext(context, operation);
        return context;
    }

    private static JsonObject encodeChainIdentity(ChainIdentity chain) {
        JsonObject object = new JsonObject();
        object.addProperty("asset", chain.asset().wireValue());
        object.addProperty("network", chain.network());
        object.addProperty("genesisHash", chain.genesisHash());
        object.addProperty("networkMagicHex", chain.networkMagicHex());
        object.addProperty("p2pkhVersion", chain.p2pkhVersion());
        JsonArray versions = new JsonArray();
        chain.p2shVersions().forEach(versions::add);
        object.add("p2shVersions", versions);
        object.addProperty("signedMessagePrefixHex", chain.signedMessagePrefixHex());
        object.addProperty("baseUnitsPerCoin", chain.baseUnitsPerCoin());
        object.addProperty("minimumConfirmations", chain.minimumConfirmations());
        object.addProperty("chainPolicyDigest", chain.chainPolicyDigest());
        return object;
    }

    private static ChainIdentity decodeChainIdentity(JsonObject object, Asset asset) {
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
        if (parseAsset(requireString(object, "asset")) != asset
                || !"mainnet".equals(requireString(object, "network"))) {
            throw new IllegalArgumentException();
        }
        JsonArray versionsArray = requireArray(object, "p2shVersions");
        if (versionsArray.isEmpty() || versionsArray.size() > 8) {
            throw new IllegalArgumentException();
        }
        List<String> versions = new ArrayList<>();
        int prior = -1;
        for (JsonElement element : versionsArray) {
            int numeric = Integer.parseInt(requireU8(requireString(element)));
            if (numeric <= prior) {
                throw new IllegalArgumentException();
            }
            prior = numeric;
            versions.add(Integer.toString(numeric));
        }
        return new ChainIdentity(
                asset,
                "mainnet",
                requireHex64(requireString(object, "genesisHash")),
                requireHex8(requireString(object, "networkMagicHex")),
                requireU8(requireString(object, "p2pkhVersion")),
                versions,
                requireEvenHex(requireString(object, "signedMessagePrefixHex")),
                requirePositiveU64(requireString(object, "baseUnitsPerCoin")),
                requirePositiveU32(requireString(object, "minimumConfirmations")),
                requireHex64(requireString(object, "chainPolicyDigest")));
    }

    private static void requireExactKeys(JsonObject object, String... keys) {
        if (!object.keySet().equals(Set.of(keys))) {
            throw new IllegalArgumentException();
        }
    }

    private static JsonObject requireObject(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException();
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException();
        }
        return element.getAsJsonArray();
    }

    private static String requireString(JsonObject object, String key) {
        return requireString(object.get(key));
    }

    private static String requireString(JsonElement element) {
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException();
        }
        return element.getAsString();
    }

    private static String requireHex8(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}")) {
            throw new IllegalArgumentException();
        }
        return value;
    }

    private static String requireHex64(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException();
        }
        return value;
    }

    private static String requireEvenHex(String value) {
        if (value == null || value.isEmpty() || (value.length() & 1) != 0
                || !value.matches("[0-9a-f]+")) {
            throw new IllegalArgumentException();
        }
        return value;
    }

    private static String requireU8(String value) {
        return requireUnsigned(value, BigInteger.valueOf(255), false);
    }

    private static String requirePositiveU32(String value) {
        return requireUnsigned(value, new BigInteger("4294967295"), true);
    }

    private static String requirePositiveU64(String value) {
        return requireUnsigned(value, new BigInteger("18446744073709551615"), true);
    }

    private static String requireUnsigned(String value, BigInteger max, boolean positive) {
        if (value == null || !value.matches("0|[1-9][0-9]*")) {
            throw new IllegalArgumentException();
        }
        BigInteger parsed = new BigInteger(value);
        if (parsed.compareTo(max) > 0 || (positive && parsed.signum() == 0)) {
            throw new IllegalArgumentException();
        }
        return value;
    }

    private static Operation parseOperation(String value) {
        return switch (value) {
            case "reserveWalletInputs" -> Operation.RESERVE_WALLET_INPUTS;
            case "proveReservedUtxoOwnership" -> Operation.PROVE_RESERVED_UTXO_OWNERSHIP;
            case "checkOwnedAddress" -> Operation.CHECK_OWNED_ADDRESS;
            case "signSettlementStage" -> Operation.SIGN_SETTLEMENT_STAGE;
            case "broadcastSettlementStage" -> Operation.BROADCAST_SETTLEMENT_STAGE;
            case "getSettlementOperationResult" -> Operation.GET_SETTLEMENT_OPERATION_RESULT;
            default -> throw new IllegalArgumentException();
        };
    }

    private static Status parseStatus(String value) {
        for (Status status : Status.values()) {
            if (status.wireValue().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException();
    }

    private static Asset parseAsset(String value) {
        return switch (value) {
            case "BLOCK" -> Asset.BLOCK;
            case "LTC" -> Asset.LTC;
            default -> throw new IllegalArgumentException();
        };
    }

    private static void parseRole(String value) {
        if (!"maker".equals(value) && !"taker".equals(value)) {
            throw new IllegalArgumentException();
        }
    }

    private static void parseStage(String value) {
        for (Stage stage : Stage.values()) {
            if (stage.wireValue().equals(value)) {
                return;
            }
        }
        throw new IllegalArgumentException();
    }

    private sealed interface FrameRecord
            permits ProfileInitialisationFrameRecord,
                    OperationFrameRecord,
                    GenerationHandoverFrameRecord {}

    private record ProfileInitialisationFrameRecord(
            String schema, String frameType, String profileId, String initialGenerationId)
            implements FrameRecord {}

    private record OperationFrameRecord(
            String schema,
            String frameType,
            String runtimeGenerationId,
            TargetContext targetContext,
            Operation operation,
            String semanticOperationId,
            String payloadDigest,
            Status status,
            String resultDigest,
            String resultEnvelopeBase64)
            implements FrameRecord {}

    private record GenerationHandoverFrameRecord(
            String schema,
            String frameType,
            String profileId,
            String priorGenerationId,
            String nextGenerationId,
            String desktopJournalHeadDigest,
            String handoverDigest)
            implements FrameRecord {}

    private record ValidatedAppend(ResultEnvelope envelope) {}

    private record PreparedFrame(byte[] recordBytes, byte[] mac) {}

    private record BroadcastIdentity(String finalBytesDigest, String finalTxid, String finalWtxid) {}

    private static final class OperationChain {
        private final String semanticOperationId;
        private final String payloadDigest;
        private final TargetContext targetContext;
        private final Operation operation;
        private final Status latestStatus;
        private final byte[] latestBytes;
        private final ResultEnvelope latestEnvelope;

        private OperationChain(
                String semanticOperationId,
                String payloadDigest,
                TargetContext targetContext,
                Operation operation,
                Status latestStatus,
                byte[] latestBytes,
                ResultEnvelope latestEnvelope) {
            this.semanticOperationId = semanticOperationId;
            this.payloadDigest = payloadDigest;
            this.targetContext = targetContext;
            this.operation = operation;
            this.latestStatus = latestStatus;
            this.latestBytes = latestBytes.clone();
            this.latestEnvelope = latestEnvelope;
        }
    }

    private static final class AuthenticatedState {
        private final String profileId;
        private final String currentGenerationId;
        private final int frameCount;
        private final long byteCount;
        private final byte[] latestMac;
        private final Map<String, OperationChain> operations;
        private final Set<String> seenGenerations;

        private AuthenticatedState(
                String profileId,
                String currentGenerationId,
                int frameCount,
                long byteCount,
                byte[] latestMac,
                Map<String, OperationChain> operations,
                Set<String> seenGenerations) {
            this.profileId = profileId;
            this.currentGenerationId = currentGenerationId;
            this.frameCount = frameCount;
            this.byteCount = byteCount;
            this.latestMac = latestMac.clone();
            this.operations = operations;
            this.seenGenerations = seenGenerations;
        }
    }

    private static final class OpenFailure extends Exception {
        private final FactoryFailure reason;

        private OpenFailure(FactoryFailure reason) {
            this.reason = reason;
        }
    }
}
