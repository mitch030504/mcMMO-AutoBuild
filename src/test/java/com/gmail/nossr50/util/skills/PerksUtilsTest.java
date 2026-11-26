package com.gmail.nossr50.util.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.MMOTestEnvironment;
import com.gmail.nossr50.api.exceptions.InvalidSkillException;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.Permissions;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link PerksUtils#handleXpPerks}, which takes its multiplier from
 * {@link PerksUtils#getXpPerkMultiplier}: the XP it applies, and the debug-mode "IS CUSTOM" line
 * that must only print for the custom boost.
 */
class PerksUtilsTest extends MMOTestEnvironment {
    private static final Logger logger = Logger.getLogger(PerksUtilsTest.class.getName());
    private static final PrimarySkillType SKILL = PrimarySkillType.MINING;
    private static final String CUSTOM_DEBUG_LINE = "XP Perk Multiplier IS CUSTOM!";

    @BeforeEach
    void setUp() throws InvalidSkillException {
        mockBaseEnvironment(logger);
    }

    @AfterEach
    void tearDown() {
        cleanUpStaticMocks();
    }

    @Nested
    class HandleXpPerks {
        @Test
        void shouldApplyOneAndAQuarterTier() {
            // Given - a player whose only XP perk is the 1.25x tier
            when(Permissions.oneAndAQuarterXp(player, SKILL)).thenReturn(true);

            // When - 100 XP passes through the perk handler
            final float boostedXp = PerksUtils.handleXpPerks(player, 100, SKILL);

            // Then - the XP is scaled by 1.25
            assertThat(boostedXp).isEqualTo(125F);
        }

        @Test
        void shouldApplyCustomBoostAndPrintCustomLineWhenDebugging() {
            // Given - a debugging player holding the custom boost alongside the 4x tier
            doReturn(true).when(mmoPlayer).isDebugMode();
            when(experienceConfigInstance.getCustomXpPerkBoost()).thenReturn(1.5);
            when(Permissions.customXpBoost(player, SKILL)).thenReturn(true);
            when(Permissions.quadrupleXp(player, SKILL)).thenReturn(true);

            // When - 100 XP passes through the perk handler
            final float boostedXp = PerksUtils.handleXpPerks(player, 100, SKILL);

            // Then - the custom boost wins and the debug output says so
            assertThat(boostedXp).isEqualTo(150F);
            verify(player).sendMessage(contains(CUSTOM_DEBUG_LINE));
        }

        @Test
        void shouldNotPrintCustomLineWhenDebuggingWithFixedTier() {
            // Given - a debugging player holding only the 2x tier
            doReturn(true).when(mmoPlayer).isDebugMode();
            when(Permissions.doubleXp(player, SKILL)).thenReturn(true);

            // When - 100 XP passes through the perk handler
            final float boostedXp = PerksUtils.handleXpPerks(player, 100, SKILL);

            // Then - the XP doubles and the custom line, reserved for the custom boost, is absent
            assertThat(boostedXp).isEqualTo(200F);
            verify(player, never()).sendMessage(contains(CUSTOM_DEBUG_LINE));
        }
    }
}
