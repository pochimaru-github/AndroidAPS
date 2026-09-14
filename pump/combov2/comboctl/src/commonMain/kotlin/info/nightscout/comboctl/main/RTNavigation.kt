package info.nightscout.comboctl.main

import info.nightscout.comboctl.base.ApplicationLayer
import info.nightscout.comboctl.base.ComboException
import info.nightscout.comboctl.base.Graph
import info.nightscout.comboctl.base.LogLevel
import info.nightscout.comboctl.base.Logger
import info.nightscout.comboctl.base.PumpIO
import info.nightscout.comboctl.base.connectBidirectionally
import info.nightscout.comboctl.base.connectDirectionally
import info.nightscout.comboctl.base.findShortestPath
import info.nightscout.comboctl.base.getElapsedTimeInMs
import info.nightscout.comboctl.parser.ParsedDisplayFrame
import info.nightscout.comboctl.parser.ParsedScreen
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.reflect.KClassifier

private val logger = Logger.get("RTNavigation")

private const val MINIMUM_WAIT_PERIOD_DURING_LONG_RT_BUTTON_PRESS_IN_MS = 110L
private const val MAXIMUM_WAIT_PERIOD_DURING_LONG_RT_BUTTON_PRESS_IN_MS = 600L
private const val MAX_NUM_SAME_QUANTITY_OBSERVATIONS = 10

enum class RTNavigationButton(val rtButtonCodes: List<ApplicationLayer.RTButton>) {
    UP(listOf(ApplicationLayer.RTButton.UP)),
    DOWN(listOf(ApplicationLayer.RTButton.DOWN)),
    MENU(listOf(ApplicationLayer.RTButton.MENU)),
    CHECK(listOf(ApplicationLayer.RTButton.CHECK)),

    BACK(listOf(ApplicationLayer.RTButton.MENU, ApplicationLayer.RTButton.UP)),
    UP_DOWN(listOf(ApplicationLayer.RTButton.UP, ApplicationLayer.RTButton.DOWN))
}

internal data class RTEdgeValue(val button: RTNavigationButton, val edgeValidityCondition: EdgeValidityCondition = EdgeValidityCondition.ALWAYS) {
    enum class EdgeValidityCondition {
        ONLY_IF_COMBO_STOPPED,
        ONLY_IF_COMBO_RUNNING,
        ALWAYS
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as RTEdgeValue

        return button == other.button
    }

    override fun hashCode(): Int {
        return button.hashCode()
    }
}

