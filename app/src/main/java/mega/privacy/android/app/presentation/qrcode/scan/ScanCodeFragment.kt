package mega.privacy.android.app.presentation.qrcode.scan

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.budiyev.android.codescanner.CodeScanner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.Result
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import mega.privacy.android.app.R
import mega.privacy.android.app.databinding.DialogAcceptContactBinding
import mega.privacy.android.app.databinding.DialogInviteBinding
import mega.privacy.android.app.databinding.FragmentScanCodeBinding
import mega.privacy.android.app.main.qrcode.QRCodeActivity
import mega.privacy.android.app.presentation.extensions.getFormattedStringOrDefault
import mega.privacy.android.app.presentation.qrcode.scan.model.ScanCodeState
import mega.privacy.android.app.utils.ContactUtil
import mega.privacy.android.data.qualifier.MegaApi
import nz.mega.sdk.MegaApiAndroid
import nz.mega.sdk.MegaContactRequest
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

/**
 * ScanCodeFragment
 */
@AndroidEntryPoint
class ScanCodeFragment : Fragment() {

    private var _binding: FragmentScanCodeBinding? = null
    private val binding get() = _binding!!

    private var _dialogInviteBinding: DialogInviteBinding? = null
    private val dialogInviteBinding get() = _dialogInviteBinding!!

    private var _dialogAcceptContactBinding: DialogAcceptContactBinding? = null
    private val dialogAcceptContactBinding get() = _dialogAcceptContactBinding!!

    private var mStartPreviewRetried = 0
    private var codeScanner: CodeScanner? = null
    private var inviteAlertDialog: AlertDialog? = null
    private var requestedAlertDialog: AlertDialog? = null

    private val viewModel: ScanCodeViewModel by activityViewModels()
    private val uiState: ScanCodeState
        get() = viewModel.state.value

    private var handler: Handler? = null

