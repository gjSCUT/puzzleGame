package com.example;

import com.example.nioFrame.ConnectionAcceptor;
import com.example.nioFrame.NIOServerSocket;
import com.example.nioFrame.NIOService;
import com.example.nioFrame.NIOSocket;
import com.example.nioFrame.ServerSocketObserver;
import com.example.nioFrame.SocketObserver;
import com.example.nioFrame.UDPSocket;
import com.example.protocol.MSGProtocol;
import com.example.serialization.SerializerFastJson;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap; 
import java.util.Vector;


/**
 * TCP连接的服务端
 * 用户必须自定义OnMsgRecListener
 * Created by HUI on 2016-04-22.
 */
public class ServerLAN implements Runnable, Server, ServerSocketObserver {
    private final static String TAG = "mServer";
    private NIOService service;
    private NIOServerSocket serverSocket;
    private ServerSocketObserver serverSocketObserver;
    private UDPSocket udpSocket;
    private OnServerReadListener serverReadListener; //设置监听器的回调函数
    private Map<String, Object> serverDataMap;
    private List<NIOSocket> socketList;
    private boolean isRunning;
    private int port;


    private ServerLAN() {
        serverDataMap = new TreeMap<>();
        socketList = new Vector<>();
        serverSocketObserver = this;
        isRunning = false;
        udpSocket = UDPSocket.getInstance();
    }

    public static Server getInstance() {
        return SingletonHolder.server;
    }

    public static void main(String args[]) {
        final Server server = ServerLAN.getInstance();
        OnServerReadListener serverReadListener = new OnServerReadListener() {
            @Override
            public void processMsg(byte[] packet, NIOSocket nioSocket) {
                System.out.println(new String(packet));
                MSGProtocol msgProtocol = SerializerFastJson.getInstance().parse(new String(packet), MSGProtocol.class);
                msgProtocol = new MSGProtocol("hello client",msgProtocol.getCommand());
                String msgString = SerializerFastJson.getInstance().serialize(msgProtocol);
                server.sendToClient(msgString.getBytes(), nioSocket);
            }
        };
        server.setServerReadListener(serverReadListener)
                .bind(NetConstant.TCP_PORT)
                .start();

    }


    public Server setServerSocketObserver(ServerSocketObserver serverSocketObserver) {
        this.serverSocketObserver = serverSocketObserver;
        return getInstance();
    }


    public Server setServerReadListener(OnServerReadListener mlistener) {
        this.serverReadListener = mlistener;
        return getInstance();
    }


    public synchronized <T> void putData(String key, T o) {
        if (key.isEmpty())
            throw new NullPointerException();
        serverDataMap.put(key, o);
    }

    public synchronized <T> T getData(String key) {
        if (key.isEmpty())
            throw new NullPointerException();
        return (T) serverDataMap.get(key);
    }

    /**
     * 绑定线程
     */
    public Server bind(int port) {
        this.port = port;
        initServerObserver();
        return getInstance();
    }


    /**
     * 开启线程
     */
    public Server start() {
        isRunning = true;
        SingletonHolder.workThread.start();
        return getInstance();
    }

    /**
     * 停止线程
     */
    public Server stop() {
        isRunning = false;
        return getInstance();
    }

    @Override
    public Server initUdp() {
        udpSocket.setUdpReadListener(new UDPSocket.OnUdpReadListener() {
            @Override
            public void processMsg(String hostName, byte[] packet) {
                String s = new String(packet);
                if (s.equals(NetConstant.FIND_SERVER)) {
                    udpSocket.send(hostName, NetConstant.RETURN_HOST.getBytes());
                }
            }
        });
        return getInstance();
    }


    /**
     * 关闭线程
     */
    public Server close() {
        service.close();
        serverSocket.close();
        return getInstance();
    }

    /**
     * 关闭线程
     */
    public Server sendToClient(byte[] content, NIOSocket socket) {
        write(content, socket);
        return getInstance();
    }

    /**
     * 关闭线程
     */
    public Server sendExceptClient(byte[] content, NIOSocket socket) {
        for (NIOSocket nioSocket : socketList)
            if (nioSocket != socket)
                write(content, nioSocket);
        return getInstance();
    }

    /**
     * 关闭线程
     */
    public Server sendAllClient(byte[] content) {
        for (NIOSocket nioSocket : socketList)
            write(content, nioSocket);
        return getInstance();
    }


    /**
     * 需要序列化功能实现,建议在回调函数Listener中使用
     * 向指定的客户端管道写信息
     */
    private void write(byte[] content, NIOSocket socket) {
        if (socket == null || !isRunning) {
            System.out.print(TAG + "mSocket is null");
            stop();
            close();
            throw new NullPointerException();
        }
        //往通道写入数据
        socket.write(content);
    }


    /**
     * 初始化ServerSocketChannel，监听和回调的内容
     */
    private void initServerObserver() {
        try {
            service = new NIOService();
            serverSocket = service.openServerSocket(port);
            serverSocket.listen(serverSocketObserver);
            serverSocket.setConnectionAcceptor(ConnectionAcceptor.ALLOW);
        } catch (IOException e) {
            stop();
            close();
            System.out.print(TAG + "ServerSocket is wrong");
            e.getStackTrace();
        }
    }


    @Override
    public void run() {
        while (isRunning) {
            try {
                service.selectBlocking();
            } catch (IOException e) {
                this.stop();
                this.close();
                e.printStackTrace();
            }
        }
    }

    @Override
    public void acceptFailed(IOException exception) {
        //待定
    }

    @Override
    public void serverSocketDied(Exception exception) {
        //待定
    }

    @Override
    public void newConnection(NIOSocket nioSocket) {
        System.out.print(TAG + "Received connection: " + nioSocket);
        socketList.add(nioSocket);

        nioSocket.listen(new SocketObserver() {
            @Override
            public void connectionOpened(NIOSocket nioSocket) {
                //待定
            }

            @Override
            public void connectionBroken(NIOSocket nioSocket, Exception exception) {
                //待定
            }

            public void packetReceived(NIOSocket socket, byte[] packet) {
                //把其中一个客户端发送的信息，在服务端更新了之后，传递给其他每一个的客户端
                if (serverReadListener != null)
                    serverReadListener.processMsg(packet, socket);
                else
                    System.out.print(TAG + "Server listener is empty");
            }

            @Override
            public void packetSent(NIOSocket socket, Object tag) {
                //待定
            }
        });
    }

    /**
     * 单例模型
     */
    private static class SingletonHolder {
        private static ServerLAN server = new ServerLAN();
        private static Thread workThread = new Thread(server);
    }
}