internal val rtNavigationGraph = Graph<KClassifier, RTEdgeValue>().apply {
    val mainNode = node(ParsedScreen.MainScreen::class)
    val quickinfoNode = node(ParsedScreen.QuickinfoMainScreen::class)
    val tbrMenuNode = node(ParsedScreen.TemporaryBasalRateMenuScreen::class)
    val tbrPercentageNode = node(ParsedScreen.TemporaryBasalRatePercentageScreen::class)
    val tbrDurationNode = node(ParsedScreen.TemporaryBasalRateDurationScreen::class)
    val myDataMenuNode = node(ParsedScreen.MyDataMenuScreen::class)
    val myDataBolusDataMenuNode = node(ParsedScreen.MyDataBolusDataScreen::class)
    val myDataErrorDataMenuNode = node(ParsedScreen.MyDataErrorDataScreen::class)
    val myDataDailyTotalsMenuNode = node(ParsedScreen.MyDataDailyTotalsScreen::class)
    val myDataTbrDataMenuNode = node(ParsedScreen.MyDataTbrDataScreen::class)
    val basalRate1MenuNode = node(ParsedScreen.BasalRate1ProgrammingMenuScreen::class)
    val basalRateTotalNode = node(ParsedScreen.BasalRateTotalScreen::class)
    val basalRateFactorSettingNode = node(ParsedScreen.BasalRateFactorSettingScreen::class)
    val timeDateSettingsMenuNode = node(ParsedScreen.TimeAndDateSettingsMenuScreen::class)
    val timeDateSettingsHourNode = node(ParsedScreen.TimeAndDateSettingsHourScreen::class)
    val timeDateSettingsMinuteNode = node(ParsedScreen.TimeAndDateSettingsMinuteScreen::class)
    val timeDateSettingsYearNode = node(ParsedScreen.TimeAndDateSettingsYearScreen::class)
    val timeDateSettingsMonthNode = node(ParsedScreen.TimeAndDateSettingsMonthScreen::class)
    val timeDateSettingsDayNode = node(ParsedScreen.TimeAndDateSettingsDayScreen::class)

    connectBidirectionally(RTEdgeValue(RTNavigationButton.CHECK), RTEdgeValue(RTNavigationButton.BACK), mainNode, quickinfoNode)

    connectBidirectionally(
        RTEdgeValue(RTNavigationButton.MENU), RTEdgeValue(RTNavigationButton.BACK),
        myDataMenuNode, basalRate1MenuNode
    )

    connectBidirectionally(
        RTEdgeValue(RTNavigationButton.BACK), RTEdgeValue(RTNavigationButton.MENU),
        mainNode,
        timeDateSettingsMenuNode
    )

    connectBidirectionally(
        RTEdgeValue(RTNavigationButton.MENU, RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_RUNNING),
        RTEdgeValue(RTNavigationButton.BACK, RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_RUNNING),
        mainNode, tbrMenuNode
    )
    connectBidirectionally(
        RTEdgeValue(RTNavigationButton.MENU, RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_RUNNING),
        RTEdgeValue(RTNavigationButton.BACK, RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_RUNNING),
        tbrMenuNode, myDataMenuNode
    )

    connectBidirectionally(
        RTEdgeValue(RTNavigationButton.MENU, RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_STOPPED),
        RTEdgeValue(RTNavigationButton.BACK, RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_STOPPED),
        mainNode, myDataMenuNode
    )

    connectBidirectionally(RTEdgeValue(RTNavigationButton.CHECK), RTEdgeValue(RTNavigationButton.BACK), tbrMenuNode, tbrPercentageNode)
    connectBidirectionally(RTEdgeValue(RTNavigationButton.MENU), RTEdgeValue(RTNavigationButton.MENU), tbrPercentageNode, tbrDurationNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), tbrDurationNode, tbrMenuNode)

    connectBidirectionally(RTEdgeValue(RTNavigationButton.CHECK), RTEdgeValue(RTNavigationButton.BACK), basalRate1MenuNode, basalRateTotalNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.MENU), basalRateTotalNode, basalRateFactorSettingNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), basalRateFactorSettingNode, basalRate1MenuNode)

    connectDirectionally(RTEdgeValue(RTNavigationButton.CHECK), myDataMenuNode, myDataBolusDataMenuNode)
    connectDirectionally(
        RTEdgeValue(RTNavigationButton.MENU),
        myDataBolusDataMenuNode, myDataErrorDataMenuNode, myDataDailyTotalsMenuNode, myDataTbrDataMenuNode
    )
    connectDirectionally(RTEdgeValue(RTNavigationButton.MENU), myDataTbrDataMenuNode, myDataBolusDataMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), myDataBolusDataMenuNode, myDataMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), myDataErrorDataMenuNode, myDataMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), myDataDailyTotalsMenuNode, myDataMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), myDataTbrDataMenuNode, myDataMenuNode)

    connectDirectionally(RTEdgeValue(RTNavigationButton.CHECK), timeDateSettingsMenuNode, timeDateSettingsHourNode)
    connectDirectionally(
        RTEdgeValue(RTNavigationButton.MENU),
        timeDateSettingsHourNode, timeDateSettingsMinuteNode, timeDateSettingsYearNode,
        timeDateSettingsMonthNode, timeDateSettingsDayNode
    )
    connectDirectionally(RTEdgeValue(RTNavigationButton.MENU), timeDateSettingsDayNode, timeDateSettingsHourNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), timeDateSettingsHourNode, timeDateSettingsMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), timeDateSettingsMinuteNode, timeDateSettingsMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), timeDateSettingsYearNode, timeDateSettingsMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), timeDateSettingsMonthNode, timeDateSettingsMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), timeDateSettingsDayNode, timeDateSettingsMenuNode)
}

