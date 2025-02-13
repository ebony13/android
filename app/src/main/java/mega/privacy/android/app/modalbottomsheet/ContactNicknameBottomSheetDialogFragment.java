package mega.privacy.android.app.modalbottomsheet;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import mega.privacy.android.app.R;
import mega.privacy.android.app.main.ContactInfoActivity;

import static mega.privacy.android.app.utils.Constants.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ContactNicknameBottomSheetDialogFragment extends BaseBottomSheetDialogFragment implements View.OnClickListener {

    private String nickname;
    private ContactInfoActivity contactInfoActivity = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        contentView = View.inflate(getContext(), R.layout.bottom_sheet_nickname, null);
        itemsLayout = contentView.findViewById(R.id.items_layout);

        if (savedInstanceState != null) {
            nickname = savedInstanceState.getString(EXTRA_USER_NICKNAME, null);
        } else if (requireActivity() instanceof ContactInfoActivity) {
            contactInfoActivity = ((ContactInfoActivity) requireActivity());
            nickname = contactInfoActivity.getNickname();
        }

        return contentView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        contentView.findViewById(R.id.edit_nickname_layout).setOnClickListener(this);
        contentView.findViewById(R.id.remove_nickname_layout).setOnClickListener(this);
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onClick(View v) {
        if (contactInfoActivity == null) return;
        switch (v.getId()) {
            case R.id.edit_nickname_layout:
                contactInfoActivity.showConfirmationSetNickname(nickname);
                break;

            case R.id.remove_nickname_layout:
                contactInfoActivity.addNickname(null, null);
                break;
        }

        setStateBottomSheetBehaviorHidden();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_USER_NICKNAME, nickname);
    }
}
