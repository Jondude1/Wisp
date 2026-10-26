package com.jondude1.wisp.agent

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.jondude1.wisp.MainActivity
import com.jondude1.wisp.Settings
import com.jondude1.wisp.services.AgentRunService
import com.jondude1.wisp.services.WispAccessibilityService
import com.jondude1.wisp.voice.VoiceIO
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

/**
 * Single source of truth for everything on screen. Runs on the main dispatcher so Compose state is
 * only ever touched from one thread; network and blocking work hop to IO inside their own calls.
 */
class AgentController(val app: Application, val settings: Settings) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine by lazy { AgentEngine(this) }
    val voice = VoiceIO(app, this)

    val history = mutableListOf<JSONObject>()
    val tasks = mutableStateListOf<AgentTask>()
    val feed = mutableStateListOf<FeedItem>()

    var orb by mutableStateOf(OrbState.Idle)
    var level by mutableFloatStateOf(0f)        // mic level 0..1, drives the orb while listening
    var partial by mutableStateOf("")           // live transcript
    var statusLine by mutableStateOf("")        // what the agent is doing right now
    var approval by mutableStateOf<Approval?>(null)
    var resumeTick by mutableIntStateOf(0)      // bumped on resume so Settings re-checks permissions
    var appVisible = true
        private set

    private var lastInputWasVoice = false
    private val pendingChoices = LinkedHashMap<String, CompletableDeferred<String>>()
    private val approvalLock = Mutex()

    // ---- input -------------------------------------------------------------------------------

    fun submit(text: String, viaVoice: Boolean = false) {
        val t = text.trim()
        if (t.isEmpty()) return
        partial = ""

        // A pending ask_user question takes the reply instead of starting a new task.
        val pending = pendingChoices.entries.firstOrNull()
        if (pending != null) {
            pendingChoices.remove(pending.key)
            addFeed(UserSaid(t))
            pending.value.complete(t)
            return
        }

        addFeed(UserSaid(t))
        if (settings.apiKey.isBlank()) {
            addFeed(AgentSaid("Add your Anthropic API key in Settings and I'm ready."))
            return
        }
        voice.stopSpeaking()
        lastInputWasVoice = viaVoice
        val task = AgentTask(t.take(60))
        tasks.add(0, task)
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, AgentRunService::class.java))
        }
        task.job = scope.launch { engine.run(task, t) }
    }

    fun toggleListening() {
        if (orb == OrbState.Listening) voice.stopListening() else voice.startListening()
    }

    fun stopTask(task: AgentTask) {
        task.job?.cancel()
    }

    fun stopAll() {
        tasks.filter { it.status == TaskStatus.Running || it.status == TaskStatus.WaitingForYou }
            .forEach { it.job?.cancel() }
    }

    fun clearFinished() {
        tasks.removeAll { it.status != TaskStatus.Running && it.status != TaskStatus.WaitingForYou }
    }

    // ---- progress from the engine ------------------------------------------------------------

    fun setThinking(task: AgentTask) {
        orb = OrbState.Thinking
        statusLine = "Thinking"
        syncCapsule(statusLine)
    }

    fun noteStep(task: AgentTask, label: String) {
        task.steps.add(TaskStep(label))
        orb = OrbState.Acting
        statusLine = label
        syncCapsule(label)
    }

    fun afterTool(task: AgentTask) {
        orb = OrbState.Thinking
    }

    fun finish(task: AgentTask, status: TaskStatus, text: String) {
        task.status = status
        task.result = text
        if (status != TaskStatus.Stopped) addFeed(AgentSaid(text))

        val stillRunning = tasks.any { it.status == TaskStatus.Running || it.status == TaskStatus.WaitingForYou }
        if (!stillRunning) {
            statusLine = ""
            WispAccessibilityService.instance?.capsule?.hide()
            runCatching { app.stopService(Intent(app, AgentRunService::class.java)) }
            if (orb != OrbState.Speaking) orb = OrbState.Idle
        }
        if (status == TaskStatus.Done && lastInputWasVoice && settings.speakReplies) {
            voice.speak(text)
        }
        if (task.usedScreen && !appVisible && !stillRunning) bringToFront(goHomeFirst = true)
    }

    // ---- generative UI -----------------------------------------------------------------------

    fun pushCard(card: AgentCard) = addFeed(CardShown(card))

    suspend fun ask(task: AgentTask, question: String, options: List<String>): String {
        val actions = options.take(4).mapIndexed { i, o ->
            CardAction("opt$i", o.take(40), if (i == 0) "primary" else "quiet")
        }
        val card = AgentCard(UUID.randomUUID().toString(), "choice", question.take(140), "", emptyList(), actions)
        val answer = CompletableDeferred<String>()
        pendingChoices[card.id] = answer
        addFeed(CardShown(card))
        task.status = TaskStatus.WaitingForYou
        statusLine = "Waiting for you"
        syncCapsule("Waiting for you")
        if (!appVisible) bringToFront()
        return try {
            answer.await()
        } finally {
            pendingChoices.remove(card.id)
            task.status = TaskStatus.Running
        }
    }

    fun onCardAction(card: AgentCard, action: CardAction) {
        card.chosen = action.label
        val pending = pendingChoices.remove(card.id)
        if (pending != null) pending.complete(action.label) else submit(action.label)
    }

    // ---- approvals ---------------------------------------------------------------------------

    suspend fun requestApproval(task: AgentTask, title: String, detail: String, irreversible: Boolean): Boolean =
        approvalLock.withLock {
            val a = Approval(title.take(100), detail.take(400), irreversible)
            approval = a
            task.status = TaskStatus.WaitingForYou
            statusLine = "Waiting for your OK"
            val capsule = WispAccessibilityService.instance?.capsule
            if (!appVisible) capsule?.showApproval(a.title, a.detail) { ok -> resolveApproval(a, ok) }
            try {
                a.result.await()
            } finally {
                approval = null
                task.status = TaskStatus.Running
                capsule?.hideApproval()
            }
        }

    fun resolveApproval(a: Approval, ok: Boolean) {
        a.result.complete(ok)
    }

    // ---- app visibility and the capsule ------------------------------------------------------

    fun onVisibility(visible: Boolean) {
        appVisible = visible
        resumeTick++
        if (visible) WispAccessibilityService.instance?.capsule?.hide() else syncCapsule(statusLine.ifBlank { "Working" })
    }

    private fun syncCapsule(text: String) {
        val svc = WispAccessibilityService.instance ?: return
        val active = tasks.any { it.status == TaskStatus.Running || it.status == TaskStatus.WaitingForYou }
        if (!appVisible && active) {
            svc.capsule.show(text, onStop = { stopAll() }, onOpen = { bringToFront() })
        } else {
            svc.capsule.hide()
        }
    }

    fun bringToFront(goHomeFirst: Boolean = false) {
        val intent = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val svc = WispAccessibilityService.instance
        val ok = runCatching { (svc ?: app).startActivity(intent) }.isSuccess
        if (!ok && goHomeFirst) svc?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
    }

    // ---- helpers -----------------------------------------------------------------------------

    private fun addFeed(item: FeedItem) {
        feed.add(item)
        while (feed.size > MAX_FEED) feed.removeAt(0)
    }

    companion object {
        private const val MAX_FEED = 80
    }
}
