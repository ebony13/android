package mega.privacy.android.app.lollipop;

import android.os.Bundle;
import android.os.Handler;

import mega.privacy.android.app.BaseActivity;
import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.PinUtil;
import mega.privacy.android.app.utils.JobUtil;
import mega.privacy.android.app.utils.LogUtil;
import mega.privacy.android.app.utils.Util;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaChatApiAndroid;


public class PinActivityLollipop extends BaseActivity {
	
	private MegaApiAndroid megaApi;
	private MegaChatApiAndroid megaChatApi;

    private static long lastStart;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (megaApi == null){
			megaApi = ((MegaApplication)getApplication()).getMegaApi();
		}

		if(Util.isChatEnabled()){
			if (megaChatApi == null){
				megaChatApi = ((MegaApplication)getApplication()).getMegaChatApi();
			}
		}
	}

	@Override
	protected void onPause() {
		LogUtil.logDebug("onPause");
		if (megaApi == null){
			megaApi = ((MegaApplication)getApplication()).getMegaApi();
		}

		if(Util.isChatEnabled()){
			if (megaChatApi == null){
				megaChatApi = ((MegaApplication)getApplication()).getMegaChatApi();
			}
		}
		PinUtil.pause(this);
		lastStart = System.currentTimeMillis();
		MegaApplication.activityPaused();
		super.onPause();
	}
	
	@Override
	protected void onResume() {
		LogUtil.logDebug("onResume");

		super.onResume();
        Util.setAppFontSize(this);
		MegaApplication.activityResumed();

		if (megaApi == null){
			megaApi = ((MegaApplication)getApplication()).getMegaApi();
		}

		if(Util.isChatEnabled()){
			if (megaChatApi == null){
				megaChatApi = ((MegaApplication)getApplication()).getMegaChatApi();
			}
		}

		if (megaChatApi != null){
			megaChatApi.retryPendingConnections(false, null);
		}

		if(MegaApplication.isShowPinScreen()){
			PinUtil.resume(this);
		}

		//if leave the APP then get back, should trigger camera upload.
        if(System.currentTimeMillis() - lastStart > 1000) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    JobUtil.startCameraUploadService(PinActivityLollipop.this);
                }
            }, 3000);
        }
	}
}
