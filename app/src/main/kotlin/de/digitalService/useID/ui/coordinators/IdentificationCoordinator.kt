package de.digitalService.useID.ui.coordinators

import android.content.Context
import android.net.Uri
import com.ramcosta.composedestinations.spec.Direction
import dagger.hilt.android.qualifiers.ApplicationContext
import de.digitalService.useID.analytics.IssueTrackerManagerType
import de.digitalService.useID.analytics.TrackerManagerType
import de.digitalService.useID.getLogger
import de.digitalService.useID.idCardInterface.EIDInteractionEvent
import de.digitalService.useID.idCardInterface.IDCardInteractionException
import de.digitalService.useID.idCardInterface.IDCardManager
import de.digitalService.useID.models.ScanError
import de.digitalService.useID.ui.screens.destinations.*
import de.digitalService.useID.ui.screens.identification.ScanEvent
import de.digitalService.useID.util.CoroutineContextProviderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdentificationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appCoordinator: AppCoordinator,
    private val idCardManager: IDCardManager,
    private val trackerManager: TrackerManagerType,
    private val issueTrackerManager: IssueTrackerManagerType,
    private val coroutineContextProvider: CoroutineContextProviderType
) {
    private val logger by getLogger()

    private val _scanEventFlow: MutableStateFlow<ScanEvent> = MutableStateFlow(ScanEvent.CardRequested)
    val scanEventFlow: Flow<ScanEvent>
        get() = _scanEventFlow

    private var requestAuthenticationEvent: EIDInteractionEvent.RequestAuthenticationRequestConfirmation? = null
    private var pinCallback: ((String) -> Unit)? = null

    private var reachedScanState = false
    private var listenToEvents = false
    private var incorrectPin: Boolean = false
    var didSetup: Boolean = false
        private set

    fun startIdentificationProcess(tcTokenURL: String, didSetup: Boolean) {
        logger.debug("Start identification process.")
        this.didSetup = didSetup

        idCardManager.cancelTask()
        reachedScanState = false
        CoroutineScope(coroutineContextProvider.IO).launch {
            _scanEventFlow.emit(ScanEvent.CardRequested)
        }
        startIdentification(tcTokenURL)
    }

    fun confirmAttributesForIdentification() {
        val requestAuthenticationEvent = requestAuthenticationEvent ?: run {
            logger.error("Cannot confirm attributes because there isn't any authentication confirmation request event saved.")
            return
        }

        val requiredAttributes = requestAuthenticationEvent.request.readAttributes.filterValues { it }
        requestAuthenticationEvent.confirmationCallback(requiredAttributes)
    }

    fun onPINEntered(pin: String) {
        if (incorrectPin) {
            appCoordinator.pop()
        }

        val pinCallback = pinCallback ?: run {
            logger.error("Cannot process PIN because there isn't any pin callback saved.")
            return
        }
        logger.debug("Executing PIN callback.")
        pinCallback(pin)
        this.pinCallback = null
        incorrectPin = false
    }

    private fun onIncorrectPersonalPIN(attempts: Int) {
        incorrectPin = true
        navigateOnMain(IdentificationPersonalPINDestination(attempts))
    }

    fun pop() {
        appCoordinator.pop()
    }

    fun cancelIdentification() {
        logger.debug("Cancel identification process.")
        listenToEvents = false
        appCoordinator.stopNFCTagHandling()
        CoroutineScope(Dispatchers.Main).launch {
            if (didSetup) {
                appCoordinator.popUpTo(SetupIntroDestination)
            } else {
                appCoordinator.popToRoot()
            }
        }
        reachedScanState = false
        incorrectPin = false
        idCardManager.cancelTask()
    }

    private fun finishIdentification() {
        logger.debug("Finish identification process.")
        listenToEvents = false
        appCoordinator.setIsNotFirstTimeUser()
        CoroutineScope(Dispatchers.Main).launch {
            appCoordinator.popToRoot()
        }
        reachedScanState = false
        incorrectPin = false
        trackerManager.trackEvent(category = "identification", action = "success", name = "")
    }

    private fun startIdentification(tcTokenURL: String) {
        listenToEvents = true

        val fullURL = Uri
            .Builder()
            .scheme("http")
            .encodedAuthority("127.0.0.1:24727")
            .appendPath("eID-Client")
            .appendQueryParameter("tcTokenURL", tcTokenURL)
            .build()
            .toString()

        CoroutineScope(coroutineContextProvider.IO).launch {
            idCardManager.identify(context, fullURL).catch { error ->
                logger.error("Identification error: $error")

                if (!listenToEvents) {
                    logger.debug("Emit: Ignoring error because the coordinator is not listening anymore.")
                    return@catch
                }

                when (error) {
                    IDCardInteractionException.CardDeactivated -> {
                        trackerManager.trackScreen("identification/cardDeactivated")

                        _scanEventFlow.emit(ScanEvent.Error(ScanError.CardDeactivated))
                        navigateOnMain(IdentificationCardDeactivatedDestination)
                    }
                    IDCardInteractionException.CardBlocked -> {
                        trackerManager.trackScreen("identification/cardUnreadable")

                        _scanEventFlow.emit(ScanEvent.Error(ScanError.PINBlocked))
                        navigateOnMain(IdentificationCardBlockedDestination)
                    }
                    is IDCardInteractionException.ProcessFailed -> {
                        if (reachedScanState) {
                            val scanEvent = if (error.redirectUrl != null) {
                                navigateOnMain(IdentificationCardUnreadableDestination(true, error.redirectUrl))
                                ScanEvent.Error(ScanError.CardErrorWithRedirect(error.redirectUrl))
                            } else {
                                navigateOnMain(IdentificationCardUnreadableDestination(true, null))
                                ScanEvent.Error(ScanError.CardErrorWithoutRedirect)
                            }
                            _scanEventFlow.emit(scanEvent)
                        } else {
                            navigateOnMain(IdentificationOtherErrorDestination(tcTokenURL))
                        }
                    }
                    else -> {
                        navigateOnMain(IdentificationOtherErrorDestination(tcTokenURL))
                        _scanEventFlow.emit(ScanEvent.Error(ScanError.Other(null)))

                        if (pinCallback == null && !reachedScanState) {
                            trackerManager.trackEvent(category = "identification", action = "loadingFailed", name = "attributes")

                            (error as? IDCardInteractionException)?.redacted?.let {
                                issueTrackerManager.capture(it)
                            }
                        }
                    }
                }
            }.collect { event ->
                if (!listenToEvents) {
                    logger.debug("Emit: Ignoring event because the coordinator is not listening anymore.")
                    return@collect
                }

                when (event) {
                    EIDInteractionEvent.AuthenticationStarted -> logger.debug("Authentication started")
                    is EIDInteractionEvent.RequestAuthenticationRequestConfirmation -> {
                        logger.debug(
                            "Requesting authentication confirmation:\n" +
                                "${event.request.subject}\n" +
                                "Read attributes: ${event.request.readAttributes.keys}"
                        )

                        requestAuthenticationEvent = event

                        navigateOnMain(IdentificationAttributeConsentDestination(event.request))
                    }
                    is EIDInteractionEvent.RequestPIN -> {
                        logger.debug("Requesting PIN")

                        pinCallback = event.pinCallback

                        if (event.attempts == null) {
                            logger.debug("PIN request without attempts")
                            navigateOnMain(IdentificationPersonalPINDestination(null))
                        } else {
                            logger.debug("PIN request with ${event.attempts} attempts")
                            _scanEventFlow.emit(ScanEvent.CardRequested)
                            onIncorrectPersonalPIN(event.attempts)
                        }
                    }
                    is EIDInteractionEvent.RequestCAN -> {
                        logger.debug("Requesting CAN")
                        _scanEventFlow.emit(ScanEvent.Error(ScanError.PINSuspended))
                        navigateOnMain(IdentificationCardSuspendedDestination)

                        trackerManager.trackScreen("identification/cardSuspended")
                        cancel()
                    }
                    is EIDInteractionEvent.RequestPINAndCAN -> {
                        logger.debug("Requesting PIN and CAN")
                        _scanEventFlow.emit(ScanEvent.Error(ScanError.PINSuspended))
                        navigateOnMain(IdentificationCardSuspendedDestination)

                        trackerManager.trackScreen("identification/cardSuspended")
                        cancel()
                    }
                    is EIDInteractionEvent.RequestPUK -> {
                        logger.debug("Requesting PUK")
                        _scanEventFlow.emit(ScanEvent.Error(ScanError.PINBlocked))
                        navigateOnMain(IdentificationCardBlockedDestination)

                        trackerManager.trackScreen("identification/cardBlocked")
                        cancel()
                    }
                    EIDInteractionEvent.RequestCardInsertion -> {
                        logger.debug("Requesting ID card")
                        if (!reachedScanState) {
                            navigateOnMain(IdentificationScanDestination)
                        }
                        appCoordinator.startNFCTagHandling()
                    }
                    EIDInteractionEvent.CardRecognized -> {
                        logger.debug("Card recognized")
                        _scanEventFlow.emit(ScanEvent.CardAttached)
                        reachedScanState = true
                    }
                    is EIDInteractionEvent.ProcessCompletedSuccessfullyWithRedirect -> {
                        logger.debug("Process completed successfully")
                        _scanEventFlow.emit(ScanEvent.Finished(event.redirectURL))

                        finishIdentification()
                    }
                    is EIDInteractionEvent.CardInteractionComplete -> {
                        logger.debug("Card interaction complete.")
                        appCoordinator.stopNFCTagHandling()
                    }
                    else -> {
                        logger.debug("Unhandled authentication event: $event")
                        issueTrackerManager.capture(event.redacted)
                    }
                }
            }
        }
    }

    private fun navigateOnMain(direction: Direction) {
        CoroutineScope(Dispatchers.Main).launch { appCoordinator.navigate(direction) }
    }
}
