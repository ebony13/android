package mega.privacy.android.app.usecase

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.blockingSubscribeBy
import mega.privacy.android.data.qualifier.MegaApi
import mega.privacy.android.app.listeners.OptionalMegaRequestListenerInterface
import mega.privacy.android.app.namecollision.data.NameCollision
import mega.privacy.android.app.namecollision.data.NameCollisionResult
import mega.privacy.android.app.usecase.chat.GetChatMessageUseCase
import mega.privacy.android.app.usecase.data.CopyRequestResult
import mega.privacy.android.app.usecase.exception.*
import mega.privacy.android.app.utils.RxUtil.blockingGetOrNull
import nz.mega.sdk.*
import nz.mega.sdk.MegaError.*
import nz.mega.sdk.MegaApiAndroid
import nz.mega.sdk.MegaNode
import javax.inject.Inject

/**
 * Use case for copying MegaNodes.
 *
 * @property megaApi                MegaApiAndroid instance to copy nodes.
 * @property megaChatApi            MegaChatApiAndroid instance to get nodes from chats.
 * @property getNodeUseCase         Required for getting [MegaNode]s.
 * @property moveNodeUseCase        Required for moving MegaNodes to the Rubbish Bin.
 * @property getChatMessageUseCase  Required for getting chat [MegaNode]s.
 */
