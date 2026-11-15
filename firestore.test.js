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
const STORE_ID = "store_1";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

async function seedStoreForAlice() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("stores").doc(STORE_ID).set({
      name: "Alice Store",
      phone: "01700000000",
      address: "Dhaka",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      members: [{ userId: ALICE_UID, role: "owner", status: "active" }],
    });
  });
}

test("Unauthenticated user: cannot read stores or products", async () => {
  await seedStoreForAlice();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("stores").doc(STORE_ID).get());
  await assertFails(unauthDb.collection("stores").doc(STORE_ID).collection("products").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("User isolation: Bob cannot read or write Alice's user profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set({
      email: "alice@test.com",
      storeId: STORE_ID,
      role: "owner",
      isSimpleMode: false,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).set({ isSimpleMode: true }, { merge: true }));

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set({ isSimpleMode: true }, { merge: true }));
});

test("Store isolation: Bob cannot read or modify Alice's store or products", async () => {
  await seedStoreForAlice();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("stores").doc(STORE_ID).get());
  await assertFails(bobDb.collection("stores").doc(STORE_ID).collection("products").get());
  await assertFails(
    bobDb.collection("stores").doc(STORE_ID).collection("products").doc("prod_1").set({
      storeId: STORE_ID,
      name: "Hack Item",
      buyPrice: 10,
      sellPrice: 20,
      stock: 5,
    })
  );
});

test("Store member: Alice can create and query products in her store, ghost fields rejected", async () => {
  await seedStoreForAlice();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("stores").doc(STORE_ID).collection("products").doc("prod_1").set({
      storeId: STORE_ID,
      name: "Rice 5kg",
      buyPrice: 300,
      sellPrice: 350,
      stock: 10,
      createdBy: ALICE_UID,
    })
  );

  await assertSucceeds(
    aliceDb.collection("stores").doc(STORE_ID).collection("products").get()
  );

  // Shadow/Ghost field attack must fail
  await assertFails(
    aliceDb.collection("stores").doc(STORE_ID).collection("products").doc("prod_ghost").set({
      storeId: STORE_ID,
      name: "Rice",
      buyPrice: 10,
      sellPrice: 20,
      stock: 5,
      isVerified: true,
    })
  );

  await assertSucceeds(
    aliceDb.collection("stores").doc(STORE_ID).collection("products").doc("prod_units").set({
      storeId: STORE_ID,
      name: "Miniket Rice",
      unit: "kg",
      buyUnit: "box",
      buyConversion: 25,
      sellUnits: { gram: 1000 },
      buyPrice: 120,
      sellPrice: 160,
      stock: 46.5,
      lowStockLimit: 5.5,
      createdBy: ALICE_UID,
    })
  );

  // Negative stock must fail
  await assertFails(
    aliceDb.collection("stores").doc(STORE_ID).collection("products").doc("prod_neg").set({
      storeId: STORE_ID,
      name: "Bad Rice",
      buyPrice: 10,
      sellPrice: 20,
      stock: -1,
    })
  );
});

test("Store query alignment: unfiltered stores list fails, ownerId/memberUids filtered succeeds", async () => {
  await seedStoreForAlice();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("stores").get());
  await assertSucceeds(aliceDb.collection("stores").where("memberUids", "array-contains", ALICE_UID).get());
});

test("Store shopType and productCategories: owner can create and update, non-member rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("stores").doc("store_hw").set({
      name: "Hardware Point",
      phone: "01711111111",
      address: "Nawabpur",
      shopType: "hardware",
      productCategories: ["Hand Tools", "Power Tools", "Nuts & Bolts", "Electrical", "Paint", "Plumbing"],
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      members: [{ userId: ALICE_UID, role: "owner", status: "active" }],
    })
  );

  await assertSucceeds(
    aliceDb.collection("stores").doc("store_hw").update({
      shopType: "general",
      productCategories: ["Hand Tools", "Power Tools", "Daily Essentials"],
    })
  );

  await assertFails(
    bobDb.collection("stores").doc("store_hw").update({
      shopType: "pharmacy",
    })
  );
});
