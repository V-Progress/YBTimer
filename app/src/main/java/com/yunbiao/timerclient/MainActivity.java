package com.yunbiao.timerclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private final String TAG = getClass().getSimpleName();

    private final String ROOT_DIR = Environment.getExternalStorageDirectory().getAbsolutePath();//外存根目录
    private final String RES_DIR_NAME = "yunbiao_time";//应用根目录
    private final String FILE_NAME = "time.txt";//文件名

    private final String KEY_TITLE = "title";//标题
    private final String KEY_DATE = "date";//计时到期日
    private final String KEY_IMAGE = "image";//背景图片

    private TextView tvTimerTitle;
    private TextView tvTimerNum;
    private LinearLayout llTimerArea;
    private LinearLayout llRootView;

    private File rootFile;//根目录文件
    private ExecutorService singleThread;//单线程操作
    private SimpleDateFormat simpleDateFormat;

    //取得授权的设备的到期时间
    private static Map<String,Date> bindList = new HashMap<>();
    static {
        bindList.put("28:f3:66:e9:b0:7c",new Date(119, 12, 18));//118:当前年份减去1900，11:十二月 19:日期
        bindList.put("14:6b:9c:1a:64:e6",new Date(119, 12, 18));
    }

    private TextClock tcDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //判断是否授权
        if(!auth()){
            setContentView(R.layout.activity_wel);
            return;
        }

        setContentView(R.layout.activity_main);
        registBroad();
        initView();
        initData();
        checkFile();
    }

    private void initView() {
        tvTimerTitle = (TextView) findViewById(R.id.tv_timer_title);
        tvTimerNum = (TextView) findViewById(R.id.tv_timer_num);
        llTimerArea = (LinearLayout) findViewById(R.id.ll_timer_area);
        llRootView = (LinearLayout) findViewById(R.id.ll_rootView);
        tcDate = (TextClock) findViewById(R.id.tc_date);
    }

    private void initData() {
        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        singleThread = Executors.newSingleThreadExecutor();
        rootFile = new File(ROOT_DIR, RES_DIR_NAME);
    }

    private void registBroad() {
        //USB广播监听
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addDataScheme("file");
        registerReceiver(usbBroad, intentFilter);
    }

    //文件筛选
    private FilenameFilter filenameFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return TextUtils.equals(FILE_NAME, name);
        }
    };

    //检查文件，如果有，直接读取
    private void checkFile() {
        singleThread.execute(new Runnable() {
            @Override
            public void run() {

                if (!rootFile.exists()) {
                    Log.e(TAG, "当前目录不存在");
                    boolean mkdirs = rootFile.mkdirs();
                    Log.d(TAG, "创建结果" + mkdirs);
                }

                File[] files = rootFile.listFiles(filenameFilter);
                if (files == null || files.length <= 0) {
                    Log.e(TAG, "没有文件");
                    setEmptyText("目录 " + ROOT_DIR + "/" + RES_DIR_NAME + " 中不存在设置文件");
                    return;
                }

                File timeFile = files[0];
                StringBuilder str = new StringBuilder();
                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(timeFile), "GBK"));
                    String tempString = null;
                    while ((tempString = bufferedReader.readLine()) != null) {
                        str.append(tempString);
                    }
                    bufferedReader.close();
                } catch (FileNotFoundException e) {
                    setEmptyText(FILE_NAME + " 文件未找到");
                } catch (IOException e) {
                    setEmptyText(FILE_NAME + "文件读取失败，请重试");
                }

                JSONObject jsonObject = null;
                try {
                    jsonObject = new JSONObject(str.toString());
                    final String title = jsonObject.getString(KEY_TITLE);
                    final String date = jsonObject.getString(KEY_DATE);
                    final String image = jsonObject.getString(KEY_IMAGE);

                    if (TextUtils.isEmpty(title) || TextUtils.isEmpty(date)) {
                        setEmptyText("标题或日期未找到，请检查" + FILE_NAME + " 文件");
                        return;
                    }

                    Date endDate = simpleDateFormat.parse(date);
                    Date currDate = simpleDateFormat.parse(simpleDateFormat.format(new Date(System.currentTimeMillis())));

                    final long daysBetween = (endDate.getTime() - currDate.getTime() + 1000000) / (60 * 60 * 24 * 1000);

                    final File imgFile = new File(rootFile, image);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!TextUtils.isEmpty(image)) {
                                if (imgFile.exists()) {
                                    Drawable drawable = Drawable.createFromPath(imgFile.getAbsolutePath());
                                    drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                        llRootView.setBackground(drawable);
                                    }
                                } else {
                                    Toast.makeText(MainActivity.this, "目录中不存在背景图片", Toast.LENGTH_LONG).show();
                                }
                            }

                            showTitle(title);
                            showTime(String.valueOf(daysBetween));
                        }
                    });

                } catch (JSONException e) {
                    setEmptyText("文件格式错误，请检查" + FILE_NAME + " 文件");
                } catch (ParseException e) {
                    setEmptyText("目标日期格式不正确，正确格式为1970-01-01");
                }
            }
        });
    }

    BroadcastReceiver usbBroad = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String dataString = intent.getDataString().substring(7);
            if (!dataString.matches("/mnt/usbhost\\d") && !dataString.matches("/storage/usbhost\\d")) {
                Toast.makeText(context, "请插入SD卡或者U盘" + dataString, Toast.LENGTH_SHORT).show();
                return;
            }
            Log.d(TAG, "插入Upan" + dataString);

            singleThread.execute(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "开始执行复制-----");
                    File usbDir = new File(dataString, RES_DIR_NAME);

                    if (!usbDir.exists()) {
                        Log.e(TAG, usbDir.getAbsolutePath() + "目录不存在");
                        setEmptyText("U盘中不存在" + RES_DIR_NAME + " 目录");
                        return;
                    }
                    Log.d(TAG, "存在" + usbDir.getAbsolutePath() + "目录");

                    File[] usbFiles = usbDir.listFiles(filenameFilter);
                    if (usbFiles == null || usbFiles.length <= 0) {
                        Log.e(TAG, FILE_NAME + "文件不存在");
                        setEmptyText("U盘中不存在" + FILE_NAME + " 文件");
                        return;
                    }
                    Log.d(TAG, "存在" + FILE_NAME + "文件");

                    try {
                        File usbFile = usbFiles[0];
                        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(usbFile));
                        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(rootFile.getAbsoluteFile(), usbFile.getName())));

                        int i;
                        for (byte[] b = new byte[8192]; (i = bis.read(b)) != -1; ) {
                            bos.write(b, 0, i);
                        }

                        bos.flush();
                        bis.close();
                        bos.close();
                    } catch (FileNotFoundException e) {
                        setEmptyText(FILE_NAME + " 文件未找到");
                    } catch (IOException e) {
                        setEmptyText(FILE_NAME + " 文件读取失败，请重试");
                    }

                    checkFile();
                }
            });
        }
    };

    //判断如果map中包含当前mac地址且日期未过期
    private boolean auth(){
        String mac = getLocalMacAddressFromWifiInfo(this);
        Set<Map.Entry<String, Date>> entries = bindList.entrySet();
        for (Map.Entry<String, Date> entry : entries) {
            if(TextUtils.equals(entry.getKey(),mac)
                    & !new Date().after(entry.getValue())){
                return true;
            }
        }
        return false;
    }

    /**
     * 根据wifi信息获取本地mac
     * @param context
     * @return
     */
    public String getLocalMacAddressFromWifiInfo(Context context){
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if(!wifi.isWifiEnabled()){
            boolean b = wifi.setWifiEnabled(true);
            Log.d(TAG,"WIFI被关闭，先打开"+b);
        }

        WifiInfo winfo = wifi.getConnectionInfo();
        String mac =  winfo.getMacAddress();
        if(!TextUtils.isEmpty(mac)){
            mac = mac.toLowerCase();
        }
        return mac;
    }

    //设置空文件提示
    private void setEmptyText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvTimerTitle.setText(text);
                llTimerArea.setVisibility(View.INVISIBLE);
            }
        });
    }

    //设置标题
    private void showTitle(String msg) {
        tvTimerTitle.setText(msg);
    }

    //设置时间
    private void showTime(String time) {
        llTimerArea.setVisibility(View.VISIBLE);
        tvTimerNum.setText(time);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbBroad);
    }
}
