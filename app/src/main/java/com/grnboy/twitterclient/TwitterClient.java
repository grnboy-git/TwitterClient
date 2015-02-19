package com.grnboy.twitterclient;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.OAuthAuthorization;
import twitter4j.auth.RequestToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationContext;



public class TwitterClient extends ActionBarActivity {

    // コンシューマーキーとコンシューマーシークレットの設定
    private final static String
        CONSUMER_KEY = "ggxR0IFnUs30MCGPqa2OfvDku",
        CONSUMER_SECRET = "KNGlHya8qJQzM3uUMlZW8WK8vdyfjTlR3A3SX0aC5OB0cLtQQ3",
        CALLBACK = "mycallback://SignTwitter";

    private final static int
        WC = LinearLayout.LayoutParams.WRAP_CONTENT,
        MP = LinearLayout.LayoutParams.MATCH_PARENT,
        MENU_SETUP = 0,
        MENU_UPDATE = 1;

    private final static String
        BR = System.getProperty("line.separator");

    // システム
    private static Handler handler = new Handler();
    private static float dpi;
    private static OAuthAuthorization oauth;
    private static RequestToken req;
    private static String token;
    private static String tokenSecret;

    // UI
    private ListView listView;
    private Map<String, Bitmap> icons = new HashMap<String, Bitmap>();
    private List<Status> statuses = new ArrayList<Status>();

    // アクティビティ起動時に呼ばれる
    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        // DPIの取得
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        dpi = metrics.density;

        // リストビューの生成
        listView = new ListView(this);
        listView.setLayoutParams(new LinearLayout.LayoutParams(MP, WC));
        listView.setAdapter(new ListAdapter());
        setContentView(listView);

