package io.cloudchains.app.net.settlement;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class SettlementSourceBoundaryTest {
    private static final Path MAIN = Path.of("src/main/java");
    private static final Path TEST = Path.of("src/test/java");
    private static final Path PACKAGE =
            Path.of("io/cloudchains/app/net/settlement");

    @Test
    void sourceSliceContainsExactlyTheTenAddOnlyFilesAndNoProductionCaller() throws Exception {
        Set<Path> expectedMain =
                Set.of(
                        PACKAGE.resolve("SettlementContractV1.java"),
                        PACKAGE.resolve("SettlementCanonicalJsonV1.java"),
                        PACKAGE.resolve("SettlementEnvelopeCodecV1.java"),
                        PACKAGE.resolve("SettlementIdentityV1.java"),
                        PACKAGE.resolve("InertSettlementOperationStoreV1.java"));
        Set<Path> expectedTest =
                Set.of(
                        PACKAGE.resolve("SettlementCanonicalJsonV1Test.java"),
                        PACKAGE.resolve("SettlementEnvelopeCodecV1Test.java"),
                        PACKAGE.resolve("SettlementIdentityV1Test.java"),
                        PACKAGE.resolve("InertSettlementOperationStoreV1Test.java"),
                        PACKAGE.resolve("SettlementSourceBoundaryTest.java"));
        assertEquals(expectedMain, relativeJavaFiles(MAIN.resolve(PACKAGE), MAIN));
        assertEquals(expectedTest, relativeJavaFiles(TEST.resolve(PACKAGE), TEST));
        Set<String> expectedChanges =
                java.util.stream.Stream.concat(
                                expectedMain.stream().map(path -> MAIN.resolve(path).toString()),
                                expectedTest.stream().map(path -> TEST.resolve(path).toString()))
                        .collect(Collectors.toSet());
        assertEquals(expectedChanges, changedPathsSinceReviewedBase());

        try (var files = Files.walk(MAIN)) {
            List<Path> callers =
                    files.filter(path -> path.toString().endsWith(".java"))
                            .filter(path -> !path.startsWith(MAIN.resolve(PACKAGE)))
                            .filter(
                                    path -> {
                                        try {
                                            return Files.readString(path)
                                                    .contains("io.cloudchains.app.net.settlement");
                                        } catch (Exception exception) {
                                            throw new AssertionError(exception);
                                        }
                                    })
                            .toList();
            assertTrue(callers.isEmpty(), callers.toString());
        }

        String settlementSource =
                Files.readString(MAIN.resolve(PACKAGE).resolve("SettlementContractV1.java"))
                        + Files.readString(MAIN.resolve(PACKAGE).resolve("SettlementCanonicalJsonV1.java"))
                        + Files.readString(MAIN.resolve(PACKAGE).resolve("SettlementEnvelopeCodecV1.java"))
                        + Files.readString(MAIN.resolve(PACKAGE).resolve("SettlementIdentityV1.java"))
                        + Files.readString(
                                MAIN.resolve(PACKAGE).resolve("InertSettlementOperationStoreV1.java"));
        for (String forbidden :
                List.of(
                        "import io.netty",
                        "import org.bitcoinj",
                        "CoinInstance",
                        "WalletHelper",
                        "KeyHandler",
                        "ConfigHelper",
                        "HTTPClient",
                        "XRouter",
                        "JSONRPC",
                        "ConsoleMenu",
                        "ArgMenu")) {
            assertFalse(settlementSource.contains(forbidden), forbidden);
        }
    }

    @Test
    void inertStoreFactoriesOpenWithoutFollowingLinksAndForceBeforeReturning() throws Exception {
        String source =
                Files.readString(
                        MAIN.resolve(PACKAGE).resolve("InertSettlementOperationStoreV1.java"));
        String fresh =
                source.substring(
                        source.indexOf("static InertSettlementOperationStoreV1 createFreshTemporaryProfile"),
                        source.indexOf("static InertSettlementOperationStoreV1 reopenTemporaryProfile"));
        String reopen =
                source.substring(
                        source.indexOf("static InertSettlementOperationStoreV1 reopenTemporaryProfile"),
                        source.indexOf("AppendOutcome append("));
        assertEquals(1, occurrences(fresh, "FileChannel.open("));
        assertEquals(1, occurrences(reopen, "FileChannel.open("));
        assertTrue(fresh.contains("StandardOpenOption.CREATE_NEW"));
        assertTrue(fresh.contains("LinkOption.NOFOLLOW_LINKS"));
        assertTrue(reopen.contains("LinkOption.NOFOLLOW_LINKS"));
        assertOrder(fresh, "writeFrame(channel, record, mac);", "channel.force(true);", "return new InertSettlementOperationStoreV1(");
        assertOrder(reopen, "preflightPrepared(", "writeFrame(channel, frame.recordBytes, frame.mac);", "channel.force(true);", "return new InertSettlementOperationStoreV1(");

        String cleanup =
                source.substring(
                        source.indexOf("private static void cleanupFactory("),
                        source.indexOf("private static List<OperationAppend> copyAndValidatePrepared("));
        String instanceZero =
                source.substring(
                        source.indexOf("private void zeroKey()"),
                        source.indexOf("private static Path validateFreshRoot("));
        assertEquals(1, occurrences(cleanup, "Arrays.fill(key, (byte) 0)"));
        assertEquals(1, occurrences(instanceZero, "Arrays.fill(authenticationKey, (byte) 0)"));
    }

    @Test
    void callableVisibilityIsExactlyTheReviewedPackagePrivateSurface() {
        for (Class<?> type :
                List.of(
                        SettlementContractV1.class,
                        SettlementCanonicalJsonV1.class,
                        SettlementEnvelopeCodecV1.class,
                        SettlementIdentityV1.class,
                        InertSettlementOperationStoreV1.class,
                        SettlementCanonicalJsonV1Test.class,
                        SettlementEnvelopeCodecV1Test.class,
                        SettlementIdentityV1Test.class,
                        InertSettlementOperationStoreV1Test.class,
                        SettlementSourceBoundaryTest.class)) {
            assertTrue(Modifier.isFinal(type.getModifiers()), type.getName());
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getName());
            assertFalse(Modifier.isProtected(type.getModifiers()), type.getName());
        }

        assertEquals(
                Set.of(
                        "parse(byte[]):com.google.gson.JsonElement:static:package",
                        "encode(com.google.gson.JsonElement):byte[]:static:package"),
                authoredEntrySignatures(SettlementCanonicalJsonV1.class));
        assertEquals(
                Set.of(
                        "decodeRequest(byte[]):io.cloudchains.app.net.settlement.SettlementContractV1$RequestEnvelope:static:package",
                        "decodeResult(byte[]):io.cloudchains.app.net.settlement.SettlementContractV1$ResultEnvelope:static:package",
                        "decodeError(byte[],io.cloudchains.app.net.settlement.SettlementContractV1$ExpectedErrorIdentity):io.cloudchains.app.net.settlement.SettlementContractV1$ErrorEnvelope:static:package",
                        "encodeResult(io.cloudchains.app.net.settlement.SettlementContractV1$ResultEnvelope):byte[]:static:package",
                        "encodeResultWithoutDigest(io.cloudchains.app.net.settlement.SettlementContractV1$ResultDigestInputs):byte[]:static:package"),
                authoredEntrySignatures(SettlementEnvelopeCodecV1.class));
        assertEquals(
                Set.of(
                        "semanticOperationId(io.cloudchains.app.net.settlement.SettlementContractV1$IdentityInputs):java.lang.String:static:package",
                        "payloadDigest(io.cloudchains.app.net.settlement.SettlementContractV1$PayloadInputs):java.lang.String:static:package",
                        "resultDigest(io.cloudchains.app.net.settlement.SettlementContractV1$ResultDigestInputs):java.lang.String:static:package",
                        "handoverDigest(io.cloudchains.app.net.settlement.SettlementContractV1$GenerationHandover):java.lang.String:static:package"),
                authoredEntrySignatures(SettlementIdentityV1.class));
        assertEquals(
                Set.of(
                        "createFreshTemporaryProfile(java.nio.file.Path,byte[],io.cloudchains.app.net.settlement.SettlementContractV1$StoreLimits,java.lang.String,java.lang.String):io.cloudchains.app.net.settlement.InertSettlementOperationStoreV1:static:package",
                        "reopenTemporaryProfile(java.nio.file.Path,byte[],io.cloudchains.app.net.settlement.SettlementContractV1$StoreLimits,java.util.List):io.cloudchains.app.net.settlement.InertSettlementOperationStoreV1:static:package",
                        "append(io.cloudchains.app.net.settlement.SettlementContractV1$OperationAppend):io.cloudchains.app.net.settlement.SettlementContractV1$AppendOutcome:instance:package",
                        "lookup(io.cloudchains.app.net.settlement.SettlementContractV1$OperationLookup):io.cloudchains.app.net.settlement.SettlementContractV1$LookupOutcome:instance:package",
                        "acceptHandover(io.cloudchains.app.net.settlement.SettlementContractV1$GenerationHandover):io.cloudchains.app.net.settlement.SettlementContractV1$HandoverOutcome:instance:package",
                        "snapshot():io.cloudchains.app.net.settlement.SettlementContractV1$SnapshotOutcome:instance:package",
                        "close():void:instance:public"),
                authoredEntrySignatures(InertSettlementOperationStoreV1.class));

        assertEquals(
                Set.of("parse", "encode"),
                authoredEntryMethods(SettlementCanonicalJsonV1.class));
        assertEquals(
                Set.of(
                        "decodeRequest",
                        "decodeResult",
                        "decodeError",
                        "encodeResult",
                        "encodeResultWithoutDigest"),
                authoredEntryMethods(SettlementEnvelopeCodecV1.class));
        assertEquals(
                Set.of("semanticOperationId", "payloadDigest", "resultDigest", "handoverDigest"),
                authoredEntryMethods(SettlementIdentityV1.class));
        assertEquals(
                Set.of(
                        "createFreshTemporaryProfile",
                        "reopenTemporaryProfile",
                        "append",
                        "lookup",
                        "acceptHandover",
                        "snapshot",
                        "close"),
                authoredEntryMethods(InertSettlementOperationStoreV1.class));
        Method close =
                Arrays.stream(InertSettlementOperationStoreV1.class.getDeclaredMethods())
                        .filter(method -> method.getName().equals("close"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(Modifier.isPublic(close.getModifiers()));
        for (Method method : InertSettlementOperationStoreV1.class.getDeclaredMethods()) {
            if (!method.isSynthetic()
                    && !Modifier.isPrivate(method.getModifiers())
                    && !method.getName().equals("close")) {
                assertFalse(Modifier.isPublic(method.getModifiers()), method.toString());
                assertFalse(Modifier.isProtected(method.getModifiers()), method.toString());
            }
        }
    }

    @Test
    void nestedContractSurfaceConstructorsModifiersAndSealedSumsAreExact() {
        Set<String> expected =
                Set.of(
                        "Operation", "Schema", "Status", "ErrorCode", "Role", "Stage", "Asset",
                        "ReservationPurpose", "ProofPurpose", "AddressProbePurpose", "SighashPolicy",
                        "Producer", "BroadcastRejectionCode", "QuarantineCode", "RequestBody",
                        "ContextualRequestBody", "ResultBody", "BroadcastStageResult", "RequestEnvelope",
                        "ExpectedErrorIdentity", "ChainIdentity", "PrevoutEvidence", "OutpointIdentity",
                        "ExpectedOutput", "RecoveryBinding", "ContextualRequestEnvelope",
                        "OwnedAddressRequestEnvelope", "LookupRequestEnvelope", "NoSemanticIdentity",
                        "ValidatedRequestIdentity", "ResultEnvelope", "ErrorEnvelope",
                        "ReserveWalletInputsRequest", "ProveReservedUtxoOwnershipRequest",
                        "CheckOwnedAddressRequest", "SignSettlementStageRequest",
                        "BroadcastSettlementStageRequest", "GetSettlementOperationResultRequest",
                        "ReservationResult", "OwnershipProofResult", "OwnedAddressResult",
                        "SignedStageResult", "NonBroadcastQuarantinedResult",
                        "BroadcastDispatchRecordedResult", "BroadcastSucceededResult",
                        "BroadcastRejectedResult", "BroadcastQuarantinedResult", "IdentityInputs",
                        "PayloadInputs", "ResultDigestInputs", "GenerationHandover", "TargetContext",
                        "OperationLookup", "OperationAppend", "StoreLimits", "StoreHold", "AppendOutcome",
                        "Appended", "IdenticalReplay", "AppendHeld", "LookupOutcome", "Found", "LookupHeld",
                        "HandoverOutcome", "HandoverAccepted", "HandoverHeld", "SnapshotOutcome",
                        "SnapshotAvailable", "SnapshotHeld", "StoreSnapshot", "FactoryFailure",
                        "StoreOpenException", "StoreCloseException");
        Map<String, Class<?>> nested =
                Arrays.stream(SettlementContractV1.class.getDeclaredClasses())
                        .collect(Collectors.toMap(Class::getSimpleName, type -> type));
        assertEquals(expected, nested.keySet());
        Map<String, String> expectedRecordComponents =
                Map.ofEntries(
                        Map.entry("ChainIdentity", "asset:Asset,network:String,genesisHash:String,networkMagicHex:String,p2pkhVersion:String,p2shVersions:List,signedMessagePrefixHex:String,baseUnitsPerCoin:String,minimumConfirmations:String,chainPolicyDigest:String"),
                        Map.entry("PrevoutEvidence", "txid:String,vout:String,valueBaseUnits:String,scriptPubKeyHex:String,ownedAddress:String,confirmationBlockHash:String,confirmationHeight:String,observedConfirmations:String"),
                        Map.entry("OutpointIdentity", "txid:String,vout:String"),
                        Map.entry("ExpectedOutput", "index:String,valueBaseUnits:String,scriptPubKeyHex:String"),
                        Map.entry("RecoveryBinding", "recoveryRecordId:String,fundingTxid:String,refundTxid:String,recoveryPayloadDigest:String,persistedAtUnixMillis:String"),
                        Map.entry("ContextualRequestEnvelope", "schema:Schema,operation:Operation,profileId:String,runtimeGenerationId:String,parentIntentId:String,childStageId:String,semanticOperationId:String,payloadDigest:String,role:String,stage:String,asset:Asset,chainIdentity:ChainIdentity,policyDigest:String,issuedAtUnixMillis:String,deadlineUnixMillis:String,body:ContextualRequestBody"),
                        Map.entry("OwnedAddressRequestEnvelope", "schema:Schema,operation:Operation,profileId:String,runtimeGenerationId:String,semanticOperationId:String,payloadDigest:String,asset:Asset,chainIdentity:ChainIdentity,policyDigest:String,issuedAtUnixMillis:String,deadlineUnixMillis:String,body:CheckOwnedAddressRequest"),
                        Map.entry("LookupRequestEnvelope", "schema:Schema,operation:Operation,profileId:String,runtimeGenerationId:String,parentIntentId:String,childStageId:String,semanticOperationId:String,payloadDigest:String,role:String,stage:String,asset:Asset,chainIdentity:ChainIdentity,policyDigest:String,issuedAtUnixMillis:String,deadlineUnixMillis:String,body:GetSettlementOperationResultRequest"),
                        Map.entry("NoSemanticIdentity", "operation:Operation"),
                        Map.entry("ValidatedRequestIdentity", "request:RequestEnvelope"),
                        Map.entry("ResultEnvelope", "schema:Schema,operation:Operation,semanticOperationId:String,payloadDigest:String,status:Status,resultDigest:String,recordedAtUnixMillis:String,body:ResultBody"),
                        Map.entry("ErrorEnvelope", "schema:Schema,operation:Operation,semanticOperationId:String,code:ErrorCode,quarantined:boolean,recordedAtUnixMillis:String"),
                        Map.entry("ReserveWalletInputsRequest", "purpose:ReservationPurpose,walletAddress:String,baseAmountBlockUnits:String,outgoingAmountUnits:String,fixedTakerFeeBlockUnits:String,maxStageNativeFeeUnits:String,maxGraphNativeFeeUnits:String,recoveryReserveUnits:String,outpoints:List"),
                        Map.entry("ProveReservedUtxoOwnershipRequest", "reservationId:String,outpoint:OutpointIdentity,messageHex:String,messageDigest:String,purpose:ProofPurpose"),
                        Map.entry("CheckOwnedAddressRequest", "address:String,purpose:AddressProbePurpose"),
                        Map.entry("SignSettlementStageRequest", "reservationId:String,unsignedTransactionHex:String,unsignedBytesDigest:String,unsignedTxid:String,unsignedWtxid:String,previousOutputs:List,expectedOutputs:List,changeAddress:String,inputSequences:List,lockTime:String,sighashPolicy:SighashPolicy,maxStageNativeFeeUnits:String,contractPolicyDigest:String"),
                        Map.entry("BroadcastSettlementStageRequest", "finalTransactionHex:String,finalBytesDigest:String,finalTxid:String,finalWtxid:String,producer:Producer,predecessorOperationId:String,contractPolicyDigest:String,recoveryBinding:RecoveryBinding"),
                        Map.entry("GetSettlementOperationResultRequest", "targetSemanticOperationId:String,expectedPayloadDigest:String"),
                        Map.entry("ReservationResult", "reservationId:String,reservationDigest:String,outpoints:List,deduplicated:boolean"),
                        Map.entry("OwnershipProofResult", "messageDigest:String,compactProofBase64:String,proofDigest:String"),
                        Map.entry("OwnedAddressResult", "owned:boolean"),
                        Map.entry("SignedStageResult", "finalTransactionHex:String,finalBytesDigest:String,finalTxid:String,finalWtxid:String,signedInputIndexes:List"),
                        Map.entry("NonBroadcastQuarantinedResult", "quarantineCode:QuarantineCode,evidenceDigest:String"),
                        Map.entry("BroadcastDispatchRecordedResult", "finalBytesDigest:String,finalTxid:String,finalWtxid:String,dispatchRecordDigest:String"),
                        Map.entry("BroadcastSucceededResult", "finalBytesDigest:String,finalTxid:String,finalWtxid:String,returnedTxid:String"),
                        Map.entry("BroadcastRejectedResult", "finalBytesDigest:String,finalTxid:String,finalWtxid:String,rejectionCode:BroadcastRejectionCode,evidenceDigest:String"),
                        Map.entry("BroadcastQuarantinedResult", "finalBytesDigest:String,finalTxid:String,finalWtxid:String,quarantineCode:QuarantineCode,evidenceDigest:String"),
                        Map.entry("IdentityInputs", "operation:Operation,profileId:String,parentIntentId:String,childStageId:String,role:String,stage:String,asset:Asset,chainPolicyDigest:String,policyDigest:String,operationObjectId:String"),
                        Map.entry("PayloadInputs", "operation:Operation,profileId:String,parentIntentId:String,childStageId:String,role:String,stage:String,asset:Asset,chainIdentityDigest:String,chainPolicyDigest:String,policyDigest:String,deadlineUnixMillis:String,operationObjectId:String,bodyDigest:String"),
                        Map.entry("ResultDigestInputs", "operation:Operation,semanticOperationId:String,payloadDigest:String,status:Status,recordedAtUnixMillis:String,body:ResultBody"),
                        Map.entry("GenerationHandover", "profileId:String,priorGenerationId:String,nextGenerationId:String,desktopJournalHeadDigest:String,handoverDigest:String"),
                        Map.entry("TargetContext", "profileId:String,parentIntentId:String,childStageId:String,role:String,stage:String,asset:Asset,chainIdentity:ChainIdentity,policyDigest:String"),
                        Map.entry("OperationLookup", "runtimeGenerationId:String,targetContext:TargetContext,semanticOperationId:String,payloadDigest:String"),
                        Map.entry("OperationAppend", "runtimeGenerationId:String,targetContext:TargetContext,operation:Operation,semanticOperationId:String,payloadDigest:String,status:Status,resultDigest:String,canonicalResultEnvelope:byte[]"),
                        Map.entry("StoreLimits", "maxFrames:int,maxBytes:long"),
                        Map.entry("Appended", "canonicalResultEnvelope:byte[]"),
                        Map.entry("IdenticalReplay", "canonicalResultEnvelope:byte[]"),
                        Map.entry("AppendHeld", "reason:StoreHold"),
                        Map.entry("Found", "canonicalResultEnvelope:byte[]"),
                        Map.entry("LookupHeld", "reason:StoreHold"),
                        Map.entry("HandoverAccepted", "nextGenerationId:String"),
                        Map.entry("HandoverHeld", "reason:StoreHold"),
                        Map.entry("SnapshotAvailable", "snapshot:StoreSnapshot"),
                        Map.entry("SnapshotHeld", "reason:StoreHold"),
                        Map.entry("StoreSnapshot", "profileId:String,currentGenerationId:String,authenticatedFrameCount:int,authenticatedByteCount:long,retainedOperationCount:int,latestFrameMacHex:String"));
        Map<String, String> actualRecordComponents =
                nested.values().stream()
                        .filter(Class::isRecord)
                        .collect(Collectors.toMap(Class::getSimpleName, SettlementSourceBoundaryTest::recordComponents));
        assertEquals(expectedRecordComponents, actualRecordComponents);
        for (Class<?> type : nested.values()) {
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getName());
            assertFalse(Modifier.isProtected(type.getModifiers()), type.getName());
            if (!type.isInterface()) {
                assertTrue(Modifier.isFinal(type.getModifiers()), type.getName());
            }
            if (type.isRecord()) {
                Class<?>[] components =
                        Arrays.stream(type.getRecordComponents())
                                .map(component -> component.getType())
                                .toArray(Class<?>[]::new);
                Constructor<?> canonical = assertDoesNotThrow(() -> type.getDeclaredConstructor(components));
                assertFalse(Modifier.isPublic(canonical.getModifiers()), type.getName());
                assertFalse(Modifier.isProtected(canonical.getModifiers()), type.getName());
                assertEquals(1, type.getDeclaredConstructors().length, type.getName());
                Set<String> allowedPublic =
                        Arrays.stream(type.getRecordComponents())
                                .map(component -> component.getName())
                                .collect(Collectors.toSet());
                allowedPublic.addAll(Set.of("equals", "hashCode", "toString"));
                for (Method method : type.getDeclaredMethods()) {
                    if (Modifier.isPublic(method.getModifiers())) {
                        assertTrue(allowedPublic.contains(method.getName()), method.toString());
                    }
                    assertFalse(Modifier.isProtected(method.getModifiers()), method.toString());
                }
            } else if (type.isEnum()) {
                assertEquals(1, type.getDeclaredConstructors().length, type.getName());
                assertTrue(
                        Modifier.isPrivate(type.getDeclaredConstructors()[0].getModifiers()),
                        type.getName());
                for (Method method : type.getDeclaredMethods()) {
                    if (Modifier.isPublic(method.getModifiers())) {
                        assertTrue(
                                Set.of("values", "valueOf").contains(method.getName()),
                                method.toString());
                    }
                    assertFalse(Modifier.isProtected(method.getModifiers()), method.toString());
                }
            } else if (!type.isInterface()) {
                for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                    assertFalse(Modifier.isPublic(constructor.getModifiers()), constructor.toString());
                    assertFalse(Modifier.isProtected(constructor.getModifiers()), constructor.toString());
                }
            }
            if (type.isInterface()) {
                assertEquals(0, type.getDeclaredMethods().length, type.getName());
            }
        }

        assertPermits(
                nested.get("RequestBody"),
                "ContextualRequestBody", "CheckOwnedAddressRequest", "GetSettlementOperationResultRequest");
        assertPermits(
                nested.get("ContextualRequestBody"),
                "ReserveWalletInputsRequest", "ProveReservedUtxoOwnershipRequest",
                "SignSettlementStageRequest", "BroadcastSettlementStageRequest");
        assertPermits(
                nested.get("ResultBody"),
                "ReservationResult", "OwnershipProofResult", "OwnedAddressResult", "SignedStageResult",
                "NonBroadcastQuarantinedResult", "BroadcastStageResult");
        assertPermits(
                nested.get("BroadcastStageResult"),
                "BroadcastDispatchRecordedResult", "BroadcastSucceededResult",
                "BroadcastRejectedResult", "BroadcastQuarantinedResult");
        assertPermits(
                nested.get("RequestEnvelope"),
                "ContextualRequestEnvelope", "OwnedAddressRequestEnvelope", "LookupRequestEnvelope");
        assertPermits(
                nested.get("ExpectedErrorIdentity"),
                "NoSemanticIdentity", "ValidatedRequestIdentity");
        assertPermits(nested.get("AppendOutcome"), "Appended", "IdenticalReplay", "AppendHeld");
        assertPermits(nested.get("LookupOutcome"), "Found", "LookupHeld");
        assertPermits(nested.get("HandoverOutcome"), "HandoverAccepted", "HandoverHeld");
        assertPermits(nested.get("SnapshotOutcome"), "SnapshotAvailable", "SnapshotHeld");

        for (Class<?> topLevel :
                List.of(
                        SettlementContractV1.class,
                        SettlementCanonicalJsonV1.class,
                        SettlementEnvelopeCodecV1.class,
                        SettlementIdentityV1.class)) {
            assertEquals(1, topLevel.getDeclaredConstructors().length);
            assertTrue(Modifier.isPrivate(topLevel.getDeclaredConstructors()[0].getModifiers()));
        }
        assertTrue(
                Arrays.stream(InertSettlementOperationStoreV1.class.getDeclaredConstructors())
                        .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
        for (Class<?> privateNested : InertSettlementOperationStoreV1.class.getDeclaredClasses()) {
            assertTrue(Modifier.isPrivate(privateNested.getModifiers()), privateNested.getName());
        }
        for (Class<?> privateNested : SettlementIdentityV1.class.getDeclaredClasses()) {
            assertTrue(Modifier.isPrivate(privateNested.getModifiers()), privateNested.getName());
        }
        for (Class<?> privateNested : SettlementCanonicalJsonV1.class.getDeclaredClasses()) {
            assertTrue(Modifier.isPrivate(privateNested.getModifiers()), privateNested.getName());
        }
    }

    @Test
    void operationSurfaceHasOnlySixNamedValuesAndNoGenericAuthority() {
        assertEquals(
                List.of(
                        "reserveWalletInputs",
                        "proveReservedUtxoOwnership",
                        "checkOwnedAddress",
                        "signSettlementStage",
                        "broadcastSettlementStage",
                        "getSettlementOperationResult"),
                Arrays.stream(SettlementContractV1.Operation.values())
                        .map(SettlementContractV1.Operation::wireValue)
                        .toList());
        assertEquals(3, SettlementContractV1.Schema.values().length);
        assertEquals(8, SettlementContractV1.Status.values().length);
        assertEquals(21, SettlementContractV1.ErrorCode.values().length);
        Set<String> callable =
                Arrays.stream(SettlementIdentityV1.class.getDeclaredMethods())
                        .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                        .map(Method::getName)
                        .collect(Collectors.toSet());
        assertFalse(callable.contains("canonicalDigest"));
        assertTrue(
                Arrays.stream(InertSettlementOperationStoreV1.class.getDeclaredMethods())
                        .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                        .noneMatch(
                                method ->
                                        method.getName().matches(
                                                "(?i).*(sign|send|broadcast|relay|wallet|key|credential|listen|rpc).*")));
    }

    @Test
    void acceptedBoundaryFilesRemainByteIdentical() throws Exception {
        Map<String, String> expected =
                Map.ofEntries(
                        Map.entry("pom.xml", "02d9a6d59967629a0b37e90d24bf2da42be2566b"),
                        Map.entry(
                                "src/main/java/io/cloudchains/app/console/ArgMenu.java",
                                "da34c551b1e00533f7e623e598fefb3001154120"),
                        Map.entry(
                                "src/main/java/io/cloudchains/app/console/ConsoleMenu.java",
                                "9325c2e091d253a2097b18abff68382fd93343da"),
                        Map.entry(
                                "src/main/java/io/cloudchains/app/crypto/KeyHandler.java",
                                "8f9ff78abe690ae5b8709c145277bdcb2b8568ce"),
                        Map.entry(
                                "src/main/java/io/cloudchains/app/net/CoinInstance.java",
                                "61e8aa9c0fbc8ba8981f9b0c21858d02bcd873da"),
                        Map.entry(
                                "src/main/java/io/cloudchains/app/net/api/JSONRPCController.java",
                                "5782007c41933d7912dfa7ccde054abde4a07cc3"),
                        Map.entry(
                                "src/main/java/io/cloudchains/app/net/api/JSONRPCServer.java",
                                "d6a9906e8178e59622c5cd35cc4c72773dbad5f8"),
                        Map.entry(
                                "src/main/java/io/cloudchains/app/net/api/http/master/HTTPServerHandler.java",
                                "80c15132b123509340c173dfc80dc1e1f84ffd03"),
                        Map.entry(
                                "src/main/java/io/cloudchains/app/net/api/http/server/HTTPServerHandler.java",
                                "06b1b0ac6458f747e2c13bb6e9c86667d5892802"),
                        Map.entry(
                                "src/main/java/io/cloudchains/app/net/api/http/server/HTTPServerInitializer.java",
                                "d4f95fd1a8c0db3ad0009cde99428e0707969c93"),
                        Map.entry(
                                "src/main/java/io/cloudchains/app/net/api/http/client/HTTPClient.java",
                                "5e07252b74a1f3129e62af6798f3f5d8774b308a"),
                        Map.entry(
                                "src/main/java/io/cloudchains/app/util/ConfigHelper.java",
                                "777673a76015b27a83694e1bf9b422334b9e35a8"),
                        Map.entry(
                                "src/main/java/io/cloudchains/app/wallet/WalletHelper.java",
                                "50780e4f397d3e7c0b2bc2580d8078e1b53bcb83"),
                        Map.entry(
                                "src/test/java/ManagedReadOnlyAuthorityBoundaryTest.java",
                                "6e54e2db223ab5417cdfce2b2deaddaee8665305"));
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), gitBlobId(Path.of(entry.getKey())), entry.getKey());
        }
    }

    private static Set<Path> relativeJavaFiles(Path directory, Path root) throws Exception {
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .map(root::relativize)
                    .collect(Collectors.toSet());
        }
    }

    private static Set<String> changedPathsSinceReviewedBase() throws Exception {
        Set<String> paths = new java.util.HashSet<>();
        String committed = runGit("diff", "--name-only", "1e24a836906e5b4c9bf594e2a326a53f35dbef04", "HEAD", "--");
        committed.lines().filter(line -> !line.isBlank()).forEach(paths::add);
        String working = runGit("status", "--porcelain=v1", "--untracked-files=all");
        working.lines()
                .filter(line -> line.length() > 3)
                .map(line -> line.substring(3))
                .forEach(paths::add);
        return Set.copyOf(paths);
    }

    private static String runGit(String... arguments) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }

    private static Set<String> authoredEntryMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());
    }

    private static Set<String> authoredEntrySignatures(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                .map(
                        method ->
                                method.getName()
                                        + "("
                                        + Arrays.stream(method.getParameterTypes())
                                                .map(Class::getTypeName)
                                                .collect(Collectors.joining(","))
                                        + "):"
                                        + method.getReturnType().getTypeName()
                                        + ":"
                                        + (Modifier.isStatic(method.getModifiers())
                                                ? "static"
                                                : "instance")
                                        + ":"
                                        + visibility(method.getModifiers()))
                .collect(Collectors.toSet());
    }

    private static String visibility(int modifiers) {
        if (Modifier.isPublic(modifiers)) {
            return "public";
        }
        if (Modifier.isProtected(modifiers)) {
            return "protected";
        }
        if (Modifier.isPrivate(modifiers)) {
            return "private";
        }
        return "package";
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static void assertOrder(String source, String... fragments) {
        int prior = -1;
        for (String fragment : fragments) {
            int next = source.indexOf(fragment, prior + 1);
            assertTrue(next > prior, fragment);
            prior = next;
        }
    }

    private static void assertPermits(Class<?> sealed, String... expected) {
        assertTrue(sealed.isSealed(), sealed.getName());
        assertEquals(
                Set.of(expected),
                Arrays.stream(sealed.getPermittedSubclasses())
                        .map(Class::getSimpleName)
                        .collect(Collectors.toSet()));
    }

    private static String recordComponents(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(component -> component.getName() + ":" + simpleType(component.getType()))
                .collect(Collectors.joining(","));
    }

    private static String simpleType(Class<?> type) {
        if (type.isArray()) {
            return simpleType(type.getComponentType()) + "[]";
        }
        return type.getSimpleName();
    }

    private static String gitBlobId(Path path) throws Exception {
        byte[] body = Files.readAllBytes(path);
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(("blob " + body.length + "\0").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        digest.update(body);
        return HexFormat.of().formatHex(digest.digest());
    }
}
