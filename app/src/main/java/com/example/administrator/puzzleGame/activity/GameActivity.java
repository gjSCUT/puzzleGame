package com.example.administrator.puzzleGame.activity;


import android.app.Activity;
import android.app.ProgressDialog;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.Window;
import android.view.WindowManager;

import com.example.Client;
import com.example.ClientLAN;
import com.example.OnClientReadListener;
import com.example.OnServerReadListener;
import com.example.Server;
import com.example.ServerLAN;
import com.example.administrator.puzzleGame.R;
import com.example.administrator.puzzleGame.adapter.GameProgressAdapter;
import com.example.administrator.puzzleGame.base.BaseHandler;
import com.example.administrator.puzzleGame.constant.CmdConstant;
import com.example.administrator.puzzleGame.constant.GameConstant;
import com.example.administrator.puzzleGame.gameModel.Vector2f;
import com.example.administrator.puzzleGame.msgbean.GameModel;
import com.example.administrator.puzzleGame.msgbean.GameProcess;
import com.example.administrator.puzzleGame.msgbean.User;
import com.example.administrator.puzzleGame.util.DrawbalBuilderUtil;
import com.example.administrator.puzzleGame.util.LogUtil;
import com.example.administrator.puzzleGame.view.Game3DView;
import com.example.administrator.puzzleGame.view.GameSetDialog;
import com.example.nioFrame.NIOSocket;
import com.example.protocol.MSGProtocol;
import com.example.serialization.Serializer;
import com.example.serialization.SerializerFastJson;

import java.util.ArrayList;
import java.util.List;

