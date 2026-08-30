package com.darkaxt.dualdex

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RawLiveMemoryControlActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var scenario: Spinner
    private lateinit var pause: Button
    private lateinit var play: Button
    private lateinit var step: Button
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            renderCurrentState()
            handler.postDelayed(this, REFRESH_INTERVAL_MILLIS)
        }
    }

    private val controller: RawLiveMemoryQaController?
        get() = (application as? RetroArchFreeUiQaApplication)?.rawMemoryQaController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_raw_live_memory_control)

        status = findViewById(R.id.raw_memory_status)
        scenario = findViewById(R.id.raw_memory_scenario)
        pause = findViewById(R.id.raw_memory_pause)
        play = findViewById(R.id.raw_memory_play)
        step = findViewById(R.id.raw_memory_step)

        val scenarioIds = controller?.scenarioIds().orEmpty()
        scenario.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            scenarioIds,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        scenario.setSelection(scenarioIds.indexOf(controller?.snapshot()?.scenarioId).coerceAtLeast(0))
        scenario.setOnItemSelectedListener(SimpleItemSelectedListener { position ->
            val selected = scenarioIds.getOrNull(position) ?: return@SimpleItemSelectedListener
            val active = runCatching { controller?.snapshot()?.scenarioId }.getOrNull()
            if (selected != active) runControllerAction { selectScenario(selected) }
        })

        pause.setOnClickListener { runControllerAction(RawLiveMemoryQaController::pause) }
        play.setOnClickListener { runControllerAction(RawLiveMemoryQaController::play) }
        step.setOnClickListener { runControllerAction(RawLiveMemoryQaController::step) }
        findViewById<Button>(R.id.raw_memory_open_companion).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        renderCurrentState()
    }

    override fun onStart() {
        super.onStart()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onStop() {
        handler.removeCallbacks(refresh)
        super.onStop()
    }

    private fun runControllerAction(action: RawLiveMemoryQaController.() -> RawLiveMemorySimulatorSnapshot) {
        val currentController = controller ?: return renderUnavailable()
        runCatching { currentController.action() }
            .onSuccess(::render)
            .onFailure { renderUnavailable() }
    }

    private fun renderCurrentState() {
        val snapshot = runCatching { controller?.snapshot() }.getOrNull()
        if (snapshot == null) renderUnavailable() else render(snapshot)
    }

    private fun render(snapshot: RawLiveMemorySimulatorSnapshot) {
        status.text = getString(
            R.string.raw_memory_status,
            snapshot.scenarioId,
            snapshot.frameId,
            snapshot.frameIndex + 1,
            snapshot.frameCount,
            getString(if (snapshot.paused) R.string.raw_memory_paused else R.string.raw_memory_playing),
        )
        pause.isEnabled = !snapshot.paused
        play.isEnabled = snapshot.paused
        step.isEnabled = true
        scenario.isEnabled = true
        val position = (scenario.adapter as? ArrayAdapter<*>)
            ?.let { adapter -> (0 until adapter.count).firstOrNull { adapter.getItem(it) == snapshot.scenarioId } }
        if (position != null && scenario.selectedItemPosition != position) scenario.setSelection(position)
    }

    private fun renderUnavailable() {
        status.setText(R.string.raw_memory_unavailable)
        pause.isEnabled = false
        play.isEnabled = false
        step.isEnabled = false
        scenario.isEnabled = false
    }

    private companion object {
        const val REFRESH_INTERVAL_MILLIS = 250L
    }
}

private class SimpleItemSelectedListener(
    private val onSelected: (Int) -> Unit,
) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        onSelected(position)
    }

    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
}
