package com.gmail.nossr50.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.MMOTestEnvironment;
import com.gmail.nossr50.api.exceptions.InvalidSkillException;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.Permissions;
import java.util.function.BiPredicate;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Covers {@code %mcmmo_xprate_<skill>%}, which must report the same XP perk multiplier that
 * {@code PerksUtils.handleXpPerks} applies. The placeholder once kept its own copy of the perk
 * tiers and missed the 1.25x one (GitHub issue #5355).
 */
class PapiExpansionSkillXpRateTest extends MMOTestEnvironment {
    private static final Logger logger = Logger.getLogger(
            PapiExpansionSkillXpRateTest.class.getName());
    private static final PrimarySkillType SKILL = PrimarySkillType.MINING;

    private PapiExpansion expansion;

    @BeforeEach
    void setUp() throws InvalidSkillException {
        mockBaseEnvironment(logger);
        expansion = new PapiExpansion();
    }

    @AfterEach
    void tearDown() {
        cleanUpStaticMocks();
    }

    private void grant(BiPredicate<Permissible, PrimarySkillType> permissionCheck) {
        mockedPermissions.when(() -> permissionCheck.test(player, SKILL)).thenReturn(true);
    }

    @Nested
    class FixedTiers {
        static Stream<Arguments> tiers() {
            return Stream.of(
                    Arguments.of("4x", (BiPredicate<Permissible, PrimarySkillType>)
                            Permissions::quadrupleXp, "4.0"),
                    Arguments.of("3x", (BiPredicate<Permissible, PrimarySkillType>)
                            Permissions::tripleXp, "3.0"),
                    Arguments.of("2.5x", (BiPredicate<Permissible, PrimarySkillType>)
                            Permissions::doubleAndOneHalfXp, "2.5"),
                    Arguments.of("2x", (BiPredicate<Permissible, PrimarySkillType>)
                            Permissions::doubleXp, "2.0"),
                    Arguments.of("1.5x", (BiPredicate<Permissible, PrimarySkillType>)
                            Permissions::oneAndOneHalfXp, "1.5"),
                    Arguments.of("1.25x", (BiPredicate<Permissible, PrimarySkillType>)
                            Permissions::oneAndAQuarterXp, "1.25"),
                    Arguments.of("1.1x", (BiPredicate<Permissible, PrimarySkillType>)
                            Permissions::oneAndOneTenthXp, "1.1"),
                    Arguments.of("no perk", null, "1.0")
            );
        }

        @ParameterizedTest(name = "{0} perk should report {2}")
        @MethodSource("tiers")
        void shouldReportMultiplierOfTheOnlyTierHeld(String tierName,
                @Nullable BiPredicate<Permissible, PrimarySkillType> permissionCheck,
                String expectedRate) {
            // Given - a player holding at most one fixed XP perk tier for the skill
            if (permissionCheck != null) {
                grant(permissionCheck);
            }

            // When - the placeholder is resolved for that skill
            final String rate = expansion.getSkillXpRate(SKILL, player);

            // Then - it reports that tier's multiplier in String.valueOf(double) form
            assertThat(rate).isEqualTo(expectedRate);
        }

        @Test
        void shouldReportStrongerTierWhenPlayerHoldsTwo() {
            // Given - a player holding both the 2x and the 1.25x perk, as stacked perk groups
            // commonly grant
            grant(Permissions::doubleXp);
            grant(Permissions::oneAndAQuarterXp);

            // When - the placeholder is resolved
            final String rate = expansion.getSkillXpRate(SKILL, player);

            // Then - the stronger tier wins, matching the XP actually applied
            assertThat(rate).isEqualTo("2.0");
        }

        @Test
        void shouldReportOneAndAQuarterWhenAlsoHoldingOneAndOneTenth() {
            // Given - 1.25x and 1.1x held together; the two neighbouring tiers that the old
            // placeholder chain collapsed into 1.1
            grant(Permissions::oneAndAQuarterXp);
            grant(Permissions::oneAndOneTenthXp);

            // When - the placeholder is resolved
            final String rate = expansion.getSkillXpRate(SKILL, player);

            // Then - 1.25 is reported
            assertThat(rate).isEqualTo("1.25");
        }
    }

    @Nested
    class CustomBoost {
        @Test
        void shouldBeatFixedTierWhenPlayerHoldsBoth() {
            // Given - a configured custom boost the player holds alongside the 4x tier
            when(experienceConfigInstance.getCustomXpPerkBoost()).thenReturn(1.75);
            grant(Permissions::customXpBoost);
            grant(Permissions::quadrupleXp);

            // When - the placeholder is resolved
            final String rate = expansion.getSkillXpRate(SKILL, player);

            // Then - the custom boost is reported even though 4x is larger, because XP gain
            // checks the custom boost first
            assertThat(rate).isEqualTo("1.75");
        }
    }

    @Nested
    class MissingUser {
        @Test
        void shouldReturnNullWhenPlayerHasNoMcMMOData() {
            // Given - a player UserManager has no loaded data for, holding a perk anyway
            final Player unloadedPlayer = mock(Player.class);
            mockedPermissions.when(() -> Permissions.doubleXp(unloadedPlayer, SKILL))
                    .thenReturn(true);

            // When - the placeholder is resolved for that player
            final String rate = expansion.getSkillXpRate(SKILL, unloadedPlayer);

            // Then - null tells PlaceholderAPI there is no value yet
            assertThat(rate).isNull();
        }
    }
}
