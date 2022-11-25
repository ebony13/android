package mega.privacy.android.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mega.privacy.android.data.gateway.CacheFolderGateway
import mega.privacy.android.data.gateway.api.MegaApiGateway
import mega.privacy.android.data.gateway.api.MegaChatApiGateway
import mega.privacy.android.data.listener.OptionalMegaRequestListenerInterface
import mega.privacy.android.data.mapper.ContactDataMapper
import mega.privacy.android.data.mapper.ContactItemMapper
import mega.privacy.android.data.mapper.ContactRequestMapper
import mega.privacy.android.data.mapper.MegaChatPeerListMapper
import mega.privacy.android.data.mapper.OnlineStatusMapper
import mega.privacy.android.data.mapper.UserLastGreenMapper
import mega.privacy.android.data.mapper.UserUpdateMapper
import mega.privacy.android.domain.exception.ContactDoesNotExistException
import mega.privacy.android.domain.exception.MegaException
import mega.privacy.android.domain.repository.ContactsRepository
import nz.mega.sdk.MegaApiJava
import nz.mega.sdk.MegaError
import nz.mega.sdk.MegaRequest
import nz.mega.sdk.MegaUser
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultContactsRepositoryTest {

    private lateinit var underTest: ContactsRepository

    private val megaApiGateway = mock<MegaApiGateway>()
    private val megaChatApiGateway = mock<MegaChatApiGateway>()
    private val cacheFolderGateway = mock<CacheFolderGateway>()
    private val contactRequestMapper = mock<ContactRequestMapper>()
    private val userLastGreenMapper = mock<UserLastGreenMapper>()
    private val userUpdateMapper = mock<UserUpdateMapper>()
    private val megaChatPeerListMapper = mock<MegaChatPeerListMapper>()
    private val onlineStatusMapper = mock<OnlineStatusMapper>()
    private val contactItemMapper = mock<ContactItemMapper>()
    private val contactDataMapper = mock<ContactDataMapper>()

    private val userEmail = "test@mega.nz"
    private val user = mock<MegaUser> { on { email }.thenReturn(userEmail) }
    private val success = mock<MegaError> { on { errorCode }.thenReturn(MegaError.API_OK) }
    private val error = mock<MegaError> { on { errorCode }.thenReturn(MegaError.API_EARGS) }

    @Before
    fun setUp() {
        underTest = DefaultContactsRepository(
            megaApiGateway = megaApiGateway,
            megaChatApiGateway = megaChatApiGateway,
            ioDispatcher = UnconfinedTestDispatcher(),
            cacheFolderGateway = cacheFolderGateway,
            contactRequestMapper = contactRequestMapper,
            userLastGreenMapper = userLastGreenMapper,
            userUpdateMapper = userUpdateMapper,
            megaChatPeerListMapper = megaChatPeerListMapper,
            onlineStatusMapper = onlineStatusMapper,
            contactItemMapper = contactItemMapper,
            contactDataMapper = contactDataMapper,
        )
    }

    @Test
    fun `test that get contact credentials returns valid credentials if user exists and api returns valid credentials`() =
        runTest {
            val validCredentials = "KJ9hFK67vhj3cNCIUHAi8ccwciojiot4hVE5yab3"
            val request = mock<MegaRequest> {
                on { type }.thenReturn(MegaRequest.TYPE_GET_ATTR_USER)
                on { paramType }.thenReturn(MegaApiJava.USER_ATTR_ED25519_PUBLIC_KEY)
                on { password }.thenReturn(validCredentials)
            }

            whenever(megaApiGateway.getContact(userEmail)).thenReturn(user)
            whenever(megaApiGateway.getUserCredentials(any(), any())).thenAnswer {
                ((it.arguments[1]) as OptionalMegaRequestListenerInterface).onRequestFinish(
                    mock(),
                    request,
                    success
                )
            }
            assertThat(underTest.getContactCredentials(userEmail)).isEqualTo(validCredentials)
        }

    @Test(expected = MegaException::class)
    fun `test that get contact credentials throws a MegaException if user exists but api returns error`() =
        runTest {
            whenever(megaApiGateway.getContact(userEmail)).thenReturn(user)
            whenever(megaApiGateway.getUserCredentials(any(), any())).thenAnswer {
                ((it.arguments[1]) as OptionalMegaRequestListenerInterface).onRequestFinish(
                    mock(),
                    mock(),
                    error
                )
            }
            assertThat(underTest.getContactCredentials(userEmail))
        }

    @Test
    fun `test that get contact credentials returns null if api returns user is null`() = runTest {
        whenever(megaApiGateway.getContact(userEmail)).thenReturn(null)
        assertThat(underTest.getContactCredentials(userEmail)).isNull()
    }

    @Test(expected = MegaException::class)
    fun `test that get contact credentials fails with MegaException if api returns an error`() =
        runTest {
            val request = mock<MegaRequest> {
                on { type }.thenReturn(MegaRequest.TYPE_GET_ATTR_USER)
                on { paramType }.thenReturn(MegaApiJava.USER_ATTR_ED25519_PUBLIC_KEY)
            }

            whenever(megaApiGateway.getContact(userEmail)).thenReturn(user)
            whenever(megaApiGateway.getUserCredentials(any(), any())).thenAnswer {
                ((it.arguments[1]) as OptionalMegaRequestListenerInterface).onRequestFinish(
                    mock(),
                    request,
                    error
                )
            }
            assertThat(underTest.getContactCredentials(userEmail))
        }

    @Test
    fun `test that get contact alias returns the alias if api returns the alias`() = runTest {
        val alias = "testAlias"
        val request = mock<MegaRequest> {
            on { type }.thenReturn(MegaRequest.TYPE_GET_ATTR_USER)
            on { paramType }.thenReturn(MegaApiJava.USER_ATTR_ALIAS)
            on { name }.thenReturn(alias)
        }

        whenever(megaApiGateway.getUserAlias(any(), any())).thenAnswer {
            ((it.arguments[1]) as OptionalMegaRequestListenerInterface).onRequestFinish(
                mock(),
                request,
                success
            )
        }
        assertThat(underTest.getUserAlias(-4L)).isEqualTo(alias)
    }

    @Test(expected = MegaException::class)
    fun `test that get user alias throws a MegaException if api fails with error`() = runTest {
        whenever(megaApiGateway.getUserAlias(any(), any())).thenAnswer {
            ((it.arguments[1]) as OptionalMegaRequestListenerInterface).onRequestFinish(
                mock(),
                mock(),
                error
            )
        }
        assertThat(underTest.getUserAlias(-4L))
    }

    @Test
    fun `test that get user first name returns the name if api returns the first name`() = runTest {
        val firstName = "First Name"
        val request = mock<MegaRequest> {
            on { type }.thenReturn(MegaRequest.TYPE_GET_ATTR_USER)
            on { paramType }.thenReturn(MegaApiJava.USER_ATTR_FIRSTNAME)
            on { text }.thenReturn(firstName)
        }

        whenever(megaApiGateway.getUserAttribute(anyString(), any(), any())).thenAnswer {
            ((it.arguments[2]) as OptionalMegaRequestListenerInterface).onRequestFinish(
                mock(),
                request,
                success
            )
        }
        assertThat(underTest.getUserFirstName(userEmail)).isEqualTo(firstName)
    }

    @Test(expected = MegaException::class)
    fun `test that get user first name throws a MegaException if api fails with error`() = runTest {
        whenever(megaApiGateway.getUserAttribute(anyString(), any(), any())).thenAnswer {
            ((it.arguments[2]) as OptionalMegaRequestListenerInterface).onRequestFinish(
                mock(),
                mock(),
                error
            )
        }
        assertThat(underTest.getUserLastName(userEmail))
    }

    @Test
    fun `test that get user last name returns the name if api returns the last name`() = runTest {
        val lastName = "Last Name"
        val request = mock<MegaRequest> {
            on { type }.thenReturn(MegaRequest.TYPE_GET_ATTR_USER)
            on { paramType }.thenReturn(MegaApiJava.USER_ATTR_LASTNAME)
            on { text }.thenReturn(lastName)
        }

        whenever(megaApiGateway.getUserAttribute(anyString(), any(), any())).thenAnswer {
            ((it.arguments[2]) as OptionalMegaRequestListenerInterface).onRequestFinish(
                mock(),
                request,
                success
            )
        }
        assertThat(underTest.getUserLastName(userEmail)).isEqualTo(lastName)
    }

    @Test(expected = MegaException::class)
    fun `test that get user last name throws a MegaException if api fails with error`() = runTest {
        whenever(megaApiGateway.getUserAttribute(anyString(), any(), any())).thenAnswer {
            ((it.arguments[2]) as OptionalMegaRequestListenerInterface).onRequestFinish(
                mock(),
                mock(),
                error
            )
        }
        assertThat(underTest.getUserLastName(userEmail))
    }

    @Test
    fun `test that are credentials verified returns true if user exists and api returns true`() =
        runTest {
            whenever(megaApiGateway.getContact(userEmail)).thenReturn(user)
            whenever(megaApiGateway.areCredentialsVerified(user)).thenReturn(true)
            assertThat(underTest.areCredentialsVerified(userEmail)).isTrue()
        }

    @Test
    fun `test that are credentials verified returns false if user exists and api returns false`() =
        runTest {
            whenever(megaApiGateway.getContact(userEmail)).thenReturn(user)
            whenever(megaApiGateway.areCredentialsVerified(user)).thenReturn(false)
            assertThat(underTest.areCredentialsVerified(userEmail)).isFalse()
        }

    @Test(expected = ContactDoesNotExistException::class)
    fun `test that are credentials verified throws a ContactDoesNotExistException if user does not exist`() =
        runTest {
            whenever(megaApiGateway.getContact(userEmail)).thenReturn(null)
            assertThat(underTest.areCredentialsVerified(userEmail))
        }

    @Test
    fun `test that reset credentials finish correctly if user exists and api completes successfully`() =
        runTest {
            whenever(megaApiGateway.getContact(userEmail)).thenReturn(user)
            whenever(megaApiGateway.resetCredentials(any(), any())).thenAnswer {
                ((it.arguments[1]) as OptionalMegaRequestListenerInterface).onRequestFinish(
                    mock(),
                    mock(),
                    success
                )
            }
            assertThat(underTest.resetCredentials(userEmail))
        }

    @Test(expected = MegaException::class)
    fun `test that reset credentials throws a MegaException if user exists but api fails with error`() =
        runTest {
            whenever(megaApiGateway.getContact(userEmail)).thenReturn(user)
            whenever(megaApiGateway.resetCredentials(any(), any())).thenAnswer {
                ((it.arguments[1]) as OptionalMegaRequestListenerInterface).onRequestFinish(
                    mock(),
                    mock(),
                    error
                )
            }
            assertThat(underTest.resetCredentials(userEmail))
        }

    @Test(expected = ContactDoesNotExistException::class)
    fun `test that reset credentials throws a ContactDoesNotExistException if user does not exist`() =
        runTest {
            whenever(megaApiGateway.getContact(userEmail)).thenReturn(null)
            assertThat(underTest.resetCredentials(userEmail))
        }

    @Test
    fun `test that verify credentials finish correctly if user exists and api completes successfully`() =
        runTest {
            whenever(megaApiGateway.getContact(userEmail)).thenReturn(user)
            whenever(megaApiGateway.verifyCredentials(any(), any())).thenAnswer {
                ((it.arguments[1]) as OptionalMegaRequestListenerInterface).onRequestFinish(
                    mock(),
                    mock(),
                    success
                )
            }
            assertThat(underTest.verifyCredentials(userEmail))
        }

    @Test(expected = MegaException::class)
    fun `test that verify credentials throws a MegaException if user exists but api fails with error`() =
        runTest {
            whenever(megaApiGateway.getContact(userEmail)).thenReturn(user)
            whenever(megaApiGateway.verifyCredentials(any(), any())).thenAnswer {
                ((it.arguments[1]) as OptionalMegaRequestListenerInterface).onRequestFinish(
                    mock(),
                    mock(),
                    error
                )
            }
            assertThat(underTest.verifyCredentials(userEmail))
        }

    @Test(expected = ContactDoesNotExistException::class)
    fun `test that verify credentials throws a ContactDoesNotExistException if user does not exist`() =
        runTest {
            whenever(megaApiGateway.getContact(userEmail)).thenReturn(null)
            assertThat(underTest.verifyCredentials(userEmail))
        }
}