    @MegaApi
    @Inject
    lateinit var megaApi: MegaApiAndroid

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate")
        super.onCreate(savedInstanceState)
        handler = Handler(Looper.getMainLooper())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Timber.d("onCreateView")
        _binding = FragmentScanCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupView()
        observeUiState()
    }

    override fun onResume() {
        Timber.d("onResume")
        super.onResume()
        codeScanner?.startPreview()
    }

    override fun onPause() {
        Timber.d("onPause")
        super.onPause()
        codeScanner?.releaseResources()
    }

    override fun onDestroyView() {
        Timber.d("onDestroyView")
        viewModel.updateInviteShown(inviteAlertDialog != null)
        viewModel.updateInviteResultDialogShown(requestedAlertDialog != null)
        inviteAlertDialog?.dismiss()
        requestedAlertDialog?.dismiss()
        _binding = null
        _dialogInviteBinding = null
        _dialogAcceptContactBinding = null
        super.onDestroyView()
    }

    /**
     * Observes changes to the UI State from [ScanCodeViewModel]
     */
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.state.collect {
                    if (it.finishActivity) {
                        val intent = Intent()
                        intent.putExtra("mail", it.email)
                        requireActivity().setResult(Activity.RESULT_OK, intent)
                        requireActivity().finish()
                    } else if (it.showInviteResultDialog) {
                        showInviteResultDialog(
                            it.dialogTitleContent,
                            it.dialogTextContent,
                            it.success,
                            it.printEmail
                        )
                    } else if (it.showInviteDialog) {
                        showInviteDialog(
                            it.email,
                            it.scannedContactLinkResult?.contactName,
                            it.scannedContactLinkResult?.isContact ?: false
                        )
                    }
                }
            }
        }
    }

    private fun setupView() {
        val aB = (activity as AppCompatActivity).supportActionBar
        aB?.apply {
            title = requireContext().getFormattedStringOrDefault(R.string.section_qr_code)
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        codeScanner = CodeScanner(requireContext(), binding.scannerView)
        codeScanner?.apply {
            setDecodeCallback { result ->
                if (inviteAlertDialog == null && requestedAlertDialog == null) {
                    activity?.runOnUiThread {
                        invite(result)
                    }
                }
            }

            setErrorCallback { error: Exception ->
                Timber.w("Start preview error:${error.message}, retry:${mStartPreviewRetried + 1}")

                if (mStartPreviewRetried++ < START_PREVIEW_RETRY) {
                    handler?.postDelayed(
                        { codeScanner?.startPreview() },
                        START_PREVIEW_DELAY.toLong()
                    )
                } else {
                    Timber.e("Start preview failed")
                }
            }
        }

        binding.invalidCodeText.visibility = View.GONE
        binding.scannerView.setOnClickListener {
            codeScanner?.startPreview()
            if (binding.invalidCodeText.isVisible) {
                binding.invalidCodeText.visibility = View.GONE
            }
        }

        uiState.run {
            if (inviteDialogShown) {
                showInviteDialog(
                    email,
                    scannedContactLinkResult?.contactName,
                    scannedContactLinkResult?.isContact ?: false
                )
            } else if (inviteResultDialogShown) {
                this@ScanCodeFragment.showInviteResultDialog(
                    dialogTitleContent,
                    dialogTextContent,
                    success,
                    printEmail
                )
            }
        }
    }

    /**
     * Method to display an alert dialog just after scan QR code and send the contact invitation to
     * communicate the operation result to the user.
     *
     * @param title      String resource ID of the dialog title.
     * @param text       String resource ID of the dialog message.
     * @param success    Flag to indicate if the operation finished with success or not.
     * @param printEmail Flag to indicate if the dialog message includes contact email or not.
     */
    private fun showInviteResultDialog(
        title: Int,
        text: Int,
        success: Boolean,
        printEmail: Boolean,
    ) {
        if (requestedAlertDialog == null) {
            _dialogInviteBinding = DialogInviteBinding.inflate(layoutInflater)

            requestedAlertDialog = MaterialAlertDialogBuilder(requireContext())
                .setView(dialogInviteBinding.root)
                .create().apply {
                    setOnDismissListener {
                        _dialogInviteBinding = null
                        requestedAlertDialog = null
                        viewModel.updateShowInviteResultDialog(false)
                        if (success) {
                            codeScanner?.releaseResources()
                            activity?.finish()
                        } else {
                            codeScanner?.startPreview()
                        }
                    }
                }

            dialogInviteBinding.dialogInviteButton.setOnClickListener {
                viewModel.updateInviteResultDialogShown(false)
                codeScanner?.releaseResources()
                requestedAlertDialog?.dismiss()
                if (uiState.success) {
                    activity?.finish()
                } else {
                    codeScanner?.startPreview()
                }
            }
        }

        dialogInviteBinding.dialogInviteTitle.text =
            requireContext().getFormattedStringOrDefault(title)

        if (printEmail) {
            dialogInviteBinding.dialogInviteText.text =
                requireContext().getFormattedStringOrDefault(text, uiState.email)
        } else {
            dialogInviteBinding.dialogInviteText.text =
                requireContext().getFormattedStringOrDefault(text)
        }

        requestedAlertDialog?.show()
    }

    private fun invite(rawResult: Result) {
        val contactLink = rawResult.text
        val s = contactLink.split("C!").toTypedArray()

        codeScanner?.startPreview()

        if (s.size <= 1) {
            binding.invalidCodeText.visibility = View.VISIBLE
        } else if (s[0] != "https://mega.nz/") {
            binding.invalidCodeText.visibility = View.VISIBLE
        } else {
            binding.invalidCodeText.visibility = View.GONE
            viewModel.queryContactLink(s[1])
        }
    }

    private fun sendInvitation() {
        Timber.d("sendInvitation")
        megaApi.inviteContact(
            uiState.email,
            null,
            MegaContactRequest.INVITE_ACTION_ADD,
            uiState.scannedContactLinkResult?.handle ?: -1,
            activity as QRCodeActivity?
        )
    }

    /**
     * Method to set scanned contact avatar in the dialog
     */
    private fun setAvatar() = uiState.scannedContactLinkResult?.avatarFile?.run {
        Timber.d("Avatar path: $absolutePath")
        val imBitmap = BitmapFactory.decodeFile(absolutePath, BitmapFactory.Options())
        if (imBitmap == null) {
            Timber.d("Show default avatar")
            delete()
            setDefaultAvatar()
        } else {
            Timber.d("Show my avatar")
            dialogAcceptContactBinding.acceptContactAvatar.setImageBitmap(imBitmap)
            dialogAcceptContactBinding.acceptContactInitialLetter.visibility = View.GONE
        }
    } ?: setDefaultAvatar()

    /**
     * Method to set a default avatar
     */
    private fun setDefaultAvatar() {
        Timber.d("setDefaultAvatar")
        val defaultAvatar = Bitmap.createBitmap(
            DEFAULT_AVATAR_WIDTH_HEIGHT,
            DEFAULT_AVATAR_WIDTH_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val c = Canvas(defaultAvatar)
        val p = Paint()
        p.isAntiAlias = true
        p.color = uiState.scannedContactLinkResult?.avatarColor
            ?: ContextCompat.getColor(requireContext(), R.color.red_600_red_300)
        val radius: Int =
            if (defaultAvatar.width < defaultAvatar.height) defaultAvatar.width / 2 else defaultAvatar.height / 2
        c.drawCircle(
            (defaultAvatar.width / 2).toFloat(),
            (defaultAvatar.height / 2).toFloat(),
            radius.toFloat(),
            p
        )
        dialogAcceptContactBinding.acceptContactAvatar.setImageBitmap(defaultAvatar)
        val density = resources.displayMetrics.density
        val avatarTextSize = getAvatarTextSize(density)
        Timber.d("DENSITY: $density:::: $avatarTextSize")
        val fullName: String? =
            uiState.scannedContactLinkResult?.contactName ?: uiState.email
        if (fullName != null && fullName.isNotEmpty()) {
            var firstLetter = fullName[0].toString() + ""
            firstLetter = firstLetter.uppercase(Locale.getDefault())
            dialogAcceptContactBinding.acceptContactInitialLetter.apply {
                text = firstLetter
                textSize = 30f
                setTextColor(Color.WHITE)
                visibility = View.VISIBLE
            }
        }
    }

    private fun getAvatarTextSize(density: Float): Int {
        val textSize: Float = if (density > 3.0) {
            density * (DisplayMetrics.DENSITY_XXXHIGH / 72.0f)
        } else if (density > 2.0) {
            density * (DisplayMetrics.DENSITY_XXHIGH / 72.0f)
        } else if (density > 1.5) {
            density * (DisplayMetrics.DENSITY_XHIGH / 72.0f)
        } else if (density > 1.0) {
            density * (72.0f / DisplayMetrics.DENSITY_HIGH / 72.0f)
        } else if (density > 0.75) {
            density * (72.0f / DisplayMetrics.DENSITY_MEDIUM / 72.0f)
        } else {
            density * (72.0f / DisplayMetrics.DENSITY_LOW / 72.0f)
        }
        return textSize.toInt()
    }

    private fun showInviteDialog(myEmail: String?, contactName: String?, isContact: Boolean) {
        if (inviteAlertDialog != null) {
            dialogAcceptContactBinding.apply {
                acceptContactName.text = contactName

                if (isContact) {
                    acceptContactMail.text =
                        requireContext().getFormattedStringOrDefault(
                            R.string.context_contact_already_exists,
                            myEmail
                        )
                    acceptContactInvite.visibility = View.GONE
                    viewContact.visibility = View.VISIBLE
                } else {
                    acceptContactMail.text = myEmail
                    acceptContactInvite.visibility = View.VISIBLE
                    viewContact.visibility = View.GONE
                }

                setAvatar()
            }
        } else {
            _dialogAcceptContactBinding = DialogAcceptContactBinding.inflate(layoutInflater)

            dialogAcceptContactBinding.apply {
                inviteAlertDialog = MaterialAlertDialogBuilder(requireContext())
                    .setView(root)
                    .create().apply {
                        setOnDismissListener {
                            Timber.d("onDismiss")
                            _dialogAcceptContactBinding = null
                            inviteAlertDialog = null
                            viewModel.updateShowInviteDialog(false)
                            codeScanner?.startPreview()
                        }
                    }

                acceptContactInvite.setOnClickListener {
                    viewModel.updateInviteShown(false)
                    sendInvitation()
                    if (inviteAlertDialog != null) {
                        inviteAlertDialog?.dismiss()
                    }
                }

                viewContact.setOnClickListener {
                    viewModel.updateInviteShown(false)
                    codeScanner?.releaseResources()
                    inviteAlertDialog?.dismiss()
                    ContactUtil.openContactInfoActivity(context, uiState.email)
                    activity?.finish()
                }

                if (isContact) {
                    acceptContactMail.text =
                        requireContext().getFormattedStringOrDefault(
                            R.string.context_contact_already_exists,
                            myEmail
                        )
                    acceptContactInvite.visibility = View.GONE
                    viewContact.visibility = View.VISIBLE
                } else {
                    acceptContactMail.text = myEmail
                    acceptContactInvite.visibility = View.VISIBLE
                    viewContact.visibility = View.GONE
                }

                acceptContactName.text = contactName
                setAvatar()
            }
        }
        inviteAlertDialog?.show()
    }

    companion object {
        private const val DEFAULT_AVATAR_WIDTH_HEIGHT = 150

        // Bug #14988: disableLocalCamera() may hasn't completely released the camera resource as
        // the megaChatApi.disableVideo() is async call. A simply way to solve the issue is
        // setErrorCallback for CodeScanner. If error occurs, retry in 300ms. Retry 5 times max.
        private const val START_PREVIEW_RETRY = 5
        private const val START_PREVIEW_DELAY = 300

        /**
         * New instance of ScanCodeFragment
         */
        @JvmStatic
        fun newInstance(): ScanCodeFragment {
            Timber.d("newInstance")
            return ScanCodeFragment()
        }
    }
}