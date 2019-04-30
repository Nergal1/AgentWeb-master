
/*
 * Copyright (C)  Justson(https://github.com/Justson/AgentWeb)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.just.agentweb;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.util.List;

import static android.provider.MediaStore.EXTRA_OUTPUT;



/**
 * 动作：权限、文件选择、相机选择等统一处理页面
 */
public final class ActionActivity extends Activity {

    public static final String KEY_ACTION = "KEY_ACTION";//动作关键字
    public static final String KEY_URI = "KEY_URI";//uri关键字
    public static final String KEY_FROM_INTENTION = "KEY_FROM_INTENTION";//意图来源关键字
    public static final String KEY_FILE_CHOOSER_INTENT = "KEY_FILE_CHOOSER_INTENT";//文件选择意图关键字
    private static RationaleListener mRationaleListener;
    private static PermissionListener mPermissionListener;
    private static ChooserListener mChooserListener;
    private static final String TAG = ActionActivity.class.getSimpleName();
    private Action mAction;
    public static final int REQUEST_CODE = 0x254;

    public static void start(final Activity activity, Action action) {
        Intent mIntent = new Intent(activity, ActionActivity.class);
        mIntent.putExtra(KEY_ACTION, action);
//        mIntent.setExtrasClassLoader(Action.class.getClassLoader());
        activity.startActivity(mIntent);
        setRationaleListener(new RationaleListener() {

            @Override
            public void onRationaleResult(boolean showRationale, String permission) {
                Log.i(TAG, "Rationale:" + showRationale + "  permission:" + permission);
                // TODO: 2019-04-30 被禁止权限处理，添加需要处理的权限
                switch (permission){
                    case "android.permission.ACCESS_FINE_LOCATION"://位置权限
                    case "android.permission.ACCESS_COARSE_LOCATION"://位置权限
                        //跳转到权限配置页面
                        Toast.makeText(activity.getApplicationContext(),"请开启应用定位权限",Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                        intent.setData(uri);
                        activity.startActivity(intent);
                        break;
                }
            }
        });

    }

    public static void setRationaleListener(RationaleListener rationaleListener){
        mRationaleListener = rationaleListener;
    }

    public static void setChooserListener(ChooserListener chooserListener) {
        mChooserListener = chooserListener;
    }

    public static void setPermissionListener(PermissionListener permissionListener) {
        mPermissionListener = permissionListener;
    }

    private void cancelAction() {
        mChooserListener = null;
        mPermissionListener = null;
        mRationaleListener = null;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            LogUtils.i(TAG, "savedInstanceState:" + savedInstanceState);
            return;
        }
        Intent intent = getIntent();
        mAction = intent.getParcelableExtra(KEY_ACTION);
        if (mAction == null) {
            cancelAction();
            this.finish();
            return;
        }
        if (mAction.getAction() == Action.ACTION_PERMISSION) {
            permission(mAction);
        } else if (mAction.getAction() == Action.ACTION_CAMERA) {
            realOpenCamera();
        } else {
            fetchFile(mAction);
        }
    }

    private void fetchFile(Action action) {
        if (mChooserListener == null) {
            finish();
        }
        realOpenFileChooser();
    }

    private void realOpenFileChooser() {
        try {
            if (mChooserListener == null) {
                finish();
                return;
            }
            Intent mIntent = getIntent().getParcelableExtra(KEY_FILE_CHOOSER_INTENT);
            if (mIntent == null) {
                cancelAction();
                return;
            }
            this.startActivityForResult(mIntent, REQUEST_CODE);
        } catch (Throwable throwable) {
            LogUtils.i(TAG, "找不到文件选择器");
            chooserActionCallback(-1, null);
            if (LogUtils.isDebug()) {
                throwable.printStackTrace();
            }
        }
    }

    private void chooserActionCallback(int resultCode, Intent data) {
        if (mChooserListener != null) {
            mChooserListener.onChoiceResult(REQUEST_CODE, resultCode, data);
            mChooserListener = null;
        }
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            chooserActionCallback(resultCode, mUri != null ? new Intent().putExtra(KEY_URI, mUri) : data);
        }
    }

    private void permission(Action action) {
        List<String> permissions = action.getPermissions();
        if (AgentWebUtils.isEmptyCollection(permissions)) {
            mPermissionListener = null;
            mRationaleListener = null;
            finish();
            return;
        }

        if (mRationaleListener != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                for (String permission : permissions) {
                    //如果应用之前请求过此权限但用户拒绝了请求，此方法将返回 true；如果用户选择了拒绝权限并且勾选不再提醒，那么这个方法会返回false
                    boolean rationale = shouldShowRequestPermissionRationale(permission);
                    if (!rationale) {
                        mRationaleListener.onRationaleResult(rationale,permission);
                    }
                }
            }

            mRationaleListener = null;
            finish();
            return;
        }
        if (mPermissionListener != null){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(permissions.toArray(new String[]{}), 1);
            }
        }
    }

    private Uri mUri;

    private void realOpenCamera() {
        try {
            if (mChooserListener == null){
                finish();
            }
            File mFile = AgentWebUtils.createImageFile(this);
            if (mFile == null) {
                mChooserListener.onChoiceResult(REQUEST_CODE, Activity.RESULT_CANCELED, null);
                mChooserListener = null;
                finish();
            }
            Intent intent = AgentWebUtils.getIntentCaptureCompat(this, mFile);
            // 指定开启系统相机的Action
            mUri = intent.getParcelableExtra(EXTRA_OUTPUT);
            this.startActivityForResult(intent, REQUEST_CODE);
        } catch (Throwable ignore) {
            LogUtils.e(TAG, "找不到系统相机");
            if (mChooserListener != null) {
                mChooserListener.onChoiceResult(REQUEST_CODE, Activity.RESULT_CANCELED, null);
            }
            mChooserListener = null;
            if (LogUtils.isDebug()){
                ignore.printStackTrace();
            }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (mPermissionListener != null) {
            Bundle mBundle = new Bundle();
            mBundle.putInt(KEY_FROM_INTENTION, mAction.getFromIntention());
            mPermissionListener.onRequestPermissionsResult(permissions, grantResults, mBundle);
        }
        mPermissionListener = null;
        finish();
    }

    public interface RationaleListener {
        //权限禁止，并勾选记住。下次进入页面，权限处理
        void onRationaleResult(boolean showRationale, String permission);
    }

    public interface PermissionListener {
        void onRequestPermissionsResult(@NonNull String[] permissions, @NonNull int[] grantResults, Bundle extras);
    }

    public interface ChooserListener {
        void onChoiceResult(int requestCode, int resultCode, Intent data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
