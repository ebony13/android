package mega.privacy.android.app.main

import mega.privacy.android.app.R

internal enum class FragmentTag {
    CLOUD_DRIVE,
    HOMEPAGE,
    PHOTOS,
    INBOX,
    INCOMING_SHARES,
    OUTGOING_SHARES,
    SEARCH,
    TRANSFERS,
    COMPLETED_TRANSFERS,
    RECENT_CHAT,
    RUBBISH_BIN,
    NOTIFICATIONS,
    TURN_ON_NOTIFICATIONS,
    PERMISSIONS,
    SMS_VERIFICATION,
    LINKS,
    MEDIA_DISCOVERY,
    ALBUM_CONTENT,
    PHOTOS_FILTER;

    val tag: String
        get() = when (this) {
            CLOUD_DRIVE -> "fileBrowserFragment"
            HOMEPAGE -> "homepageFragment"
            RUBBISH_BIN -> "rubbishBinFragment"
            PHOTOS -> "photosFragment"
            INBOX -> "inboxFragment"
            INCOMING_SHARES -> "incomingSharesFragment"
            OUTGOING_SHARES -> "outgoingSharesFragment"
            SEARCH -> "searchFragment"
            TRANSFERS -> "android:switcher:${R.id.transfers_tabs_pager}:0"
            COMPLETED_TRANSFERS -> "android:switcher:${R.id.transfers_tabs_pager}:1"
            RECENT_CHAT -> "chatTabsFragment"
            NOTIFICATIONS -> "notificationsFragment"
            TURN_ON_NOTIFICATIONS -> "turnOnNotificationsFragment"
            PERMISSIONS -> "permissionsFragment"
            SMS_VERIFICATION -> "smsVerificationFragment"
            LINKS -> "linksFragment"
            MEDIA_DISCOVERY -> "mediaDiscoveryFragment"
            ALBUM_CONTENT -> "fragmentAlbumContent"
            PHOTOS_FILTER -> "fragmentPhotosFilter"
        }
}