        // トークンの読み込み
        SharedPreferences pref = getSharedPreferences("token", MODE_PRIVATE);
        token = pref.getString("token","");
        tokenSecret = pref.getString("tokenSecret", "");
    }

    // レジューム時に呼ばれる
    @Override
    protected void onResume() {
        super.onResume();

        // ブラウザのコールバックで起動したときの処理
        final Uri uri = getIntent().getData();
        if (uri != null && uri.toString().startsWith(CALLBACK) &&
            uri.getQueryParameter("oauth_verifier") != null) {
            Thread thread = new Thread(new Runnable() {
                public void run() {
                    try {
                        //Twitterのユーザ認証結果の取得
                        String verifier = uri.getQueryParameter("oauth_verifier");
                        AccessToken accToken = oauth.getOAuthAccessToken(req, verifier);
                        token = accToken.getToken();
                        tokenSecret = accToken.getTokenSecret();

                        // トークンの書き込み
                        SharedPreferences pref = getSharedPreferences("token", MODE_PRIVATE);
                        SharedPreferences.Editor editor = pref.edit();
                        editor.putString("token", token);
                        editor.putString("tokenSecret", tokenSecret);
                        editor.commit();

                        // ステータスの更新
                        handler.post(new Runnable() {
                            public void run() {
                                updateStatuses();
                            }
                        });
                    } catch (TwitterException e) {
                        toast(TwitterClient.this, e.getMessage());
                    }
                }
            });
            thread.start();
        } else {
            handler.post(new Runnable() {public void run() {
                updateStatuses();
            }});
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuItem item0 = menu.add(0, MENU_SETUP, 0, "設定");
        item0.setIcon(android.R.drawable.ic_menu_preferences);
        MenuItem item1 = menu.add(0, MENU_UPDATE, 0, "更新");
        item1.setIcon(android.R.drawable.ic_menu_upload);
        return true;
   }

    // メニューアイテム選択イベントの処理
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        // Twitterのユーザ認証
        if (id == MENU_SETUP) {
            startOAuth();
        }
        // ステータスの更新
        else if (id == MENU_UPDATE) {
            updateStatuses();
        }
        return true;
    }

    // Twitterのユーザ認証
    private void startOAuth() {
        Thread thread = new Thread(new Runnable() {public void run() {
            // Oauth認証凹部じぇくとの生成
            Configuration conf = ConfigurationContext.getInstance();
            oauth = new OAuthAuthorization(conf);
            oauth.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);

            //認証ＵＲＬを取得
            try {
                req = oauth.getOAuthRequestToken(CALLBACK);
            } catch (TwitterException e) {
                toast(TwitterClient.this, e.getMessage());
            }
            String uri = req.getAuthorizationURL();

            // ブラウザの起動
            startActivity(new Intent(Intent.ACTION_VIEW , Uri.parse(uri)));
            finish();
        }});
        thread.start();
    }

    // ステータスの更新
    private void updateStatuses() {
        // Twitterユーザ認証
        if (token.length() == 0) {
            startOAuth();
            return;
        }

        // 不定プログレスの表示
        setProgressBarIndeterminateVisibility(true);

        // ステータスの更新
        Thread thread = new Thread(new Runnable() {public void run() {
            try {
                // ステータスの読み込み
                Twitter twitter = new TwitterFactory().getInstance();
                twitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);
                twitter.setOAuthAccessToken(new AccessToken(token,tokenSecret));
                statuses = twitter.getHomeTimeline();
                icons.clear();

                handler.post(new Runnable() {public void run() {
                    //不定プログレスの非表示
                    setProgressBarIndeterminateVisibility(false);

                    //リストビューの更新
                    ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
                }});
            } catch (TwitterException e) {
                toast(TwitterClient.this, e.getMessage());
            }
        }});
        thread.start();
    }

    // トーストの表示
    public static void toast(final TwitterClient activity, final String text) {
        handler.post(new Runnable() {public void run() {
            // 不定プログレスの非表示
            activity.setProgressBarIndeterminateVisibility(false);

            // トーストの表示
            Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
        }});
    }

    // リストアアダプタの生成
    public class ListAdapter extends BaseAdapter {
        // 項目数の取得
        @Override
        public int getCount() {
            return statuses.size();
        }

        // 項目の取得
        @Override
        public Object getItem(int pos) {
            return statuses.get(pos);
        }

        // 項目IDの取得
        @Override
        public long getItemId(int pos) {
            return pos;
        }

        // ビューの取得
        @Override
        public View getView(int pos, View view, ViewGroup parent) {
            Context context = TwitterClient.this;
            Status status = statuses.get(pos);

            // レイアウト生成
            if (view == null) {
                int padding = (int)(6*dpi);
                LinearLayout layout = new LinearLayout(context);
                layout.setBackgroundColor(Color.WHITE);
                layout.setPadding(padding, padding, padding, padding);
                layout.setGravity(Gravity.TOP);
                view = layout;

                // アイコン
                int size = (int)(48*dpi);
                ImageView imageView = new ImageView(context);
                imageView.setTag("icon");
                imageView.setLayoutParams(new LinearLayout.LayoutParams(size, size));
                layout.addView(imageView);

                // テキストの指定
                TextView textView = new TextView(context);
                textView.setTextColor(Color.BLACK);
                textView.setTag("text");
                textView.setPadding(padding, 0, padding, 0);
                layout.addView(textView);
            }

            // ステータスの値の取得
            ImageView imageView = (ImageView)view.findViewWithTag("icon");
            readIcon(imageView,
                    status.getUser().getBiggerProfileImageURL().toString());
            TextView textView = (TextView) view.findViewWithTag("text");
            textView.setText("【"+status.getUser().getName()+"】"+BR+status.getText());
            return view;
        }
    }

    // アイコンの読み込み
    private void readIcon(final ImageView imageView, final String url) {
        // キャッシュ内アイコンの取得
        if (icons.containsKey(url)) {
            imageView.setImageBitmap(icons.get(url));
            return;
        }

        // ネット上アイコンの取得
        imageView.setImageBitmap(null);
        Thread thread = new Thread(new Runnable() {public void run() {
            try {
                InputStream input = (new URL(url)).openStream();
                final Bitmap icon = BitmapFactory.decodeStream(input);
                if (icon != null) {
                    icons.put(url, icon);
                    handler.post(new Runnable() {public void run() {
                        imageView.setImageBitmap(icon);
                    }});
                }
            } catch (Exception e) {
                toast(TwitterClient.this, e.getMessage());
            }
        }});
        thread.start();
    }
}
