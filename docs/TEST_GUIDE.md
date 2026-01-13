# Battle System Test Guide

**Phase 13: Test Suite Build-Out**

This document provides comprehensive guidance for understanding and running the battle system test suite.

## Table of Contents

1. [Test Coverage Overview](#test-coverage-overview)
2. [Test Categories](#test-categories)
3. [Running Tests](#running-tests)
4. [Test Structure](#test-structure)
5. [Writing New Tests](#writing-new-tests)
6. [Test Data Factories](#test-data-factories)
7. [Common Test Patterns](#common-test-patterns)
8. [Troubleshooting](#troubleshooting)

---

## Test Coverage Overview

**Total Battle System Tests:** 300+ tests across 30+ test files

**Coverage Areas:**
- ✅ Configuration & validation
- ✅ Character creation & persistence
- ✅ Combat mechanics & damage calculations
- ✅ Status effects & abilities
- ✅ Progression & leaderboards
- ✅ Admin commands & permissions
- ✅ Concurrency & race conditions
- ✅ Integration & end-to-end flows

**Test Success Rate:** All tests passing (verified in Phase 13)

---

## Test Categories

### 1. Unit Tests

**Purpose:** Test individual components in isolation

**Location:** `src/test/java/com/discordbot/battle/`

**Key Files:**
- `service/BattleServiceTest.java` - Core battle logic (21 tests)
- `entity/PlayerCharacterTest.java` - Character validation
- `effect/EffectParserTest.java` - Ability effect parsing
- `service/StatusEffectServiceTest.java` - Status effect mechanics
- `service/CharacterValidationServiceTest.java` - Validation rules

**Example:**
```java
@Test
void testAttackWithHighStrength() {
    // Tests attack calculation with specific STR value
}
```

### 2. Integration Tests

**Purpose:** Test complete flows from start to finish

**Location:** `src/test/java/com/discordbot/battle/integration/`

**Key Files:**
- `BattleFlowIntegrationTest.java` - End-to-end battle scenarios (8 tests)

**Scenarios Tested:**
- Complete battle from challenge to victory
- Forfeit flow
- Defend action mechanics
- Character creation → battle → progression
- Challenge expiration
- Busy state validation
- State consistency across operations

**Example:**
```java
@Test
void completeBattleFlowFromChallengeToVictory() {
    // Tests entire battle lifecycle
    createChallenge() → acceptChallenge() → performAttacks() → victory
}
```

### 3. Property-Based Tests

**Purpose:** Verify invariants across many input combinations

**Location:** `src/test/java/com/discordbot/battle/service/`

**Key Files:**
- `DamageCalculationPropertyTest.java` - Damage invariants (10 tests, 100+ test cases)

**Properties Tested:**
- Damage is never negative
- Damage is within reasonable bounds (0-200)
- Critical hits always deal ≥ normal damage
- HP never goes below 0
- Healing cannot exceed max HP
- Armor class doesn't affect damage amount
- Zero HP results in battle end

**Example:**
```java
@ParameterizedTest
@MethodSource("provideAbilityScoreCombinations")
void damageShouldNeverBeNegative(int attackerStr, int defenderCon) {
    // Tests damage calculation with many STR/CON combinations
}
```

### 4. Concurrency Tests

**Purpose:** Verify thread-safety and race condition handling

**Location:** `src/test/java/com/discordbot/battle/service/`

**Key Files:**
- `BattleConcurrencyTest.java` - Thread-safety tests (6 tests)

**Scenarios Tested:**
- Simultaneous attack button presses (only 1 processes)
- Concurrent accept attempts (only 1 succeeds)
- Concurrent challenge creations
- Concurrent battle reads (all succeed)
- Concurrent forfeit attempts (only 1 succeeds)

**Example:**
```java
@RepeatedTest(10)
void simultaneousAttacksShouldOnlyProcessOneTurn() {
    // Simulates 3 concurrent attack button presses
    // Verifies only one attack is processed
}
```

### 5. Repository Tests

**Purpose:** Test database interactions

**Location:** `src/test/java/com/discordbot/battle/repository/`

**Key Files:**
- `PlayerCharacterRepositoryTest.java` - Character CRUD operations
- `AbilityRepositoryTest.java` - Ability queries

### 6. Controller Tests

**Purpose:** Test command handlers and UI interactions

**Location:** `src/test/java/com/discordbot/battle/controller/`

**Key Files:**
- `CreateCharacterCommandHandlerTest.java`
- `DuelCommandHandler` (tested in BattleServiceTest)
- `BattleInteractionHandlerTest.java` - Button press handling
- `BattleAdminCommandHandlerTest.java` - Admin commands (12 tests)

### 7. Configuration Tests

**Purpose:** Test configuration loading and validation

**Location:** `src/test/java/com/discordbot/battle/config/`

**Key Files:**
- `BattlePropertiesTest.java` - Property binding
- `BattleConfigValidatorTest.java` - Startup validation (6 tests)

---

## Running Tests

### Run All Tests

```bash
mvn test
```

### Run Battle Tests Only

```bash
mvn test -Dtest="com.discordbot.battle.**"
```

### Run Specific Test Class

```bash
mvn test -Dtest=BattleServiceTest
```

### Run Specific Test Method

```bash
mvn test -Dtest=BattleServiceTest#testAttackWithCriticalHit
```

### Run with Coverage

```bash
mvn clean test jacoco:report
```

Coverage report: `target/site/jacoco/index.html`

### Run Tests in Parallel

```bash
mvn test -T 4  # Use 4 threads
```

### Run Integration Tests Only

```bash
mvn test -Dtest="**/*IntegrationTest"
```

### Run Property-Based Tests Only

```bash
mvn test -Dtest="**/*PropertyTest"
```

### Run Concurrency Tests Only

```bash
mvn test -Dtest="**/*ConcurrencyTest"
```

---

## Test Structure

### Standard Test Class Structure

```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock
    private Dependency1 dependency1;

    @Mock
    private Dependency2 dependency2;

    private MyService service;

    @BeforeEach
    void setUp() {
        service = new MyService(dependency1, dependency2);
    }

    @Test
    void testSomething() {
        // Given
        when(dependency1.someMethod()).thenReturn(value);

        // When
        Result result = service.doSomething();

        // Then
        assertNotNull(result);
        verify(dependency1).someMethod();
    }
}
```

### Integration Test Structure

```java
class MyIntegrationTest {

    // Real objects (not mocks) for integration testing
    private BattleService battleService;
    private PlayerCharacter character1;
    private PlayerCharacter character2;

    @BeforeEach
    void setUp() {
        // Setup real or semi-real objects
    }

    @Test
    void testCompleteFlow() {
        // Execute multiple operations in sequence
        // Verify end-to-end behavior
    }
}
```

### Property-Based Test Structure

```java
class MyPropertyTest {

    @ParameterizedTest
    @MethodSource("provideTestData")
    void testPropertyHolds(int input1, int input2) {
        // Verify invariant holds for all inputs
    }

    static Stream<Arguments> provideTestData() {
        return Stream.of(
            Arguments.of(1, 2),
            Arguments.of(3, 4),
            // ... many combinations
        );
    }
}
```

---

## Writing New Tests

### 1. Choose Test Type

- **Unit test:** Testing single method/class in isolation
- **Integration test:** Testing multiple components together
- **Property test:** Verifying invariants across many inputs
- **Concurrency test:** Testing thread-safety

### 2. Follow Naming Conventions

**Test Class:** `{ClassName}Test.java` or `{Feature}IntegrationTest.java`

**Test Method:** `test{WhatIsBeingTested}` or `{scenario}Should{ExpectedBehavior}`

**Examples:**
```java
testAttackWithHighStrength()
attackShouldDealDamage()
criticalHitShouldDealDoubleDamage()
```

### 3. Use AAA Pattern

```java
@Test
void testSomething() {
    // Arrange (Given)
    PlayerCharacter character = createTestCharacter();

    // Act (When)
    int damage = character.calculateDamage();

    // Assert (Then)
    assertTrue(damage > 0);
}
```

### 4. Write Clear Assertions

```java
// Good: Clear message
assertEquals(expected, actual, "HP should be 50 after taking 10 damage");

// Better: Use assertTrue/assertFalse with message
assertTrue(battle.isActive(), "Battle should be active after acceptance");

// Best: Multiple specific assertions
assertNotNull(battle);
assertTrue(battle.isActive());
assertEquals(2, battle.getTurnNumber());
```

### 5. Test Edge Cases

- Minimum values (0, 1, min score)
- Maximum values (max score, max HP)
- Boundary conditions (exactly at threshold)
- Invalid inputs (negative values, null)
- Empty collections
- Very large inputs

---

## Test Data Factories

### PlayerCharacterTestFactory

**Purpose:** Create test characters without Discord snowflake validation

**Usage:**
```java
PlayerCharacter character = PlayerCharacterTestFactory.create(
    "user123",      // userId (can be any string)
    "guild456",     // guildId (can be any string)
    "Warrior",      // characterClass
    "Human",        // race
    15, 10, 14,     // STR, DEX, CON
    10, 10, 10      // INT, WIS, CHA
);
```

**Why:** Production `PlayerCharacter` validates Discord snowflake IDs (15-22 digits). Test factory bypasses validation for easier testing.

### ActiveBattle.createPending()

**Purpose:** Create test battles

**Usage:**
```java
ActiveBattle battle = ActiveBattle.createPending(
    "guild123",
    "challenger456",
    "opponent789"
);
```

---

## Common Test Patterns

### 1. Mock Setup Pattern

```java
@BeforeEach
void setUp() {
    when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
        .thenReturn(Optional.of(character1));
    when(characterRepository.findByUserIdAndGuildId("user2", "guild1"))
        .thenReturn(Optional.of(character2));
}
```

### 2. Exception Testing

```java
@Test
void shouldThrowExceptionForInvalidInput() {
    assertThrows(IllegalArgumentException.class, () ->
        service.doSomethingInvalid()
    );
}
```

### 3. Repeated Test (for randomness)

```java
@RepeatedTest(100)
void randomBehaviorShouldBeWithinBounds() {
    int roll = rollD20();
    assertTrue(roll >= 1 && roll <= 20);
}
```

### 4. Parameterized Test

```java
@ParameterizedTest
@ValueSource(ints = {8, 10, 12, 15, 18})
void testWithDifferentAbilityScores(int abilityScore) {
    // Test runs 5 times with different values
}
```

### 5. Verify Mock Interactions

```java
@Test
void shouldCallRepository() {
    service.saveCharacter(character);

    verify(characterRepository).save(character);
    verify(characterRepository, times(1)).save(any());
}
```

---

## Troubleshooting

### Test Fails: "Discord snowflake validation"

**Problem:** Using `new PlayerCharacter()` with test IDs

**Solution:** Use `PlayerCharacterTestFactory.create()` instead

```java
// Bad:
PlayerCharacter character = new PlayerCharacter("user123", "guild456", ...);

// Good:
PlayerCharacter character = PlayerCharacterTestFactory.create("user123", "guild456", ...);
```

### Test Fails: "NPE on mock"

**Problem:** Mock not configured

**Solution:** Add mock setup in `@BeforeEach`

```java
@BeforeEach
void setUp() {
    when(mockService.someMethod()).thenReturn(someValue);
}
```

### Test Fails: "UnnecessaryStubbingException"

**Problem:** Mock stubbed but never used

**Solution:** Remove unused stubbing or use `@MockitoSettings(strictness = Strictness.LENIENT)`

### Concurrency Test Flaky

**Problem:** Race conditions in test itself

**Solution:** Use proper synchronization (CountDownLatch, await with timeout)

```java
CountDownLatch latch = new CountDownLatch(3);
// ... execute concurrent operations ...
latch.await(5, TimeUnit.SECONDS);
```

### Integration Test Slow

**Problem:** Too many operations or network calls

**Solution:** Use mocks for expensive operations, focus on critical path

---

## Test Statistics

**Phase 13 Achievements:**

- **Total Tests:** 300+ tests
- **New Tests Added:** 24 tests
  - Property-based: 10 tests (100+ parameterized cases)
  - Concurrency: 6 tests
  - Integration: 8 tests
- **Test Success Rate:** 100% passing
- **Coverage:** High coverage across all battle system components

**Test Distribution:**
- Unit tests: ~85% (260+ tests)
- Integration tests: ~3% (8 tests)
- Property-based tests: ~10% (30+ tests with parameterization)
- Concurrency tests: ~2% (6 tests)

---

## Best Practices

1. **Keep tests fast** - Mock expensive operations
2. **Test one thing** - Each test should verify one behavior
3. **Use descriptive names** - Test name should explain what it tests
4. **Avoid test interdependence** - Tests should run independently
5. **Clean up resources** - Use `@AfterEach` if needed
6. **Test edge cases** - Min/max values, boundaries, nulls
7. **Use factories** - PlayerCharacterTestFactory for test data
8. **Verify mock interactions** - Use `verify()` to ensure methods called
9. **Test exceptions** - Use `assertThrows()` for error cases
10. **Document complex tests** - Add comments for non-obvious test logic

---

**Last Updated:** 2025-11-16
**Phase:** 13 - Test Suite Build-Out
