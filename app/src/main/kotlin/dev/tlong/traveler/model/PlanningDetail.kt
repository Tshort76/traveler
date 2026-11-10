package dev.tlong.traveler.model

import kotlinx.serialization.Serializable

/** Desktop research attached to a bookable key; reservation facts remain on the original entity. */
@Serializable
data class PlanningDetail(
    val id: String,
    val type: String,
    val status: String = "research",
    val title: String? = null,
    val stayId: String? = null,
    val nextAction: String? = null,
    val notes: String? = null,
    val fields: Map<String, String> = emptyMap(),
    val options: List<ResearchOption> = emptyList(),
    val critical: Boolean = true,
    val flightId: String? = null,
    val journey: String? = null,
)

@Serializable
data class ResearchOption(
    val name: String,
    val url: String? = null,
    val estimate: String? = null,
    val notes: String? = null,
)
