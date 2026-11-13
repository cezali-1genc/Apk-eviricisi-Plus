const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const SAMPLE_SHA256 = "a".repeat(64);
const OUTPUT_SHA256 = "b".repeat(64);

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp(msAgo = 5000) {
  return new Date(Date.now() - msAgo);
}

function validProjectPayload(uid, overrides = {}) {
  return {
    userId: uid,
    fileName: "sample.apk",
    packageName: "com.example.sample",
    appLabel: "Sample App",
    versionName: "1.0.0",
    inputSha256: SAMPLE_SHA256,
    sourceLang: "en",
    targetLang: "tr",
    translationEngine: "GEMINI_AI",
    translatedCount: 42,
    status: "inspected",
    createdAt: pastTimestamp(10000),
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write user profiles or apk_projects", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("apk_projects").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("apk_projects")
      .doc("proj_1")
      .set(validProjectPayload(ALICE_UID))
  );
});

test("Owner CRUD: Alice can create, read, list, update (inspected -> signed), attach AI summary, and delete her ApkProject", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projectRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("apk_projects")
    .doc("proj_1");

  await assertSucceeds(projectRef.set(validProjectPayload(ALICE_UID)));
  await assertSucceeds(projectRef.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("apk_projects").get()
  );

  // Transition inspected -> signed
  await assertSucceeds(
    projectRef.update({
      status: "signed",
      outputSha256: OUTPUT_SHA256,
      translatedCount: 120,
      updatedAt: pastTimestamp(2000),
    })
  );

  // Terminal state 'signed' still allows attaching AI security summary
  await assertSucceeds(
    projectRef.update({
      aiSecuritySummary: "Temiz APK, şüpheli izin yok.",
      updatedAt: pastTimestamp(1000),
    })
  );

  // Terminal state 'signed' rejects mutating status or outputSha256
  await assertFails(
    projectRef.update({
      status: "inspected",
      updatedAt: pastTimestamp(500),
    })
  );

  await assertSucceeds(projectRef.delete());
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's profile or ApkProject", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("apk_projects")
      .doc("proj_alice")
      .set(validProjectPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("apk_projects").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("apk_projects")
      .doc("proj_alice")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("apk_projects")
      .doc("proj_hack")
      .set(validProjectPayload(BOB_UID))
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("apk_projects")
      .doc("proj_alice")
      .delete()
  );
});

test("Red-team payload checks: rejects ghost fields, invalid SHA-256 length, invalid enum, and userId spoofing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const baseCol = aliceDb.collection("users").doc(ALICE_UID).collection("apk_projects");

  // Ghost field isAdmin
  await assertFails(
    baseCol.doc("ghost_1").set(validProjectPayload(ALICE_UID, { isAdmin: true }))
  );

  // Invalid SHA-256 length
  await assertFails(
    baseCol.doc("bad_sha").set(validProjectPayload(ALICE_UID, { inputSha256: "short" }))
  );

  // Invalid translationEngine enum
  await assertFails(
    baseCol
      .doc("bad_engine")
      .set(validProjectPayload(ALICE_UID, { translationEngine: "HACKED" }))
  );

  // Spoofed userId
  await assertFails(
    baseCol.doc("spoof_uid").set(validProjectPayload(BOB_UID))
  );
});

test("UserProfile CRUD: owner can create and update profile without ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    profileRef.set({
      userId: ALICE_UID,
      displayName: "Serhat Güner",
      email: "serhat@example.com",
      preferredTargetLang: "tr",
      createdAt: pastTimestamp(5000),
      updatedAt: pastTimestamp(5000),
    })
  );

  await assertSucceeds(
    profileRef.update({
      preferredTargetLang: "en",
      updatedAt: pastTimestamp(1000),
    })
  );

  // Cannot inject role field
  await assertFails(
    profileRef.update({
      role: "admin",
      updatedAt: pastTimestamp(500),
    })
  );
});