class CopyNodeUseCase @Inject constructor(
    @MegaApi private val megaApi: MegaApiAndroid,
    private val megaChatApi: MegaChatApiAndroid,
    private val getNodeUseCase: GetNodeUseCase,
    private val moveNodeUseCase: MoveNodeUseCase,
    private val getChatMessageUseCase: GetChatMessageUseCase
) {

    /**
     * Copies a node.
     *
     * @param handle        The identifier of the MegaNode to copy.
     * @param parentHandle  The parent MegaNode where the node has to be copied.
     * @return Completable.
     */
    fun copy(handle: Long, parentHandle: Long): Completable =
        Completable.fromCallable {
            copy(
                getNodeUseCase.get(handle).blockingGetOrNull(),
                getNodeUseCase.get(parentHandle).blockingGetOrNull()
            ).blockingAwait()
        }

    /**
     * Copies a node.
     *
     * @param node          The MegaNode to copy.
     * @param parentHandle  The parent MegaNode where the node has to be copied.
     * @return Completable.
     */
    fun copy(node: MegaNode?, parentHandle: Long): Completable =
        Completable.fromCallable {
            copy(node, getNodeUseCase.get(parentHandle).blockingGetOrNull()).blockingAwait()
        }

    /**
     * Copies a node.
     *
     * @param node          The MegaNoe to copy.
     * @param parentNode    The parent MegaNode where the node has to be copied.
     * @param newName       New name for the copied node. Null if it wants to keep the original one.
     * @return Completable.
     */
    fun copy(node: MegaNode?, parentNode: MegaNode?, newName: String? = null): Completable =
        Completable.create { emitter ->
            if (node == null) {
                emitter.onError(MegaNodeException.NodeDoesNotExistsException())
                return@create
            }

            if (parentNode == null) {
                emitter.onError(MegaNodeException.ParentDoesNotExistException())
                return@create
            }

            val listener = OptionalMegaRequestListenerInterface(onRequestFinish = { _, error ->
                when {
                    emitter.isDisposed -> return@OptionalMegaRequestListenerInterface
                    error.errorCode == API_OK -> emitter.onComplete()
                    error.errorCode == API_EOVERQUOTA && megaApi.isForeignNode(parentNode.handle) ->
                        emitter.onError(ForeignNodeException())
                    else -> emitter.onError(error.toMegaException())
                }
            })

            if (newName != null) {
                megaApi.copyNode(node, parentNode, newName, listener)
            } else {
                megaApi.copyNode(node, parentNode, listener)
            }

        }

    /**
     * Copies a node after resolving a name collision.
     *
     * @param collisionResult   The result of the name collision.
     * @param rename            True if should rename the node, false otherwise.
     * @return Single with the copy result.
     */
    fun copy(
        collisionResult: NameCollisionResult,
        rename: Boolean
    ): Single<CopyRequestResult> =
        Single.create { emitter ->
            val node = if (collisionResult.nameCollision is NameCollision.Import) {
                val collision = collisionResult.nameCollision
                val nodes =
                    getChatMessageUseCase.getChatNodes(collision.chatId, collision.messageId)
                        .blockingGetOrNull()

                if (nodes == null) {
                    null
                } else {
                    var nodeCollision: MegaNode? = null

                    for (node in nodes) {
                        if (node.handle == collision.nodeHandle) {
                            nodeCollision = node
                            break
                        }
                    }

                    nodeCollision
                }
            } else {
                getNodeUseCase
                    .get((collisionResult.nameCollision as NameCollision.Copy).nodeHandle)
                    .blockingGetOrNull()
            }

            if (node == null) {
                emitter.onError(MegaNodeException.NodeDoesNotExistsException())
                return@create
            }

            val parentNode = getNodeUseCase
                .get(collisionResult.nameCollision.parentHandle).blockingGetOrNull()

            if (parentNode == null) {
                emitter.onError(MegaNodeException.ParentDoesNotExistException())
                return@create
            }

            if (!rename && node.isFile) {
                moveNodeUseCase.moveToRubbishBin(collisionResult.nameCollision.collisionHandle)
                    .blockingSubscribeBy(onError = { error -> emitter.onError(error) })
            }

            if (emitter.isDisposed) return@create

            copy(node, parentNode, if (rename) collisionResult.renameName else null)
                .blockingSubscribeBy(
                    onError = { error ->
                        when {
                            emitter.isDisposed -> return@blockingSubscribeBy
                            error.shouldEmmitError() -> emitter.onError(error)
                            else -> emitter.onSuccess(
                                CopyRequestResult(
                                    count = 1,
                                    errorCount = 1
                                )
                            )
                        }
                    },
                    onComplete = {
                        when {
                            emitter.isDisposed -> return@blockingSubscribeBy
                            else -> emitter.onSuccess(
                                CopyRequestResult(
                                    count = 1,
                                    errorCount = 0
                                ).apply { resetAccountDetailsIfNeeded() }
                            )
                        }
                    }
                )
        }

    /**
     * Copies a list of nodes after resolving name collisions.
     *
     * @param collisions    The list with the result of the name collisions.
     * @param rename        True if should rename the nodes, false otherwise.
     * @return Single with the copies result.
     */
    fun copy(
        collisions: List<NameCollisionResult>,
        rename: Boolean
    ): Single<CopyRequestResult> =
        Single.create { emitter ->
            var errorCount = 0

            for (collision in collisions) {
                if (emitter.isDisposed) break

                copy(collision, rename).blockingSubscribeBy(onError = { error ->
                    when {
                        error.shouldEmmitError() -> emitter.onError(error)
                        else -> errorCount++
                    }
                })
            }

            when {
                emitter.isDisposed -> return@create
                else -> emitter.onSuccess(
                    CopyRequestResult(
                        count = collisions.size,
                        errorCount = errorCount
                    ).apply { resetAccountDetailsIfNeeded() }
                )
            }
        }

    /**
     * Copies nodes.
     *
     * @param handles           List of MegaNode handles to copy.
     * @param newParentHandle   Parent MegaNode handle in which the nodes have to be copied.
     * @return Single with the [CopyRequestResult].
     */
    fun copy(handles: LongArray, newParentHandle: Long): Single<CopyRequestResult> =
        Single.create { emitter ->
            val parentNode = getNodeUseCase.get(newParentHandle).blockingGetOrNull()

            if (parentNode == null) {
                emitter.onError(MegaNodeException.ParentDoesNotExistException())
                return@create
            }

            var errorCount = 0

            for (handle in handles) {
                if (emitter.isDisposed) break

                val node = getNodeUseCase.get(handle).blockingGetOrNull()

                if (node == null) {
                    errorCount++
                } else {
                    copy(node, parentNode).blockingSubscribeBy(onError = { error ->
                        when {
                            error.shouldEmmitError() -> emitter.onError(error)
                            else -> errorCount++
                        }
                    })
                }
            }

            when {
                emitter.isDisposed -> return@create
                else -> emitter.onSuccess(
                    CopyRequestResult(
                        handles.size,
                        errorCount
                    ).apply { resetAccountDetailsIfNeeded() }
                )
            }
        }

    /**
     * Copies nodes.
     *
     * @param nodes         List of MegaNodes to copy.
     * @param parentHandle  Parent MegaNode handle in which the nodes have to be copied.
     * @return Single with the [CopyRequestResult].
     */
    fun copy(nodes: List<MegaNode>, parentHandle: Long): Single<CopyRequestResult> =
        Single.create { emitter ->
            val parentNode = getNodeUseCase.get(parentHandle).blockingGetOrNull()

            if (parentNode == null) {
                emitter.onError(MegaNodeException.ParentDoesNotExistException())
                return@create
            }

            var errorCount = 0

            for (node in nodes) {
                if (emitter.isDisposed) break

                copy(node, parentNode).blockingSubscribeBy(onError = { error ->
                    when {
                        error.shouldEmmitError() -> emitter.onError(error)
                        else -> errorCount++
                    }
                })
            }

            when {
                emitter.isDisposed -> return@create
                else -> emitter.onSuccess(
                    CopyRequestResult(
                        nodes.size,
                        errorCount
                    ).apply { resetAccountDetailsIfNeeded() }
                )
            }
        }

    /**
     * Checks if the error of the request is over quota, pre over quota or foreign node.
     *
     * @return True if the error is one of those specified above, false otherwise.
     */
    private fun Throwable.shouldEmmitError(): Boolean =
        when (this) {
            is QuotaExceededMegaException, is NotEnoughQuotaMegaException, is ForeignNodeException -> true
            else -> false
        }

}