open class RTNavigationException(message: String) : ComboException(message)

class CouldNotFindRTScreenException(val targetScreenType: KClassifier) :
    RTNavigationException("Could not find RT screen $targetScreenType")

class UnexpectedRTScreenException(
    val expectedScreenType: KClassifier,
    val encounteredScreenType: KClassifier
) : RTNavigationException("Unexpected RT screen; expected $expectedScreenType, encountered $encounteredScreenType")

class CouldNotRecognizeAnyRTScreenException : RTNavigationException("Could not recognize any RT screen")

class NoUsableRTScreenException : RTNavigationException("No usable RT screen available")

class QuantityNotChangingException(
    val targetQuantity: Int,
    val hitLimitAt: Int
) : RTNavigationException("Attempted to adjust quantity to target value $targetQuantity, but hit limit at $hitLimitAt")

interface RTNavigationContext {
    val maxNumCycleAttempts: Int

    fun resetDuplicate()

    suspend fun getParsedDisplayFrame(filterDuplicates: Boolean, processAlertScreens: Boolean = true): ParsedDisplayFrame?

    suspend fun startLongButtonPress(button: RTNavigationButton, keepGoing: (suspend () -> Boolean)? = null)
    suspend fun stopLongButtonPress()
    suspend fun waitForLongButtonPressToFinish()
    suspend fun shortPressButton(button: RTNavigationButton)
}

class RTNavigationContextProduction(
    private val pumpIO: PumpIO,
    override val maxNumCycleAttempts: Int = 20
) : RTNavigationContext {
    init {
        require(maxNumCycleAttempts > 0)
    }

    override fun resetDuplicate() {}

    override suspend fun getParsedDisplayFrame(filterDuplicates: Boolean, processAlertScreens: Boolean): ParsedDisplayFrame? = null

    override suspend fun startLongButtonPress(button: RTNavigationButton, keepGoing: (suspend () -> Boolean)?) =
        pumpIO.startLongRTButtonPress(button.rtButtonCodes, keepGoing)

    override suspend fun stopLongButtonPress() = pumpIO.stopLongRTButtonPress()

    override suspend fun waitForLongButtonPressToFinish() = pumpIO.waitForLongRTButtonPressToFinish()

    override suspend fun shortPressButton(button: RTNavigationButton) = pumpIO.sendShortRTButtonPress(button.rtButtonCodes)
}

sealed class ShortPressRTButtonsCommand {
    object DoNothing : ShortPressRTButtonsCommand()
    object Stop : ShortPressRTButtonsCommand()
    data class PressButton(val button: RTNavigationButton) : ShortPressRTButtonsCommand()
}

sealed class LongPressRTButtonsCommand {
    object ContinuePressingButton : LongPressRTButtonsCommand()
    object ReleaseButton : LongPressRTButtonsCommand()
}

