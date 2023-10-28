package SW;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

//停等协议：服务器端
public class Server {
    private byte serial;  // 用于标识分组序列号
    private final int mss = 1496;  // 最大分组大小
    private DatagramSocket socket;  // 套接字
    private int size = 5;  // 分组序列号的大小

    public Server(int port) throws SocketException
    {
        // 创建UDP套接字，并绑定到指定端口
        this.socket=new DatagramSocket(port);
        // 初始化分组序列号
        this.serial=0;
    }


    // 服务器运行方法
    public void run()
    {
        System.out.println("服务器，启动");
        while(true){

            //buf用于存储数据
            byte[] buf=new byte[mss+4];

            //数据包可以用于发送或接收UDP数据
            DatagramPacket packet=new DatagramPacket(buf,mss+4);

            try
            {
                // 使用socket对象接收从客户端发送过来的数据包，并将数据包的内容存储在packet对象中
                this.socket.receive(packet);

                switch (new String(buf, 0, packet.getLength()))
                {
                    case "-time":
                        // 获取当前时间，并以特定格式返回给客户端
                        Date date = new Date();
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd :hh:mm:ss");
                        byte[] time = dateFormat.format(date).getBytes(StandardCharsets.UTF_8);

                        //创建一个新的DatagramPacket对象recall，用于将时间数据包发送回客户端。
                        DatagramPacket recall = new DatagramPacket(time, time.length, packet.getAddress(), packet.getPort());
                        this.socket.send(recall);
                        break;

                    case "-quit":
                        // 发送 "Good bye!" 给客户端并退出
                        byte[] bye = "Good bye!".getBytes();
                        this.socket.send(new DatagramPacket(bye, bye.length, packet.getAddress(), packet.getPort()));
                        break;

                    case "-testsw":
                        // 向客户端发送名为 "swTest.txt" 的文件内容
                        String filename = "src\\main\\resources\\swTest.txt";
                        send(filename, packet.getAddress(), packet.getPort());
                        break;

                    default:
                        socket.send(packet);
                        break;
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    // 用于向客户端发送文件内容的方法
    public void send(String filename,InetAddress targetHost,int targetPort) throws SocketException, FileNotFoundException {

        //创建文件
        File file=new File(filename);

        //将套接字连接到指定的目标主机（`targetHost`）和端口（`targetPort`）。这意味着后续的数据传输将发送到这个目标主机和端口。
        this.socket.connect(new InetSocketAddress(targetHost,targetPort));

        //创建了两个字节数组 `sendBuf` 和 `recvBuf`，它们将用于存储将要发送和接收的数据
        byte[] sendBuf=new byte[mss+4];
        byte[] recvBuf=new byte[mss+4];

        //用于将数据放入发送和接收缓冲区中
        DatagramPacket send=new DatagramPacket(sendBuf,mss+4);
        DatagramPacket recv=new DatagramPacket(recvBuf,mss+4);

        // 设置套接字的超时时间为1秒
        this.socket.setSoTimeout(1000);

        if(!file.exists())
        {
            throw new FileNotFoundException(filename+" Not Found!");
        }

        // 声明并初始化一个名为 `ack` 的布尔变量。`ack` 用于跟踪数据传输中的确认状态，初始值为 `true`，表示初始情况下假设数据传输已经确认。
        // 在数据传输的过程中，`ack` 的值将根据接收到的确认信息进行更新，以确保数据包的可靠传输。
        // 如果 `ack` 为 `true`，则表示上一个数据包已被确认；
        // 如果 `ack` 为 `false`，则表示上一个数据包需要重新发送，因为它没有被确认。
        boolean ack=true;

        try(FileInputStream reader = new FileInputStream(file))
        {
            do {
                //记录当前要发送的数据包的长度
                int length = 0;
                //如果 ack 为 true，则表示上一个数据包已经被确认，可以继续发送下一个数据包。
                //如果 ack 为 false，则表示上一个数据包需要重新发送，因此不会继续发送下一个数据包。
                if (ack)
                {
                    System.out.println("已收到分组"+this.serial+"的ACK");
                    //将数据包的第一个字节 sendBuf[0] 设置为 this.serial，即当前数据包的序列号。
                    sendBuf[0] = this.serial;

                    //从文件读取数据并将其存储在 sendBuf 中，从偏移量 4 开始，并最多读取 mss 字节的数据。
                    length = reader.read(sendBuf, 4, mss);
                    if (length == -1)
                    {
                        break;
                    }

                    //length & 0xFF 是一种位运算操作，用于从整数length中提取最低8位（一个字节）的数值。
                    //作用是将length中的高位清零，保留最低8位的值。
                    sendBuf[2] = (byte) (length & 0xFF);

                    //将整数length中的第9到16位（从右往左数，从0开始）提取出来
                    sendBuf[3] = (byte) (length >> 8  & 0xFF);

                    //模拟发出后未收到，方法是不发送
                    if(Math.random()>0.1)
                    {
                        this.socket.send(send);
                    }

                }


                try
                {
                    //接收从目标主机发送回来的数据包
                    this.socket.receive(recv);

                    //确认接收到的数据包是预期的数据包
                    if (recv.getData()[1] == this.serial)
                    {
                        //用于更新 `this.serial`，以便它指向下一个要发送的数据包的序列号
                        updateSerial();

                        //接收到的数据包已经被确认
                        ack = true;
                    }
                    //确认接收到的数据包不是预期的数据包
                    else
                    {
                        System.out.println("检测冗余,不做反应。");
                        ack = false;
                    }
                }

                //套接字接收数据时发生了超时
                catch (SocketTimeoutException e)
                {
                    System.out.println("检测超时，重新发送分组。");
                    ack = false;
                    if(Math.random()>0.1)
                    {
                        this.socket.send(send);
                    }
                    else
                        System.out.println("发送的包丢失。");
                }

            } while (true);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        this.socket.setSoTimeout(0);
    }
    private void updateSerial()
    {
        //增加序列号，以便下一个数据包或消息可以具有不同的序列号。
        this.serial++;

        //如果当前序列号等于上限值，那么将序列号重置为0，以便在达到上限后重新从0开始。
        if(this.serial==this.size)
        {
            this.serial=0;
        }

    }
    public static void main(String[] args) throws SocketException
    {
        Server server =new Server(7759);
        server.run();
    }
}
