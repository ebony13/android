package mega.privacy.android.data.gateway.preferences

import kotlinx.coroutines.flow.Flow

/**
 * UI preferences gateway
 *
 */
interface UIPreferencesGateway {
    /**
     * Monitor preferred start screen
     *
     * @return preferred start screen
     */
    fun monitorPreferredStartScreen(): Flow<Int?>

    /**
     * Set preferred start screen
     *
     * @param value
     */
    suspend fun setPreferredStartScreen(value: Int)

    /**
     * Monitor the List View type
     *
     * @return a [Flow] to observe the List View type
     */
    fun monitorListViewType(): Flow<Int?>

    /**
     * Set the new List View type
     *
     * @param value An [Integer] representing a new List View type
     */
    suspend fun setListViewType(value: Int)

    /**
     * Monitor start screen login timestamp
     *
     * @return start screen login timestamp
     */
    fun monitorStartScreenLoginTimestamp(): Flow<Long?>

    /**
     * Set start screen login timestamp
     *
     * @param value
     */
    suspend fun setStartScreenLoginTimestamp(value: Long)

    /**
     * Monitor do not alert about start screen
     *
     * @return true if alert should be displayed, else false
     */
    fun monitorDoNotAlertAboutStartScreen(): Flow<Boolean?>

    /**
     * Set do not alert about start screen
     *
     * @param value
     */
    suspend fun setDoNotAlertAboutStartScreen(value: Boolean)

    /**
     * Monitor hide recent activity
     *
     * @return true if recent activity should be hidden, else false
     */
    fun monitorHideRecentActivity(): Flow<Boolean?>

    /**
     * Monitor media discovery view
     *
     * @return media discovery state
     */
    fun monitorMediaDiscoveryView(): Flow<Int?>

    /**
     * Set hide recent activity
     *
     * @param value
     */
    suspend fun setHideRecentActivity(value: Boolean)

    /**
     * Set media discovery view
     *
     * @param value
     */
    suspend fun setMediaDiscoveryView(value: Int)
}