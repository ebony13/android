package mega.privacy.android.app.meeting

import mega.privacy.android.app.meeting.adapter.Participant

interface BottomFloatingPanelListener {
    /**
     * Listener for button bar
     */
    fun onChangeMicState(micOn: Boolean)
    fun onChangeCamState(camOn: Boolean)
    fun onChangeHoldState(isHold: Boolean)
    fun onChangeSpeakerState()
    fun onEndMeeting()

    /**
     * Listener for share & invite button
     */
    fun onShareLink()
    fun onInviteParticipants()

    /**
     * Listener for participant item
     */
    fun onParticipantOption(participant: Participant)
}
