package com.lx.picturesearch.activity;


import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.lidroid.xutils.HttpUtils;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.ResponseInfo;
import com.lidroid.xutils.http.callback.RequestCallBack;
import com.lidroid.xutils.http.client.HttpRequest;
import com.lx.picturesearch.Constants;
import com.lx.picturesearch.ISelect;
import com.lx.picturesearch.R;
import com.lx.picturesearch.adapter.ImageAdapter;
import com.lx.picturesearch.adapter.MyViewPagerAdapter;
import com.lx.picturesearch.util.SPUtil;
import com.lx.picturesearch.util.Utils;
import com.z.dragimageviewapplication.DragImageActivity;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity implements View.OnClickListener, ISelect, AdapterView.OnItemClickListener {

    //网格
    GridView gv_main;
    //信息条
    TextView tv_info;
    //进度条
    ProgressBar pb_hor;

    Button btn_stop;
    //适配器
    ImageAdapter adapter;
    //回退 下载 全选
    ImageView iv_main_back, iv_btn, iv_select;
    //设置 深度搜索
    RelativeLayout menu_main_corner, rl_deep_search,rl_main_check_picture;
    //主页侧滑菜单
    DrawerLayout drawer_main;

    //获得自定义的旋转进度框
    Dialog grabingDialog;//正在抓取自定义对话框

    String html;
    List<String> listPic =  new ArrayList<String>();


    private ViewPager viewPager;
    private LinearLayout group;//圆点指示器
    private ImageView[] ivPoints;//小圆点图片的集合
    private int totalPage; //总的页数
    private int mPageSize = 100; //每页显示的最大的数量
    private int viewPagerPosition;
    private List<View> viewPagerList;//GridView作为一个View对象添加到ViewPager集合中
    private Map<Integer,ImageAdapter> viewMap = new HashMap<Integer, ImageAdapter>();


    private void rebuild(){
        if(currURL.contains("ss=mb-") && null == SPUtil.readArray(this,currURL)) {
            for (String url : listPic) {
                SPUtil.saveArray(this, currURL, url);
            }
        }
        initView1();
        initData();
        adapter =viewMap.get(viewPagerPosition);
    }
    private void initView1() {
        // TODO Auto-generated method stub
        viewPager = (ViewPager)findViewById(R.id.viewpager);
        group = (LinearLayout)findViewById(R.id.points);


    }
    private void initData() {
        // TODO Auto-generated method stub
        //总的页数向上取整
        totalPage = (int) Math.ceil(listPic.size() * 1.0 / mPageSize);
        viewPagerList = new ArrayList<View>();
        for(int i = 0; i < totalPage; i++){
            //每个页面都是inflate出一个新实例
            final GridView gridView = (GridView)View.inflate(this, R.layout.item_gridview, null);
            ImageAdapter adapter1 = new ImageAdapter(this, listPic, i, mPageSize);
             adapter1.setiSelect(this);
            gridView.setAdapter(adapter1);
            viewMap.put(i,adapter1);
            //添加item点击监听
            gridView.setOnItemClickListener(this);

            //每一个GridView作为一个View对象添加到ViewPager集合中      
            viewPagerList.add(gridView);
        }
        //设置ViewPager适配器
        viewPager.setAdapter(new MyViewPagerAdapter(viewPagerList));

        //添加小圆点
        ivPoints = new ImageView[totalPage];
        for(int i = 0; i < totalPage; i++){
            //循坏加入点点图片组
            ivPoints[i] = new ImageView(this);
            if(i==0){
                ivPoints[i].setImageResource(R.drawable.list_longpressed_holo);
            }else {
                ivPoints[i].setImageResource(R.drawable.list_longpressed_holo_light);
            }
            ivPoints[i].setPadding(8, 8, 8, 8);
            group.addView(ivPoints[i]);
        }
        //设置ViewPager的滑动监听，主要是设置点点的背景颜色的改变
        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener(){
            @Override
            public void onPageSelected(int position) {
                // TODO Auto-generated method stub
                //currentPage = position;
                viewPagerPosition = position;
                for(int i=0 ; i < totalPage; i++){
                    if(i == position){
                        ivPoints[i].setImageResource(R.drawable.list_longpressed_holo);
                    }else {
                        ivPoints[i].setImageResource(R.drawable.list_longpressed_holo_light);
                    }
                }
            }
        });
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        setContentView(R.layout.activity_main);

        Utils.setContext(this);

        // 接收从Home传来的参数
        String url = getIntent().getStringExtra("url");
        int state = getIntent().getIntExtra("state",0);


        //初始化控件
        initView();

        //抓取进度框
        grabingDialog = Utils.createLoadingDialog(this, "");

        updateIV_btn();

        //初始化监听
        initListener();


        if(state==Constants.S_SDCARD){
            // 获取文件信息
            menu_main_corner.setVisibility(View.GONE);
            listPic = getDownloadImages(Constants.SAVE_DIR);
            rebuild();
            Constants.state = Constants.S_SDCARD;// 更新状态
            update_infobar("");
        }else{
            getHttpImage(url);
        }



    }


    private void doMobaoUrl(){
        String site = currURL;
        List<String> similarLinks = new ArrayList<String>();
        String reg = "(.*[page|p]=)[\\d]+(.*)";
        if(site.matches(reg)){
            String siteBuilder = site.replaceAll (reg, "$1"+"xxxx"+"$2");
            for(int i=0;i< 100;i++){
                similarLinks.add(siteBuilder.replaceAll("xxxx",i+""));
            }
        }
        numTotalLinks = similarLinks.size();
        pb_hor.setMax(numTotalLinks);
        pb_hor.setProgress(numCurrLinks);
        doSimilarLink(similarLinks);
        if(similarLinks.isEmpty()){
            update_final("");
        }
    }

    private void doSimilarLink(List<String> similarLinks){
        for(final String href:similarLinks){
            // 新的抓取线程
            new HttpUtils().send(HttpRequest.HttpMethod.GET, href, new RequestCallBack<String>() {
                @Override
                public void onSuccess(ResponseInfo<String> objectResponseInfo) {

                    // 拿到2级页面源码
                    String html = objectResponseInfo.result;
                    /***********************解析<img>图片标签**************************/
                    // 框架JSoup:GitHub
                    Document doc = Jsoup.parse(html);// 解析HTML页面
                    // 获取图片
                    List<Element> imgs = doc.getElementsByTag("img");
                    //获取所有连接
                    Elements links = doc.select("a[href]");
                    // 存入集合=哈希表
                    // 所有图片地址
                    doParsePicture(imgs,links);
                    update_final(href);

                }

                @Override
                public void onFailure(HttpException e, String s) {

                }
            });
        }
    }

    private void initView() {
        menu_main_corner = (RelativeLayout) findViewById(R.id.menu_main_corner);
        rl_deep_search = (RelativeLayout) findViewById(R.id.rl_deep_search);
        rl_main_check_picture = (RelativeLayout) findViewById(R.id.rl_main_check_picture);
        iv_main_back = (ImageView) findViewById(R.id.iv_main_back);
        tv_info = (TextView) findViewById(R.id.tv_info);
        btn_stop = (Button) findViewById(R.id.btn_stop);
        iv_btn = (ImageView) findViewById(R.id.iv_btn);
        iv_select = (ImageView) findViewById(R.id.iv_select);
        pb_hor = (ProgressBar) findViewById(R.id.pb_hor);
        drawer_main = (DrawerLayout) findViewById(R.id.drawer_main);
    }

    //初始化右上角的隐藏图标
    private void updateIV_btn() {
        if (Constants.state == Constants.S_WEB) {
            //下载
            iv_btn.setImageResource(R.drawable.icon_s_download_press);
        } else {
            //删除
            iv_btn.setImageResource(R.drawable.op_del_press);
        }
    }

    private void initListener() {
        menu_main_corner.setOnClickListener(this);
        iv_main_back.setOnClickListener(this);
        rl_deep_search.setOnClickListener(this);
        rl_main_check_picture.setOnClickListener(this);
        btn_stop.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        //右边侧滑
        if (v.getId() == R.id.menu_main_corner) {
            if (drawer_main.isDrawerOpen(Gravity.RIGHT)) {
                drawer_main.closeDrawer(Gravity.RIGHT);
            } else {
                drawer_main.openDrawer(Gravity.RIGHT);
            }
        }

        //深度搜索
        if (v.getId() == R.id.rl_deep_search) {

            deepSearch(html);
            drawer_main.closeDrawer(Gravity.RIGHT);
        }

        //查看下载
        if (v.getId() == R.id.rl_main_check_picture) {

            Intent intent = new Intent(this,MainActivity.class);
            intent.putExtra("state", Constants.S_SDCARD);
            startActivity(intent);
        }

        //后退
        if (v.getId() == R.id.iv_main_back) {
            onBackPressed();
        }
        //停止搜索
        if (v.getId() == R.id.btn_stop) {
            stopGrab();
        }
    }

    //系统后退
    @Override
    public void onBackPressed() {
        //关闭抽屉
        if (drawer_main.isDrawerOpen(Gravity.RIGHT)) {
            drawer_main.closeDrawer(Gravity.RIGHT);
        }else if(inDeepSearch) {//停止搜索
            stopGrab();
        }else{
            if (hasSelected()) {
                Constants.selectedAll =true;
                doSelectAll();
                return;
            }
            super.onBackPressed();
        }


    }



    /**
     * 根据当前状态更新信息栏
     */
    private void update_infobar(String url) {
        if (Constants.state == Constants.S_WEB) {
            tv_info.setText("总共搜索到" + listPic.size() + "张图片!");
        }
        if (Constants.state == Constants.S_SDCARD) {
            tv_info.setText("在下载文件夹中共有" + listPic.size() + "张图片!");
        }
        updateIV_btn();
        Constants.selectedAll = false;
        iv_select.setImageResource(R.drawable.op_select_nothing_press);

    }


    /**
     * 获取文件中的所有图片的绝对路径
     *
     * @param dir
     * @return
     */
    private List<String> getDownloadImages(String dir) {
        List<String> res = new ArrayList<String>();
        File fdir = new File(dir);
        File[] files = fdir.listFiles();
        if (files != null) {
            // 遍历
            for (int i = 0; i < files.length; i++) {
                res.add(files[i].getAbsolutePath());// 绝对路径
            }
        }
        return res;
    }



    String currURL = "";
    String currquery = "";

    //获取图片
    private void getHttpImage(final String query) {
        currquery = query;
        currURL = query;
        //提交网址
        if (!query.startsWith("http")) {
            currURL = "http://" + query;
        }
        String[] arr =SPUtil.readArray(this,currURL);
        if(null != arr) {
            listPic = Arrays.asList(arr);
            rebuild();
            update_infobar("");
            return;
        }
        grabingDialog.show();
        pb_hor.setVisibility(View.VISIBLE);
        new HttpUtils().send(
                HttpRequest.HttpMethod.GET,
                currURL,
                new RequestCallBack<String>() {
                    @Override
                    public void onSuccess(ResponseInfo<String> stringResponseInfo) {
                        // 首页内容
                        html = stringResponseInfo.result;
                        //解析HTML页面
                        Document doc = Jsoup.parse(html);

                        //获取所有连接
                        Elements links = doc.select("a[href]");
                        Log.i("lx", "总数" + links.size());
                        List<String> useLinks = getUseableLinks(links);// 过滤
                        Log.i("lx", "过滤后" + useLinks.size());

                        // 获取图片
                        List<Element> imgs = doc.getElementsByTag("img");
                        listPic.clear();

                        mapImages.clear();// 清空

                        doParsePicture(imgs,links);
                        doMobaoUrl();
                            //UI
                        Constants.state = Constants.S_WEB;
                        update_infobar(query);

                       // grabingDialog.dismiss();
//                        pd.dismiss();
                    }

                    @Override
                    public void onFailure(HttpException e, String s) {

                    }
                });


    }


    private void doParsePicture(List<Element> imgs,Elements links){
        String prefix = currURL.substring(0,currURL.lastIndexOf("/")+1);
        for (Element img : imgs) {
            String src = img.attr("src");
            String absSrc =  img.attr("abs:src");
            if(!TextUtils.isEmpty(src) && !src.startsWith("//") && TextUtils.isEmpty(absSrc)){
                src = prefix + src;
            }
            if(src.startsWith("//")) {
                src = src.replaceAll("//", "http://");
            }
            // 保持顺序不变,同时去重
            String width = img.attr("width");
            if(src.endsWith(".gif")
                    || (!TextUtils.isEmpty(width) && (width.toLowerCase().contains("px")
                    || width.toLowerCase().matches("[\\d]+"))
                    &&
                    Integer.parseInt(width.replaceAll("[\\D]+", ""))<300)
            ) continue;
            if (!mapImages.containsKey(src)) {
                listPic.add(src);
                mapImages.put(src, src);// 去重src
            }
        }

        // 遍历哈希表
        for (Element link : links) {
            String href = link.attr("href");
            if (href.toLowerCase().contains("jpg")){
                String absHref =  link.attr("abs:href");
                if(!TextUtils.isEmpty(href) && TextUtils.isEmpty(absHref)){
                    href = prefix + href;
                }
                // 保持顺序不变,同时去重
                if (!mapImages.containsKey(href)) {
                    listPic.add(href);
                    mapImages.put(href, href);// 去重src
                }
            }
        }
    }


    int numTotalLinks = 0;// 总连接数
    int numCurrLinks = 0; // 抓取完的当前链接
    Map<String, String> mapImages = new HashMap<String, String>();


    // 在深度搜索中
    boolean inDeepSearch = false;
    // 停止抓取
    boolean stopGrab = false;

    /**
     * 深度搜索
     *
     * @param
     */
    private void deepSearch(String html) {

        inDeepSearch = true;
        stopGrab = false;


        Document doc = Jsoup.parse(html);// 解析HTML页面
        // 获取页面中的所有连接
        Elements links = doc.select("a[href]");
        Log.i("lx", "链接总数: " + links.size());
        List<String> useLinks = getUseableLinks(links);// 过滤
        Log.i("lx", "过滤后,链接总数: " + useLinks.size());

        numTotalLinks = useLinks.size();
        numCurrLinks = 0;

        pb_hor.setVisibility(View.VISIBLE);
        btn_stop.setVisibility(View.VISIBLE);
        pb_hor.setMax(numTotalLinks);
        pb_hor.setProgress(numCurrLinks);


        // 遍历哈希表
        for (final String href : useLinks) {

            // 新的抓取线程
            new HttpUtils().send(HttpRequest.HttpMethod.GET, href, new RequestCallBack<String>() {
                @Override
                public void onSuccess(ResponseInfo<String> objectResponseInfo) {
                    if (!stopGrab) {
                        // 拿到2级页面源码
                        String html = objectResponseInfo.result;
                        /***********************解析<img>图片标签**************************/
                        // 框架JSoup:GitHub
                        Document doc = Jsoup.parse(html);// 解析HTML页面
                        // 获取图片
                        List<Element> imgs = doc.getElementsByTag("img");
                        //获取所有连接
                        Elements links = doc.select("a[href]");
                        // 存入集合=哈希表
                        // 所有图片地址
                        doParsePicture(imgs,links);

                        /***********************解析<img>图片标签**************************/
                        update_final(href);
                    }


                }

                @Override
                public void onFailure(HttpException e, String s) {

                    if (!stopGrab) {
                        update_final(href);
                    }

                }
            });

        }

    }

    /**
     * 在最后一个链接抓取结束后,更新列表
     *
     * @param href
     */
    private void update_final(String href) {
        // 处理进度条
        numCurrLinks++;
        pb_hor.setProgress(numCurrLinks);
        tv_info.setText("总共搜索到" + listPic.size() + "张图片!");
        //tv_info.setText("深度搜索中("+numCurrLinks+"/"+numTotalLinks+")," + href );
        if (numCurrLinks >= numTotalLinks) {// 最后一名
            pb_hor.setProgress(numTotalLinks);
            pb_hor.setVisibility(View.GONE);
            btn_stop.setVisibility(View.GONE);
            grabingDialog.dismiss();
            update_infobar(currquery);
            inDeepSearch = false;
            rebuild();
        }
    }

    @Override
    public void handleSelected(int position) {
        boolean temp = adapter.getCheckList().get(position);//选中
        adapter.getCheckList().put(position, !temp);//取反赋值
        adapter.notifyDataSetChanged();//跟新
        //判斷当前选中个数:1选中/ 0个选中
        if (hasSelected()) {
            iv_btn.setVisibility(View.VISIBLE);
            iv_select.setVisibility(View.VISIBLE);
        } else {
            iv_btn.setVisibility(View.GONE);
            iv_select.setVisibility(View.GONE);
        }
    }

    /**
     * 判断是否有一个选中
     *
     * @return
     */
    private boolean hasSelected() {
        for (Boolean value : adapter.getCheckList().values()) {
            if (value) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回"true"的个数
     *
     * @return
     */
    private int getSelectedNumber() {
        int number = 0;
        for (Boolean value : adapter.getCheckList().values()) {
            if (value) {
                number++;
            }
        }
        return number;
    }

    private void  doSelectAll(){
        // 全选/全不选
        // 改变哈希表

        Constants.selectedAll = !Constants.selectedAll;
        adapter.setCheckList(Constants.selectedAll);
        adapter.notifyDataSetChanged();

        if (Constants.selectedAll) {
            iv_select.setImageResource(R.drawable.op_select_all_press);
        } else {
            iv_select.setImageResource(R.drawable.op_select_nothing_press);
        }

        // 判断当前选中的个数: 1个选中/ 0 个选中
        if (hasSelected()) {
            // 图片显示
            iv_btn.setVisibility(View.VISIBLE);
            iv_select.setVisibility(View.VISIBLE);
        } else {
            iv_btn.setVisibility(View.GONE);
            iv_select.setVisibility(View.GONE);
        }
    }
    // 点击"批量"图片按钮
    public void iv_btnClick(View v) {

        if (v.getId() == R.id.iv_select) {
            doSelectAll();
        }

        if (v.getId() == R.id.iv_btn) {

            if (Constants.state == Constants.S_WEB) {
                // 批量下载
                Constants.num_max = getSelectedNumber();
                Constants.num_curr = 0;
                grabingDialog.show();
                for (Integer position : adapter.getCheckList().keySet()) {
                    if (adapter.getCheckList().get(position)) {
                        // 当前图片需要下载
                        Constants.num_curr++;
                        Utils.downloadImage(listPic.get(position));// 子线程

                    }
                }
                if (Constants.num_curr >= Constants.num_max) {
//                    pd.dismiss();
                    grabingDialog.dismiss();
                    //去掉勾选
                    adapter.initCheckList();//初始化
                    adapter.notifyDataSetChanged();
                    iv_btn.setVisibility(View.GONE);
                    iv_select.setVisibility(View.GONE);
                }
            } else {
                // 批量删除
                for (Integer position : adapter.getCheckList().keySet()) {
                    if (adapter.getCheckList().get(position)) {
                        // 当前图片需要删除
                        String path = listPic.get(position);
                        File file = new File(path);
                        if (file.exists()) {
                            file.delete();// 删除
                        }
                    }
                }
                Utils.showToast("所有图片删除完毕!");
                // 列表更新
                listPic = getDownloadImages(Constants.SAVE_DIR);
                rebuild();
                Constants.state = Constants.S_SDCARD;// 更新状态
                update_infobar("");
                iv_btn.setVisibility(View.GONE);
                iv_select.setVisibility(View.GONE);

            }
        }

    }

    //看大图
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        //item点击事件
        Intent intent = new Intent(this, DragImageActivity.class);
        intent.putExtra(Constants.P_URL, listToArray());//图片地址
        intent.putExtra(Constants.P_POS, position);//图片位置
        intent.putExtra(Constants.P_INDEX, viewPager.getCurrentItem());
        intent.putExtra(Constants.P_PAGESIZE, mPageSize);
        intent.putExtra(Constants.P_TOTAL, listPic.size());
        startActivity(intent);
    }

    private String[] listToArray() {
        ImageAdapter adapter1 = new ImageAdapter(this, listPic, viewPager.getCurrentItem(), mPageSize);
        String[] arr = new String[adapter1.getCount()];
        for (int i = 0; i < adapter1.getCount(); i++) {
            arr[i] = listPic.get((int)adapter1.getItemId(i));
        }
        return arr;
    }

    /**
     * 过滤出有效链接
     *
     * @param links
     * @return
     */
    private List<String> getUseableLinks(Elements links) {
        Map<String, String> mapLinks = new HashMap<String, String>();
        List<String> lstLinks = new ArrayList<String>();

        String home = currURL;// 本站的域名

        //遍历所有links,过滤,保存有效链接
        for (Element link : links) {
            String href = link.attr("href");// abs:href, "http://"
            //Log.i("spl","过滤前,链接:"+href);
            // 设置过滤条件
            if (href.equals("")) {
                continue;// 跳过
            }
            if (href.equals(home)) {
                continue;// 跳过
            }
            if (href.startsWith("javascript")) {
                continue;// 跳过
            }

            if (href.startsWith("/")) {
                href = home + href;
            }
            if (!mapLinks.containsKey(href)) {
                mapLinks.put(href, href);// 将有效链接保存至哈希表中
                lstLinks.add(href);
            }

            Log.i("lx", "有效链接:" + href);
        }

        return lstLinks;
    }


    // 停止抓取
    private void stopGrab() {
        stopGrab = true;
        pb_hor.setVisibility(View.GONE);
        btn_stop.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
        update_infobar(currquery);
        inDeepSearch = false;
        Utils.showToast("深度抓取已经终止");
    }

}