suspend fun longPressRTButtonUntil(
    rtNavigationContext: RTNavigationContext,
    button: RTNavigationButton,
    checkScreen: (parsedScreen: ParsedScreen) -> LongPressRTButtonsCommand
): ParsedScreen {
    lateinit var lastParsedScreen: ParsedScreen

    logger(LogLevel.DEBUG) { "Long-pressing RT button $button" }

    rtNavigationContext.resetDuplicate()

    var thrownDuringButtonPress: Throwable? = null

    rtNavigationContext.startLongButtonPress(button) {
        val timestampBeforeDisplayFrameRetrieval = getElapsedTimeInMs()

        val parsedDisplayFrame = try {
            withTimeout(
                timeMillis = MAXIMUM_WAIT_PERIOD_DURING_LONG_RT_BUTTON_PRESS_IN_MS
            ) {
                rtNavigationContext.getParsedDisplayFrame(filterDuplicates = true)
            }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (t: Throwable) {
            thrownDuringButtonPress = t
            return@startLongButtonPress false
        } ?: return@startLongButtonPress true

        val elapsedTime = getElapsedTimeInMs() - timestampBeforeDisplayFrameRetrieval
        if (elapsedTime < MINIMUM_WAIT_PERIOD_DURING_LONG_RT_BUTTON_PRESS_IN_MS) {
            val waitingPeriodInMs = MINIMUM_WAIT_PERIOD_DURING_LONG_RT_BUTTON_PRESS_IN_MS - elapsedTime
            logger(LogLevel.VERBOSE) { "Waiting $waitingPeriodInMs milliseconds before continuing button long-press" }
            delay(timeMillis = waitingPeriodInMs)
        }

        val parsedScreen = parsedDisplayFrame.parsedScreen
        val predicateResult = try {
            checkScreen(parsedScreen)
        } catch (t: Throwable) {
            thrownDuringButtonPress = t
            return@startLongButtonPress false
        }

        val releaseButton = (predicateResult == LongPressRTButtonsCommand.ReleaseButton)
        logger(LogLevel.VERBOSE) {
            "Observed parsed screen $parsedScreen while long-pressing RT button; predicate result = $predicateResult"
        }
        if (releaseButton) {
            lastParsedScreen = parsedScreen
            return@startLongButtonPress false
        } else
            return@startLongButtonPress true
    }

    rtNavigationContext.waitForLongButtonPressToFinish()

    thrownDuringButtonPress?.let {
        logger(LogLevel.INFO) { "Rethrowing Throwable caught during long RT button press: $it" }
        throw it
    }

    logger(LogLevel.DEBUG) { "Long-pressing RT button $button stopped" }

    return lastParsedScreen
}

suspend fun shortPressRTButtonsUntil(
    rtNavigationContext: RTNavigationContext,
    processScreen: (parsedScreen: ParsedScreen) -> ShortPressRTButtonsCommand
): ParsedScreen {
    logger(LogLevel.DEBUG) { "Repeatedly short-pressing RT button according to callback commands" }

    rtNavigationContext.resetDuplicate()

    while (true) {
        val parsedDisplayFrame = rtNavigationContext.getParsedDisplayFrame(filterDuplicates = true) ?: continue
        val parsedScreen = parsedDisplayFrame.parsedScreen

        logger(LogLevel.VERBOSE) { "Got new screen $parsedScreen" }

        val command = processScreen(parsedScreen)
        logger(LogLevel.VERBOSE) { "Short-press RT button callback returned $command" }

        when (command) {
            ShortPressRTButtonsCommand.DoNothing -> Unit
            ShortPressRTButtonsCommand.Stop -> return parsedScreen
            is ShortPressRTButtonsCommand.PressButton -> rtNavigationContext.shortPressButton(command.button)
        }
    }
}

suspend fun cycleToRTScreen(
    rtNavigationContext: RTNavigationContext,
    button: RTNavigationButton,
    targetScreenType: KClassifier
): ParsedScreen {
    logger(LogLevel.DEBUG) { "Running shortPressRTButtonsUntil() until screen of type $targetScreenType is observed" }
    var cycleCount = 0
    return shortPressRTButtonsUntil(rtNavigationContext) { parsedScreen ->
        if (cycleCount >= rtNavigationContext.maxNumCycleAttempts)
            throw CouldNotFindRTScreenException(targetScreenType)

        when (parsedScreen::class) {
            targetScreenType -> {
                logger(LogLevel.DEBUG) { "Target screen of type $targetScreenType reached; cycleCount = $cycleCount" }
                ShortPressRTButtonsCommand.Stop
            }
            else -> {
                cycleCount++
                logger(LogLevel.VERBOSE) { "Did not yet reach target screen type; cycleCount increased to $cycleCount" }
                ShortPressRTButtonsCommand.PressButton(button)
            }
        }
    }
}

suspend fun waitUntilScreenAppears(
    rtNavigationContext: RTNavigationContext,
    targetScreenType: KClassifier
): ParsedScreen {
    logger(LogLevel.DEBUG) { "Observing incoming parsed screens and waiting for screen of type $targetScreenType to appear" }
    var cycleCount = 0

    rtNavigationContext.resetDuplicate()

    while (true) {
        if (cycleCount >= rtNavigationContext.maxNumCycleAttempts)
            throw CouldNotFindRTScreenException(targetScreenType)

        val parsedDisplayFrame = rtNavigationContext.getParsedDisplayFrame(filterDuplicates = true) ?: continue
        val parsedScreen = parsedDisplayFrame.parsedScreen

        if (parsedScreen::class == targetScreenType) {
            logger(LogLevel.DEBUG) { "Target screen of type $targetScreenType appeared; cycleCount = $cycleCount" }
            return parsedScreen
        } else {
            logger(LogLevel.VERBOSE) { "Target screen type did not appear yet; cycleCount increased to $cycleCount" }
            cycleCount++
        }
    }
}

suspend fun adjustQuantityOnScreen(
    rtNavigationContext: RTNavigationContext,
    targetQuantity: Int,
    incrementButton: RTNavigationButton = RTNavigationButton.UP,
    decrementButton: RTNavigationButton = RTNavigationButton.DOWN,
    cyclicQuantityRange: Int? = null,
    longRTButtonPressPredicate: (targetQuantity: Int, quantityOnScreen: Int) -> Boolean = { _, _ -> true },
    incrementSteps: Array<Pair<Int, Int>>,
    getQuantity: (parsedScreen: ParsedScreen) -> Int?
) {
    require(incrementSteps.isNotEmpty()) { "There must be at least one incrementSteps item" }
    require((cyclicQuantityRange == null) || (incrementSteps.size == 1)) {
        "If cyclicQuantityRange is not null, incrementSteps must contain " +
        "exactly one item; actually contains ${incrementSteps.size}"
    }

    fun checkIfNeedsToIncrement(currentQuantity: Int): Boolean {
        return if (cyclicQuantityRange != null) {
            val distance = (targetQuantity - currentQuantity)
            if (distance.absoluteValue <= (cyclicQuantityRange / 2))
                (currentQuantity < targetQuantity)
            else
                (currentQuantity > targetQuantity)
        } else
            (currentQuantity < targetQuantity)
    }

    logger(LogLevel.DEBUG) {
        "Adjusting quantity on RT screen; targetQuantity = $targetQuantity; " +
        "increment / decrement buttons = $incrementButton / $decrementButton; " +
        "cyclicQuantityRange = $cyclicQuantityRange"
    }

    var previouslySeenQuantity: Int? = null
    var seenSameQuantityCount = 0

    fun checkIfQuantityUnexpectedlyNotChanging(currentQuantity: Int): Boolean {
        if ((previouslySeenQuantity == null) || (previouslySeenQuantity != currentQuantity)) {
            previouslySeenQuantity = currentQuantity
            seenSameQuantityCount = 0
            return false
        }

        seenSameQuantityCount++

        return (seenSameQuantityCount >= MAX_NUM_SAME_QUANTITY_OBSERVATIONS)
    }

    val initialQuantity: Int
    rtNavigationContext.resetDuplicate()

    while (true) {
        val parsedDisplayFrame = rtNavigationContext.getParsedDisplayFrame(filterDuplicates = true) ?: continue
        val parsedScreen = parsedDisplayFrame.parsedScreen
        val quantity = getQuantity(parsedScreen)
        if (quantity != null) {
            initialQuantity = quantity
            break
        }
    }

    logger(LogLevel.DEBUG) { "Initial observed quantity: $initialQuantity" }

    if (initialQuantity == targetQuantity) {
        logger(LogLevel.DEBUG) { "Initial quantity is already the target quantity; nothing to do" }
        return
    }

    val currentQuantity: Int

    if (longRTButtonPressPredicate(targetQuantity, initialQuantity)) {
        val needToIncrement = checkIfNeedsToIncrement(initialQuantity)
        logger(LogLevel.DEBUG) {
            "First phase; long-pressing RT button to " +
                "${if (needToIncrement) "increment" else "decrement"} quantity"
        }

        longPressRTButtonUntil(
            rtNavigationContext,
            if (needToIncrement) incrementButton else decrementButton
        ) { parsedScreen ->
            val currentQuantityOnScreen = getQuantity(parsedScreen)
            logger(LogLevel.VERBOSE) { "Current quantity in first phase: $currentQuantityOnScreen; need to increment: $needToIncrement" }
            if (currentQuantityOnScreen == null) {
                LongPressRTButtonsCommand.ContinuePressingButton
            } else {
                if (currentQuantityOnScreen != targetQuantity) {
                    if (checkIfQuantityUnexpectedlyNotChanging(currentQuantityOnScreen)) {
                        logger(LogLevel.ERROR) { "Quantity unexpectedly not changing" }
                        throw QuantityNotChangingException(targetQuantity = targetQuantity, hitLimitAt = currentQuantityOnScreen)
                    }
                }

                val keepPressing =
                    if (currentQuantityOnScreen == targetQuantity)
                        false
                    else if (needToIncrement)
                        checkIfNeedsToIncrement(currentQuantityOnScreen)
                    else
                        !checkIfNeedsToIncrement(currentQuantityOnScreen)

                if (keepPressing)
                    LongPressRTButtonsCommand.ContinuePressingButton
                else
                    LongPressRTButtonsCommand.ReleaseButton
            }
        }

        var lastQuantity: Int? = null
        var sameQuantityObservedCount = 0
        rtNavigationContext.resetDuplicate()

        while (true) {
            val parsedDisplayFrame = rtNavigationContext.getParsedDisplayFrame(filterDuplicates = false) ?: continue
            val parsedScreen = parsedDisplayFrame.parsedScreen
            val currentQuantityOnScreen = getQuantity(parsedScreen)

            logger(LogLevel.DEBUG) {
                "Observed quantity after long-pressing RT button: " +
                    "last / current quantity: $lastQuantity / $currentQuantityOnScreen"
            }

            if (currentQuantityOnScreen != null) {
                if (currentQuantityOnScreen == lastQuantity) {
                    sameQuantityObservedCount++
                    if (sameQuantityObservedCount >= 3)
                        break
                } else {
                    lastQuantity = currentQuantityOnScreen
                    sameQuantityObservedCount = 0
                }
            }
        }

        if (lastQuantity == targetQuantity) {
            logger(LogLevel.DEBUG) { "Last seen quantity $lastQuantity is the target quantity; adjustment finished" }
            return
        }

        logger(LogLevel.DEBUG) {
            "Second phase: last seen quantity $lastQuantity is not the target quantity; " +
                "short-pressing RT button(s) to finetune it"
        }

        currentQuantity = lastQuantity!!
    } else {
        while (true) {
            val parsedDisplayFrame = rtNavigationContext.getParsedDisplayFrame(filterDuplicates = true) ?: continue
            val parsedScreen = parsedDisplayFrame.parsedScreen
            val quantity = getQuantity(parsedScreen)
            if (quantity != null) {
                currentQuantity = quantity
                break
            }
        }
    }

    val (numNeededShortRTButtonPresses: Int, shortRTButtonToPress) = computeShortRTButtonPress(
        currentQuantity = currentQuantity,
        targetQuantity = targetQuantity,
        cyclicQuantityRange = cyclicQuantityRange,
        incrementSteps = incrementSteps,
        incrementButton = incrementButton,
        decrementButton = decrementButton
    )
    if (numNeededShortRTButtonPresses != 0) {
        logger(LogLevel.DEBUG) {
            "Need to short-press the $shortRTButtonToPress " +
                    "RT button $numNeededShortRTButtonPresses time(s)"
        }
        repeat(numNeededShortRTButtonPresses) {
            while (true) {
                val displayFrame = rtNavigationContext.getParsedDisplayFrame(processAlertScreens = true, filterDuplicates = true)
                if ((displayFrame != null) && displayFrame.parsedScreen.isBlinkedOut) {
                    logger(LogLevel.DEBUG) { "Screen is blinked out (contents: ${displayFrame.parsedScreen}); skipping" }
                    continue
                }
                break
            }
            rtNavigationContext.shortPressButton(shortRTButtonToPress)
        }
    } else {
        logger(LogLevel.DEBUG) {
            "Quantity on screen is already equal to target quantity; no need to press any button"
        }
    }
}

suspend fun navigateToRTScreen(
    rtNavigationContext: RTNavigationContext,
    targetScreenType: KClassifier,
    isComboStopped: Boolean
): ParsedScreen {
    logger(LogLevel.DEBUG) { "About to navigate to RT screen of type $targetScreenType" }

    var numAttemptsToRecognizeScreen = 0
    lateinit var currentParsedScreen: ParsedScreen

    rtNavigationContext.resetDuplicate()

    while (true) {
        val parsedDisplayFrame = rtNavigationContext.getParsedDisplayFrame(filterDuplicates = true) ?: continue
        val parsedScreen = parsedDisplayFrame.parsedScreen

        if (parsedScreen is ParsedScreen.UnrecognizedScreen) {
            numAttemptsToRecognizeScreen++
            if (numAttemptsToRecognizeScreen >= rtNavigationContext.maxNumCycleAttempts)
                throw CouldNotRecognizeAnyRTScreenException()
            rtNavigationContext.shortPressButton(RTNavigationButton.BACK)
        } else {
            currentParsedScreen = parsedScreen
            break
        }
    }

    if (currentParsedScreen::class == targetScreenType) {
        logger(LogLevel.DEBUG) { "Already at target; exiting" }
        return currentParsedScreen
    }

    logger(LogLevel.DEBUG) { "Navigation starts at screen of type ${currentParsedScreen::class} and ends at screen of type $targetScreenType" }

    var path = try {
        findShortestRtPath(currentParsedScreen::class, targetScreenType, isComboStopped)
    } catch (e: IllegalArgumentException) {
        null
    }

    if (path?.isEmpty() == true)
        return currentParsedScreen

    if (path == null) {
        logger(LogLevel.WARN) {
            "We are at screen of type ${currentParsedScreen::class}, which is unknown " +
                    "to findRTNavigationPath(); exiting back to the main screen"
        }
        currentParsedScreen = cycleToRTScreen(
            rtNavigationContext,
            RTNavigationButton.BACK,
            ParsedScreen.MainScreen::class
        )

        path = try {
            findShortestRtPath(currentParsedScreen::class, targetScreenType, isComboStopped)
        } catch (e: IllegalArgumentException) {
            listOf()
        }

        if (path == null) {
            logger(LogLevel.ERROR) { "Could not find RT navigation path even after navigating back to the main menu" }
            throw CouldNotFindRTScreenException(targetScreenType)
        }
    }

    rtNavigationContext.resetDuplicate()

    var cycleCount = 0
    val pathIt = path.iterator()
    var nextPathItem = pathIt.next()
    var previousScreenType: KClassifier? = null
    while (true) {
        if (cycleCount >= rtNavigationContext.maxNumCycleAttempts)
            throw CouldNotFindRTScreenException(targetScreenType)

        val parsedDisplayFrame = rtNavigationContext.getParsedDisplayFrame(filterDuplicates = true) ?: continue
        val parsedScreen = parsedDisplayFrame.parsedScreen

        if ((parsedScreen::class != ParsedScreen.UnrecognizedScreen::class) &&
            (previousScreenType != null) &&
            (previousScreenType == parsedScreen::class)) {
            logger(LogLevel.DEBUG) { "Got a screen of the same type ${parsedScreen::class}; skipping" }
            continue
        }
        previousScreenType = parsedScreen::class

        val nextTargetScreenTypeInPath = nextPathItem.targetNodeValue

        logger(LogLevel.DEBUG) { "We are currently at screen $parsedScreen; next target screen type: $nextTargetScreenTypeInPath" }

        if (parsedScreen::class == nextTargetScreenTypeInPath) {
            cycleCount = 0
            if (pathIt.hasNext()) {
                nextPathItem = pathIt.next()
                logger(LogLevel.DEBUG) {
                    "Reached screen type $nextTargetScreenTypeInPath in path; " +
                            "continuing to ${nextPathItem.targetNodeValue}"
                }
            } else {
                logger(LogLevel.DEBUG) { "Target screen type $targetScreenType reached" }
                return parsedScreen
            }
        }

        val navButtonToPress = nextPathItem.edgeValue.button
        logger(LogLevel.DEBUG) { "Pressing button $navButtonToPress to navigate further" }
        rtNavigationContext.shortPressButton(navButtonToPress)

        cycleCount++
    }
}

internal fun findShortestRtPath(from: KClassifier, to: KClassifier, isComboStopped: Boolean) =
    rtNavigationGraph.findShortestPath(from, to) {
        when (it.edgeValidityCondition) {
            RTEdgeValue.EdgeValidityCondition.ALWAYS -> true
            RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_RUNNING -> !isComboStopped
            RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_STOPPED -> isComboStopped
        }
    }

internal fun computeShortRTButtonPress(
    currentQuantity: Int,
    targetQuantity: Int,
    cyclicQuantityRange: Int?,
    incrementSteps: Array<Pair<Int, Int>>,
    incrementButton: RTNavigationButton,
    decrementButton: RTNavigationButton
): Pair<Int, RTNavigationButton> {
    val numNeededShortRTButtonPresses: Int
    val shortRTButtonToPress: RTNavigationButton

    fun computeNumSteps(stepSize: Int, distance: Int) = (distance + (stepSize - 1)) / stepSize

    if (currentQuantity == targetQuantity) {
        numNeededShortRTButtonPresses = 0
        shortRTButtonToPress = RTNavigationButton.CHECK
    } else if (incrementSteps.size == 1) {
        val stepSize = incrementSteps[0].second
        require(stepSize > 0)
        val distance = (targetQuantity - currentQuantity).absoluteValue
        if (cyclicQuantityRange != null) {
            if (distance > (cyclicQuantityRange / 2)) {
                numNeededShortRTButtonPresses = computeNumSteps(stepSize, cyclicQuantityRange - distance)
                shortRTButtonToPress = if (targetQuantity < currentQuantity) incrementButton else decrementButton
            } else {
                numNeededShortRTButtonPresses = computeNumSteps(stepSize, distance)
                shortRTButtonToPress = if (targetQuantity > currentQuantity) incrementButton else decrementButton
            }
        } else {
            numNeededShortRTButtonPresses = computeNumSteps(stepSize, distance)
            shortRTButtonToPress = if (targetQuantity > currentQuantity) incrementButton else decrementButton
        }
    } else {
        val (start, end, button) = if (currentQuantity < targetQuantity)
            Triple(currentQuantity, targetQuantity, incrementButton)
        else
            Triple(targetQuantity, currentQuantity, decrementButton)

        shortRTButtonToPress = button

        var currentValue = start
        var numPresses = 0

        for (index in incrementSteps.indices) {
            val incrementStep = incrementSteps[index]
            val stepSize = incrementStep.second
            require(stepSize > 0)
            val curRangeStart = incrementStep.first
            val curRangeEnd = if (index == incrementSteps.size - 1)
                end
            else
                min(incrementSteps[index + 1].first, end)

            if (currentValue >= curRangeEnd)
                continue

            if (currentValue < curRangeStart)
                currentValue = curRangeStart

            numPresses += computeNumSteps(stepSize, curRangeEnd - currentValue)

            currentValue = curRangeEnd

            if (currentValue >= end)
                break
        }

        numNeededShortRTButtonPresses = numPresses
    }

    return Pair(numNeededShortRTButtonPresses, shortRTButtonToPress)
}
