package tribalage.game.ingentuity.com.recoverdata;

import android.app.ProgressDialog;
import android.content.pm.PackageInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;

import es.dmoral.toasty.Toasty;

public class MainActivity extends AppCompatActivity {

    /**
     * 应用的包名
     */
    private static final String AppPackageName = "com.tap4fun.brutalage_test";

    /**
     * 应用的启动类
     */
    private static final String AppLauncherActivity = "com.tap4fun.project.CustomGameActivity";

    /**
     * 恢复的目标应用 data 存储绝对路径
     */
    private static final String BasePath = "/data/data/com.tap4fun.brutalage_test";

    /**
     * 存储备份的文件夹名字
     */
    private static final String SaveBackupDirName = "Pictures/野蛮时代";

    Button btnRefresh;
    Button btnRecover;
    ListView lvFiles;
    ArrayAdapter<String> arrayAdapter;

    private String curSelectName = null;
    private ProgressDialog dialog;
    private long mPressedTime = 0;
    private AsyncTask<Void, Void, Boolean> asyncTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnRefresh = (Button) findViewById(R.id.btRefresh);
        btnRecover = (Button) findViewById(R.id.btRecover);
        lvFiles = (ListView) findViewById(R.id.lvFiles);
        lvFiles.setAdapter(arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1));
        lvFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                curSelectName = arrayAdapter.getItem(position);
            }
        });

        //添加弹出的对话框
        dialog = new ProgressDialog(this);
        dialog.setTitle("提示");
        dialog.setMessage("备份恢复中，请稍后···");
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (asyncTask != null) {
            asyncTask.cancel(false);
        }
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        updateAdapterData();
    }


    @Override
    public void onBackPressed() {
        // 获取第一次按键时间
        long mNowTime = System.currentTimeMillis();
        if ((mNowTime - mPressedTime) > 2000) {
            // 比较两次按键时间差
            Toasty.info(this, "再按一次退出程序").show();
            mPressedTime = mNowTime;
        } else {
            // 退出程序
            finish();
        }
    }

    /**
     * 点击刷新
     */
    public void onClickRefresh(View view) {
        updateAdapterData();
    }

    /**
     * 点击恢复
     */
    public void onClickRecover(View view) {
        if (curSelectName == null) {
            Toasty.warning(MainActivity.this, "请先选择需要恢复项").show();
            return;
        }
        if (!isInstalled(AppPackageName)) {
            Toasty.warning(MainActivity.this, "请先安装需要恢复数据的应用").show();
            return;
        }
        asyncTask = new BackupTask().execute();
    }

    /**
     * 更新适配器数据
     */
    private void updateAdapterData() {
        // 更新 list view 的数据适配器
        arrayAdapter.clear();

        // 获取存储备份路径下的内容列表转换为 listView 数据项
        File saveBackupDirFile = getBackupFile();
        // 目标存在且是目录
        if (saveBackupDirFile.exists() && saveBackupDirFile.isDirectory()) {
            // 获取该目录下的所有文件和目录
            for (File childFile : saveBackupDirFile.listFiles()) {
                // 是文件夹
                if (childFile.isDirectory()) {
                    arrayAdapter.add(childFile.getName());
                }
            }
        }

        // 通知数据更新
        arrayAdapter.notifyDataSetChanged();
    }

    /**
     * 获取存储备份的文件夹
     */
    private File getBackupFile() {
        // 外部存储路径
        File sdDirFile = Environment.getExternalStorageDirectory().getAbsoluteFile();
        // 存储备份路径
        return new File(sdDirFile, SaveBackupDirName);
    }

    /**
     * 检查系统中是否安装了某个应用
     */
    public boolean isInstalled(final String packageName) {
        if (Strings.isNullOrEmpty(packageName)) {
            return false;
        }
        // 获取所有已安装程序的包信息
        for (PackageInfo temp : getPackageManager().getInstalledPackages(0)) {
            if (temp != null && temp.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 异步任务执行
     */
    private class BackupTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        //在界面上显示进度条
        protected void onPreExecute() {
            dialog.show();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            dialog.dismiss();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                // 指令集
                List<String> commands = Lists.newArrayList();

                // 清理指定应用的 data/data 数据
                if (new File(BasePath).exists()) {
                    commands.add(String.format("rm -r %s", BasePath));
                }
                // 复制
                commands.add(String.format("cp -r %s %s", new File(getBackupFile(), curSelectName).getAbsolutePath(), BasePath));
                // 修改权限
                commands.add(String.format("chmod -R 777 %s", BasePath));
                // 杀死程序
                commands.add(String.format("am force-stop %s", AppPackageName));
                // 启动程序
                commands.add(String.format("am start -n %s/%s", AppPackageName, AppLauncherActivity));

                // 执行
                ShellUtils.CommandResult commandResult = ShellUtils.execCommand(commands, true);

                Log.d("备份", commandResult.toString());
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            dialog.dismiss();
            if (result) {
                Toasty.success(MainActivity.this, String.format("恢复成功:%s", curSelectName), Toast.LENGTH_LONG).show();
            } else {
                Toasty.error(MainActivity.this, String.format("恢复失败:%s", curSelectName), Toast.LENGTH_SHORT).show();
            }
        }
    }

}