public class GameActivity extends Activity implements
        BaseHandler.OnMessageListener,
        Game3DView.MsgSender {
    private Game3DView mGLSurfaceView;
    private RecyclerView mRecyclerView;
    private GameProgressAdapter mAdapter;
    private BaseHandler.UnleakHandler handler;
    private Serializer serializer;
    private Client client;
    private Server server;
    private ProgressDialog dialog;

    private int num, mode;
    private Vector2f[] quadPoints;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        //设置为全屏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // 设置为横屏模式
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //切换到主界面
        setContentView(R.layout.activity_game3d);

        serializer = SerializerFastJson.getInstance();
        handler = new BaseHandler.UnleakHandler(this);

        client = ClientLAN.getInstance();
        mode = this.getIntent().getIntExtra("mode", 1);
        num = this.getIntent().getIntExtra("num", 3);
        float[] floats = this.getIntent().getFloatArrayExtra("points");

        quadPoints = new Vector2f[4];
        for (int i = 0; i < 4; i++) {
            quadPoints[i] = new Vector2f(floats[i * 2], floats[i * 2 + 1]);
        }

        if (this.getIntent().getBooleanExtra("isServer", false)) {
            server = ServerLAN.getInstance();
            List<User> users = client.getData("users");
            List<GameProcess> gameProcesses = new ArrayList<>();
            for (int i = 0; i < users.size(); i++) {
                gameProcesses.add(new GameProcess(0f));
            }
            server.putData("processes", gameProcesses);
        }

        initNet();
        initGameView();
        initProgressView();
        newGame();
    }

    private void initGameView() {
        //初始化GLSurfaceView
        mGLSurfaceView = (Game3DView) findViewById(R.id.game_3d_view);
        mGLSurfaceView.requestFocus();//获取焦点
        mGLSurfaceView.setFocusableInTouchMode(true);//设置为可触控

    }

    private void newGame() {
        //TODO 初始化游戏模式
        switch (mode) {
            case 1:
                mGLSurfaceView.init(num, Game3DView.ObjectType.CUBE, quadPoints, true, this);
                break;
            case 2:
                mGLSurfaceView.init(num, Game3DView.ObjectType.QUAD_PLANE, quadPoints, false, this);
                break;
            case 3:
                mGLSurfaceView.init(num, Game3DView.ObjectType.SPHERE, quadPoints, true, this);
                break;
        }
        //设置SurfaceView中hasLoad为false,使渲染管重新初始化object
        mGLSurfaceView.setLoad(false);
        //把玩家进度清零
        initProgressView();
    }

    private void initProgressView() {
        mRecyclerView = (RecyclerView) findViewById(R.id.list_game_progress_view);
        mRecyclerView.setHasFixedSize(true);
        List<GameProgressAdapter.ListData> listDatas = new ArrayList<>();

        //TODO 插入玩家数据
        List<User> users = client.getData("users");
        for (User user : users) {
            listDatas.add((new GameProgressAdapter.ListData(user.getName(), user.getName().substring(0, 1))));
        }
        mAdapter = new GameProgressAdapter(
                this,
                R.layout.listitem_game_progress,
                listDatas,
                DrawbalBuilderUtil.getDrawbalBuilder(DrawbalBuilderUtil.DRAWABLE_TYPE.SAMPLE_ROUND_RECT_BORDER)
        );

        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mRecyclerView.getContext()));
    }

    private void initNet() {
        final Serializer serializer = SerializerFastJson.getInstance();
        client.setClientReadListener(new OnClientReadListener() {
            @Override
            public void processMsg(byte[] packet) {
                String s = new String(packet);
                MSGProtocol msgProtocol = serializer.parse(s, MSGProtocol.class);
                Message message = new Message();
                int cmd = msgProtocol.getCommand();
                message.what = cmd;
                switch (cmd) {
                    case CmdConstant.PROGRESS:
                        List<GameProcess> gameProcess = (ArrayList<GameProcess>) msgProtocol.getAddObjects();
                        client.putData("processes", gameProcess);
                        break;
                    case CmdConstant.FINISH:
                        //设置游戏结束,判断是否自己赢了
                        boolean isWin;
                        User user = (User) msgProtocol.getAddObject();
                        if (GameConstant.PHONE.equals(user.getName())) {
                            isWin = true;
                        } else {
                            isWin = false;
                        }
                        message.getData().putBoolean("isWin", isWin);
                        break;
                    case CmdConstant.START:
                        //设置游戏模式，交给handle的Message去处理
                        GameModel gameModel = (GameModel) msgProtocol.getAddObject();
                        num = gameModel.getNum();
                        mode = gameModel.getGameModel();
                        float[] points = gameModel.getPoints();
                        quadPoints = new Vector2f[]{
                                new Vector2f(points[0], points[1]),
                                new Vector2f(points[2], points[3]),
                                new Vector2f(points[4], points[5]),
                                new Vector2f(points[6], points[7]),
                        };
                        break;
                }
                handler.sendMessage(message);
            }
        });
        if (server != null) {
            server.setServerReadListener(new OnServerReadListener() {
                @Override
                public void processMsg(byte[] packet, NIOSocket nioSocket) {
                    MSGProtocol msgProtocol = serializer.parse(new String(packet), MSGProtocol.class);
                    int cmd = msgProtocol.getCommand();
                    int pos = ((List) server.getData("clients")).indexOf(msgProtocol.getSenderName());
                    switch (cmd) {
                        case CmdConstant.PROGRESS:
                            GameProcess gameProcess = (GameProcess) msgProtocol.getAddObject();
                            List<GameProcess> gameProcesses = (List<GameProcess>) server.getData("processes");
                            gameProcesses.get(pos).setProgress(gameProcess.getProgress());
                            msgProtocol = new MSGProtocol(GameConstant.PHONE, CmdConstant.PROGRESS, gameProcesses);
                            break;
                        case CmdConstant.FINISH:
                            //客户端发送结束请求
                            GameProcess gameProcess1 = (GameProcess) msgProtocol.getAddObject();
                            if (gameProcess1.getProgress() == 1.0f) {
                                //发送获胜的玩家给全部人
                                User user = new User(msgProtocol.getSenderName());
                                msgProtocol = new MSGProtocol(GameConstant.PHONE, CmdConstant.FINISH, user);
                            } else {
                                LogUtil.d("GameActivity", "game finish error");
                            }
                            break;
                        case CmdConstant.START:
                            break;
                    }
                    String s = serializer.serialize(msgProtocol);
                    server.sendAllClient(s.getBytes());
                }
            });
        }

    }


    @Override
    protected void onResume() {
        super.onResume();
        mGLSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGLSurfaceView.onPause();
    }

    @Override
    public void processMessage(Message message) {
        switch (message.what) {
            case CmdConstant.PROGRESS:
                List<GameProcess> gameProcesses = client.getData("processes");
                for (int i = 0; i < gameProcesses.size(); i++) {
                    mAdapter.setGameProgress(i, gameProcesses.get(i).getProgress());
                }
                mAdapter.notifyDataSetChanged();
                break;
            case CmdConstant.START:
                //关闭ProgressDialog，初始化开启新游戏
                dialog.cancel();
                newGame();
                break;
            case CmdConstant.FINISH:
                //主线程调用UI设置成功
                if (server != null)
                    new SetTask().execute();

                if (message.getData().getBoolean("isWin")) {
                    //TODO 设置获胜dialog
                    dialog = ProgressDialog.show(this, "游戏结束！你赢了！", "正在等待房主设置游戏难度", false, true);
                    dialog.setCanceledOnTouchOutside(false);
                    System.out.println("TCP Game over,I win");
                } else {
                    //TODO 设置失败dialog
                    dialog = ProgressDialog.show(this, "游戏结束！你输了", "正在等待房主设置游戏难度", false, true);
                    dialog.setCanceledOnTouchOutside(false);
                    System.out.println("TCP Game over,I lose");
                }
                break;
        }
    }

    @Override
    public void sendMsgProtocol(MSGProtocol msgProtocol) {
        client.sendToServer(serializer.serialize(msgProtocol).getBytes());
    }

    class SetTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            //让房主先停止2秒查看胜利信息
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            GameSetDialog gameSetDialog = new GameSetDialog(GameActivity.this, new GameSetDialog.OnGameChangedListener() {
                @Override
                public void gameChanged(int mode, int num, Vector2f[] quadPoints) {
                    GameModel gameModel = new GameModel(mode, num, quadPoints);
                    MSGProtocol<GameModel> msgProtocol = new MSGProtocol<>(GameConstant.PHONE, CmdConstant.START, gameModel);
                    String s = SerializerFastJson.getInstance().serialize(msgProtocol);
                    client.sendToServer(s.getBytes());
                }
            }, 2);
            Window dialogWindow = gameSetDialog.getWindow();
            WindowManager.LayoutParams lp = dialogWindow.getAttributes();

            int width = (int) (GameConstant.WIDTH * 0.9);
            int height = (int) (GameConstant.HEIGHT * 0.8);
            lp.width = width; // 宽度
            lp.height = height; // 高度
            dialogWindow.setAttributes(lp);
            gameSetDialog.show();
        }
    }
}
