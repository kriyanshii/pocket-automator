package com.pocketautomator.core

import android.content.Context
import com.pocketautomator.model.ExportRecording
import com.pocketautomator.model.JsonConfig

data class LearnedSkill(
    val id: String,
    val displayName: String,
    val examplePrompt: String
)

object SkillTrajectoryMapper {
    private val skillToAsset = mapOf(
        "bluetooth_enable" to "trajectories/bluetooth_enable.json",
        "calendar_create_event" to "trajectories/calendar_create_event.json",
        "camera_take_photo" to "trajectories/camera_take_photo.json",
        "contacts_search" to "trajectories/contacts_search.json",
        "create_alarm" to "trajectories/create_alarm.json",
        "gmail_send_email" to "trajectories/gmail_send_email.json",
        "linkedin_search_person" to "trajectories/linkedin_search_person.json",
        "slack_open_channel" to "trajectories/slack_open_channel.json",
        "spotify_pause" to "trajectories/spotify_pause.json",
        "spotify_play_playlist" to "trajectories/spotify_play_playlist.json",
        "spotify_search_play" to "trajectories/spotify_search_play.json",
        "uber_request_ride" to "trajectories/uber_request_ride.json",
        "whatsapp_send_message" to "trajectories/whatsapp_send_message.json",
        "wifi_enable" to "trajectories/wifi_enable.json",
        "youtube_search" to "trajectories/youtube_search.json"
    )

    private val skillDetails = mapOf(
        "bluetooth_enable" to LearnedSkill(
            id = "bluetooth_enable",
            displayName = "Turn on Bluetooth",
            examplePrompt = "Turn bluetooth on"
        ),
        "calendar_create_event" to LearnedSkill(
            id = "calendar_create_event",
            displayName = "Create calendar event",
            examplePrompt = "Create a calendar event for tomorrow at 4 PM"
        ),
        "camera_take_photo" to LearnedSkill(
            id = "camera_take_photo",
            displayName = "Take a photo",
            examplePrompt = "Open camera and take a picture"
        ),
        "contacts_search" to LearnedSkill(
            id = "contacts_search",
            displayName = "Search contacts",
            examplePrompt = "Find parag shah in contacts"
        ),
        "create_alarm" to LearnedSkill(
            id = "create_alarm",
            displayName = "Create Alarm",
            examplePrompt = "Set an alarm for 7 AM tomorrow"
        ),
        "gmail_send_email" to LearnedSkill(
            id = "gmail_send_email",
            displayName = "Send Gmail",
            examplePrompt = "Email my team saying project update"
        ),
        "linkedin_search_person" to LearnedSkill(
            id = "linkedin_search_person",
            displayName = "Search LinkedIn",
            examplePrompt = "Search arya sheth on linkedin"
        ),
        "slack_open_channel" to LearnedSkill(
            id = "slack_open_channel",
            displayName = "Open Slack channel",
            examplePrompt = "Open data contributors channel on slack"
        ),
        "spotify_pause" to LearnedSkill(
            id = "spotify_pause",
            displayName = "Pause Spotify",
            examplePrompt = "Pause spotify"
        ),
        "spotify_play_playlist" to LearnedSkill(
            id = "spotify_play_playlist",
            displayName = "Play Playlist",
            examplePrompt = "Play my workout playlist on Spotify"
        ),
        "spotify_search_play" to LearnedSkill(
            id = "spotify_search_play",
            displayName = "Search & play on Spotify",
            examplePrompt = "Search edm music on spotify and play it"
        ),
        "uber_request_ride" to LearnedSkill(
            id = "uber_request_ride",
            displayName = "Request Uber ride",
            examplePrompt = "Book an uber to the airport"
        ),
        "whatsapp_send_message" to LearnedSkill(
            id = "whatsapp_send_message",
            displayName = "Send Message",
            examplePrompt = "Send a WhatsApp message to Mom"
        ),
        "wifi_enable" to LearnedSkill(
            id = "wifi_enable",
            displayName = "Enable Wi-Fi",
            examplePrompt = "Turn wifi on"
        ),
        "youtube_search" to LearnedSkill(
            id = "youtube_search",
            displayName = "Search YouTube",
            examplePrompt = "Search pasta recipes on youtube"
        )
    )

    /** Skills shown as quick-tap cards on the home screen. */
    private val featuredSkillIds = listOf(
        "create_alarm",
        "spotify_play_playlist",
        "whatsapp_send_message",
        "slack_open_channel",
        "uber_request_ride",
        "youtube_search"
    )

    fun assetPathFor(skill: String): String? = skillToAsset[skill]

    fun learnedSkills(): List<LearnedSkill> =
        featuredSkillIds.mapNotNull { skillDetails[it] }
}

class TrajectoryLoader(private val context: Context) {

    fun loadRecording(skill: String): ExportRecording? {
        val assetPath = SkillTrajectoryMapper.assetPathFor(skill) ?: return null
        return runCatching {
            val json = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            JsonConfig.json.decodeFromString(ExportRecording.serializer(), json)
        }.getOrNull()
    }
}
