import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepcognitive.ai.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.deepcognitive.ai.ui.theme.AppTheme
import com.deepcognitive.ai.ui.theme.TranslucentGrey

data class Event(
    val time: String,
    val date: String,
    val question: String,
    val agents: List<AgentType>,
    val resources: String
)

enum class AgentType {
    AUDIO, WEB, IMAGE, DOCUMENT
}

enum class SortKey {
    TIME, DATE, QUESTION, AGENTS, RESOURCES
}

data class SortConfig(
    val key: SortKey,
    val direction: Boolean // true for ascending
)

@Composable
fun DashboardScreen() {
    var sortConfig by remember { mutableStateOf(SortConfig(SortKey.DATE, true)) }
    val events = remember { generateSampleEvents() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.backgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        DashboardHeader()
        Spacer(modifier = Modifier.height(16.dp))
        ResourceBoxes()
        EventList(
            events = sortEvents(events, sortConfig),
            sortConfig = sortConfig,
            onSortChanged = { newKey ->
                sortConfig = if (sortConfig.key == newKey) {
                    sortConfig.copy(direction = !sortConfig.direction)
                } else {
                    SortConfig(newKey, true)
                }
            },
            modifier = Modifier.offset(y = (-200).dp)
        )
    }
}

@Composable
fun DashboardHeader() {
    Text(
        text = "Dashboard",
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
fun ResourceBoxes() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ResourceBox("CPU", "25%", R.drawable.ic_cpu)
        ResourceBox("GPU", "20GB", R.drawable.ic_gpu)
        ResourceBox("RAM", "60GB", R.drawable.ic_ram)
        ResourceBox("Network", "120GB", R.drawable.ic_network)
    }
}

@Composable
fun ResourceBox(
    label: String,
    value: String,
    iconRes: Int
) {
    Surface(
        modifier = Modifier
            .padding(4.dp)
            .width(120.dp)
            .heightIn(min = 100.dp)
            .clickable { },
        color = TranslucentGrey,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(iconRes),
                contentDescription = label,
                tint = Color.Gray,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = label,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun EventList(
    events: List<Event>,
    sortConfig: SortConfig,
    onSortChanged: (SortKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(500.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = TranslucentGrey
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Event List - Total Events: ${events.size}",
                style = MaterialTheme.typography.titleLarge,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SortableHeader("Time", SortKey.TIME, sortConfig, onSortChanged)
                SortableHeader("Date", SortKey.DATE, sortConfig, onSortChanged)
                SortableHeader("Question", SortKey.QUESTION, sortConfig, onSortChanged)
                SortableHeader("Agents", SortKey.AGENTS, sortConfig, onSortChanged)
                SortableHeader("Resources", SortKey.RESOURCES, sortConfig, onSortChanged)
            }
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(events) { event ->
                    EventRow(event)
                    Divider()
                }
            }
        }
    }
}

@Composable
fun SortableHeader(
    text: String,
    key: SortKey,
    sortConfig: SortConfig,
    onSortChanged: (SortKey) -> Unit
) {
    Row(
        modifier = Modifier
            .clickable { onSortChanged(key) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        if (sortConfig.key == key) {
            Icon(
                imageVector = if (sortConfig.direction) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Sort direction",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun EventRow(event: Event) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = event.time,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black
        )
        Text(
            text = event.date,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black
        )
        Text(
            text = event.question,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            color = Color.Black
        )
        Row {
            event.agents.forEach { agent ->
                AgentIcon(agent)
            }
        }
        Text(
            text = event.resources,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black
        )
    }
}

@Composable
fun AgentIcon(type: AgentType) {
    val iconRes = when (type) {
        AgentType.AUDIO -> R.drawable.ic_audio
        AgentType.WEB -> R.drawable.ic_web
        AgentType.IMAGE -> R.drawable.ic_image
        AgentType.DOCUMENT -> R.drawable.ic_document
    }

    val tint = when (type) {
        AgentType.AUDIO -> Color(0xFFFF5722)
        AgentType.WEB -> Color(0xFF2196F3)
        AgentType.IMAGE -> Color(0xFF4CAF50)
        AgentType.DOCUMENT -> Color(0xFF9C27B0)
    }

    Icon(
        imageVector = ImageVector.vectorResource(iconRes),
        contentDescription = type.name,
        tint = tint,
        modifier = Modifier
            .size(20.dp)
            .padding(horizontal = 4.dp)
    )
}

private fun generateSampleEvents(): List<Event> {
    // Sample data matching the React implementation
    return listOf(
        Event("10:00 AM", "2022-01-01", "What is React?", listOf(AgentType.AUDIO, AgentType.WEB), "CPU: 20%, RAM: 30%"),
        Event("11:00 AM", "2022-01-01", "How to use hooks?", listOf(AgentType.IMAGE), "CPU: 25%, RAM: 35%"),
        // Add more sample events...
    )
}

private fun sortEvents(events: List<Event>, config: SortConfig): List<Event> {
    return events.sortedWith { a, b ->
        val comparison = when (config.key) {
            SortKey.TIME -> a.time.compareTo(b.time)
            SortKey.DATE -> a.date.compareTo(b.date)
            SortKey.QUESTION -> a.question.compareTo(b.question)
            SortKey.AGENTS -> a.agents.size.compareTo(b.agents.size)
            SortKey.RESOURCES -> {
                val aRam = a.resources.split("RAM: ")[1].replace("%", "").toInt()
                val bRam = b.resources.split("RAM: ")[1].replace("%", "").toInt()
                aRam.compareTo(bRam)
            }
        }
        if (config.direction) comparison else -comparison
    }
}