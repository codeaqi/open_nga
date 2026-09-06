package gov.anzong.androidnga.activity;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import sp.phone.param.ParamKey;
import sp.phone.param.TopicListParam;
import sp.phone.ui.fragment.TopicCacheFragment;
import sp.phone.ui.fragment.TopicFolderFragment;

/**
 * @author Justwen
 */
public class TopicCacheActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setToolbarEnabled(true);
        super.onCreate(savedInstanceState);
        TopicListParam param = new TopicListParam();
        param.loadCache = true;
        Bundle bundle = new Bundle();
        bundle.putParcelable(ParamKey.KEY_PARAM, param);
        Fragment fragment = new TopicCacheFragment();
        fragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }

    /** 在文件夹里按返回先退回根视图，而不是直接退出界面 */
    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(android.R.id.content);
        if (fragment instanceof TopicFolderFragment
                && ((TopicFolderFragment) fragment).onBackPressedHandled()) {
            return;
        }
        super.onBackPressed();
    }
}
