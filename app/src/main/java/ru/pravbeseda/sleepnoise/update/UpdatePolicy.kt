package ru.pravbeseda.sleepnoise.update

/** No version declined yet: every real versionCode is above it. */
const val NO_DECLINED_VERSION = 0

/** Play's install status, reduced to what the screen acts on. */
enum class InstallProgress { NONE, RUNNING, DOWNLOADED }

enum class UpdateAction { NOTHING, OFFER, RESTART }

/**
 * What the screen does about a newer version while nothing plays. Offered once per version: a dismissed
 * offer is recorded as [declinedVersionCode] and only a higher code is offered again.
 *
 * @param offerable Play has a newer version and allows the flexible flow for it.
 */
fun updateAction(offerable: Boolean, availableVersionCode: Int, declinedVersionCode: Int, install: InstallProgress): UpdateAction = when {
    install == InstallProgress.DOWNLOADED -> UpdateAction.RESTART
    install == InstallProgress.RUNNING -> UpdateAction.NOTHING
    offerable && availableVersionCode > declinedVersionCode -> UpdateAction.OFFER
    else -> UpdateAction.NOTHING
}
