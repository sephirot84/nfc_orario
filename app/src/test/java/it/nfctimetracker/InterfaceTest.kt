package it.nfctimetracker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = TrackerApplication::class)
class InterfaceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun terminalAndEmployeeCreationWorkAcrossActivityRecreation() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("AVVICINA IL TAG NFC").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("AVVICINA IL TAG NFC").assertIsDisplayed()
        compose.onNodeWithContentDescription("Amministrazione").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Aggiungi dipendente").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Aggiungi dipendente").performClick()
        compose.onNodeWithText("Nome").performTextInput("Mario")
        compose.onNodeWithText("Cognome").performTextInput("Rossi")
        compose.onNodeWithText("Salva").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Mario Rossi").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Mario Rossi").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Mario Rossi").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Mario Rossi").assertIsDisplayed()
        compose.onNodeWithContentDescription("Torna al terminale").performClick()
        compose.onNodeWithText("AVVICINA IL TAG NFC").assertIsDisplayed()
    }
}
