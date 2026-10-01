package ru.pravbeseda.sleepnoise

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/** The credits item in the overflow menu opens the credits, and their close button closes them. */
@RunWith(AndroidJUnit4::class)
class CreditsDialogUiTest {
    @Test
    fun theCreditsOpenFromTheMenuAndCloseWithTheirButton() {
        ActivityScenario.launch(MainActivity::class.java).use { screen ->
            openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().targetContext)
            onView(withText(screen.read { it.getString(R.string.credits) })).perform(click())

            onView(withId(R.id.btn_close)).inRoot(isDialog()).check(matches(isDisplayed())).perform(click())

            onView(withId(R.id.btn_close)).check(doesNotExist())
        }
    }
}
