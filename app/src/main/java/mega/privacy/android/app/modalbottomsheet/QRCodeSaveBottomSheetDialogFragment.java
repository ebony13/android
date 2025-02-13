package mega.privacy.android.app.modalbottomsheet;

import static mega.privacy.android.app.main.qrcode.MyCodeFragment.QR_IMAGE_FILE_NAME;
import static mega.privacy.android.app.utils.AlertsAndWarnings.showOverDiskQuotaPaywallWarning;
import static mega.privacy.android.app.utils.CacheFolderManager.buildQrFile;
import static mega.privacy.android.app.utils.Constants.REQUEST_DOWNLOAD_FOLDER;
import static mega.privacy.android.app.utils.FileUtil.isFileAvailable;
import static mega.privacy.android.app.utils.Util.showSnackbar;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.util.ArrayList;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import mega.privacy.android.app.R;
import mega.privacy.android.app.ShareInfo;
import mega.privacy.android.app.main.FileStorageActivity;
import mega.privacy.android.app.main.qrcode.QRCodeActivity;
import mega.privacy.android.app.namecollision.data.NameCollision;
import mega.privacy.android.app.namecollision.usecase.CheckNameCollisionUseCase;
import mega.privacy.android.app.presentation.bottomsheet.QrCodeSaveBottomSheetDialogViewModel;
import mega.privacy.android.app.usecase.UploadUseCase;
import mega.privacy.android.app.usecase.exception.MegaNodeException;
import mega.privacy.android.app.utils.StringResourcesUtils;
import mega.privacy.android.app.utils.permission.PermissionUtils;
import mega.privacy.android.domain.entity.StorageState;
import nz.mega.sdk.MegaNode;
import timber.log.Timber;

@AndroidEntryPoint
public class QRCodeSaveBottomSheetDialogFragment extends BaseBottomSheetDialogFragment implements View.OnClickListener {

    @Inject
    CheckNameCollisionUseCase checkNameCollisionUseCase;
    @Inject
    UploadUseCase uploadUseCase;

    private QrCodeSaveBottomSheetDialogViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        contentView = View.inflate(getContext(), R.layout.bottom_sheet_qr_code, null);
        itemsLayout = contentView.findViewById(R.id.items_layout);
        return contentView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        contentView.findViewById(R.id.qr_code_saveTo_cloud_layout).setOnClickListener(this);
        contentView.findViewById(R.id.qr_code_saveTo_fileSystem_layout).setOnClickListener(this);
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(QrCodeSaveBottomSheetDialogViewModel.class);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.qr_code_saveTo_cloud_layout:
                saveToCloudDrive();
                break;

            case R.id.qr_code_saveTo_fileSystem_layout:
                saveToFileSystem();
                break;
        }

        setStateBottomSheetBehaviorHidden();
    }

    private void saveToCloudDrive() {
        MegaNode parentNode = megaApi.getRootNode();
        String myEmail = megaApi.getMyUser().getEmail();
        File qrFile = buildQrFile(getActivity(), myEmail + QR_IMAGE_FILE_NAME);

        if (!isFileAvailable(qrFile)) {
            showSnackbar(requireActivity(), StringResourcesUtils.getString(R.string.error_upload_qr));
            return;
        }

        if (viewModel.getStorageState() == StorageState.PayWall) {
            showOverDiskQuotaPaywallWarning();
            return;
        }

        checkNameCollisionUseCase.check(qrFile.getName(), parentNode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(handle -> {
                            ArrayList<NameCollision> list = new ArrayList<>();
                            list.add(NameCollision.Upload.getUploadCollision(handle, qrFile,
                                    parentNode.getHandle()));
                            ((QRCodeActivity) requireActivity()).nameCollisionActivityContract.launch(list);
                        },
                        throwable -> {
                            if (throwable instanceof MegaNodeException.ParentDoesNotExistException) {
                                showSnackbar(requireActivity(), StringResourcesUtils.getString(R.string.error_upload_qr));
                            } else if (throwable instanceof MegaNodeException.ChildDoesNotExistsException){
                                ShareInfo info = ShareInfo.infoFromFile(qrFile);
                                if (info == null) {
                                    showSnackbar(requireActivity(), StringResourcesUtils.getString(R.string.error_upload_qr));
                                    return;
                                }

                                PermissionUtils.checkNotificationsPermission(requireActivity());

                                String text = StringResourcesUtils.getString(R.string.save_qr_cloud_drive, qrFile.getName());

                                uploadUseCase.upload(requireActivity(), info, null, parentNode.getHandle())
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe(() -> showSnackbar(requireActivity(), text), Timber::e);
                            }
                        });
    }

    private void saveToFileSystem() {
        Intent intent = new Intent(getActivity(), FileStorageActivity.class);
        intent.putExtra(FileStorageActivity.PICK_FOLDER_TYPE, FileStorageActivity.PickFolderType.DOWNLOAD_FOLDER.getFolderType());
        intent.setAction(FileStorageActivity.Mode.PICK_FOLDER.getAction());
        ((QRCodeActivity) getActivity()).startActivityForResult(intent, REQUEST_DOWNLOAD_FOLDER);
    }
}
