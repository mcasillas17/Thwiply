package com.elopenmike.thwiply.ui.today

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject

enum class TaskFilter(val label: String) {
    ALL("All"),
    NOTIFICATIONS("🔔 Notifications"),
    SCREENSHOTS("📸 Screenshots"),
    HIGH_PRIORITY("⚡ High Priority"),
    COMPLETED("✔️ Done")
}

@HiltViewModel
class TodayViewModel @Inject constructor() : ViewModel() {

    private val _tasks = MutableStateFlow<List<TaskItem>>(
        listOf(
            TaskItem(
                id = "1",
                title = "Pick up lactose-free milk & sourdough",
                subtitle = "From Trader Joe's before dinner",
                source = TaskSource.WHATSAPP,
                sourceSender = "Mom",
                dueTime = "Today, 5:30 PM",
                isCompleted = false,
                isHighPriority = true,
                aiSnippet = "\"Hey sweetie! Can you pick up lactose-free milk and some sourdough from TJ's before coming over?\""
            ),
            TaskItem(
                id = "2",
                title = "Review PR #42 & merge to staging",
                subtitle = "LiteRT model inference engine optimizations",
                source = TaskSource.SLACK,
                sourceSender = "#mobile-eng",
                dueTime = "Today, 4:00 PM",
                isCompleted = false,
                isHighPriority = true,
                aiSnippet = "\"@channel reminder to review PR #42 before EOD standup so we can test the build!\""
            ),
            TaskItem(
                id = "3",
                title = "Check in for flight DL-1844",
                subtitle = "Seat 12A • Gate B14",
                source = TaskSource.GMAIL,
                sourceSender = "Delta Air Lines",
                dueTime = "Tomorrow, 8:00 AM",
                isCompleted = false,
                isHighPriority = false,
                aiSnippet = "\"Your upcoming flight to SFO is ready for 24-hour check in. Confirmation: H9X2KL\""
            ),
            TaskItem(
                id = "4",
                title = "Book haircut appointment with Mateo",
                subtitle = "Fade & trim",
                source = TaskSource.SCREENSHOT,
                sourceSender = "Instagram DM",
                dueTime = "Friday, 2:00 PM",
                isCompleted = false,
                isHighPriority = false,
                aiSnippet = "\"Mateo has open slots this Friday at 2pm if you want to book!\""
            ),
            TaskItem(
                id = "5",
                title = "Pay electric bill ($84.20)",
                subtitle = "Auto-pay confirmation due",
                source = TaskSource.MESSAGES,
                sourceSender = "City Power",
                dueTime = "Yesterday",
                isCompleted = true,
                isHighPriority = false,
                aiSnippet = "\"Your monthly electricity statement is ready. Balance: $84.20 due Aug 24.\""
            )
        )
    )
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    private val _selectedFilter = MutableStateFlow(TaskFilter.ALL)
    val selectedFilter: StateFlow<TaskFilter> = _selectedFilter.asStateFlow()

    fun setFilter(filter: TaskFilter) {
        _selectedFilter.value = filter
    }

    fun toggleTask(id: String) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it
        }
    }

    fun deleteTask(id: String) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
    }

    fun addTask(
        title: String,
        subtitle: String?,
        source: TaskSource,
        sender: String,
        dueTime: String?,
        isHighPriority: Boolean,
        snippet: String?
    ) {
        val newTask = TaskItem(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            subtitle = subtitle?.trim()?.ifBlank { null },
            source = source,
            sourceSender = sender.trim().ifBlank { "Manual" },
            dueTime = dueTime?.trim()?.ifBlank { null },
            isCompleted = false,
            isHighPriority = isHighPriority,
            aiSnippet = snippet?.trim()?.ifBlank { null }
        )
        _tasks.value = listOf(newTask) + _tasks.value
    }
}
