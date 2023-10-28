package SW;

//import com.sun.xml.internal.ws.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class Client {
    //存储客户端的序列号
    private byte serial;

    //可以一次传输的最大数据包大小
    private final int mss=1496;

    //用于客户端与服务器端之间的数据通信
    private DatagramSocket socket;

    //序列号的上限值
    private final int size = 5;

    //客户端类的构造函数，用于初始化客户端对象。它接受一个整数参数 port，表示客户端将绑定的端口号
    public Client(int port) throws SocketException
    {
        this.socket=new DatagramSocket(port);
        this.serial=0;
    }

    public void qTime()
    {
        String message="-time";
        byte[] buf=new byte[mss+4];

        //将 message 转换为字节数组，并将其存储在 buf 数组中
        message.getBytes(0,message.length(),buf,0);

        //用于发送包含时间请求消息的数据包
        DatagramPacket packet=new DatagramPacket(buf, message.length());

        try
        {
            //将套接字与目标服务器的地址（"127.0.0.1"表示本地主机）和端口号（7759）连接
            this.socket.connect(new InetSocketAddress("127.0.0.1",7759));
            //发送数据包 packet 到指定的服务器地址和端口
            this.socket.send(packet);

            //接收服务器的响应数据
            DatagramPacket recv=new DatagramPacket(buf,mss+4);
            // 接收从服务器返回的数据包
            this.socket.receive(recv);

            //将接收到的数据包的内容以字节形式写入到标准输出
            System.out.write(buf,0,recv.getLength());
            System.out.println();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    //与QTime流程相似
    public void qQuit()
    {
        String message="-quit";
        byte[] buf=new byte[mss+4];

        message.getBytes(0,message.length(),buf,0);

        DatagramPacket packet=new DatagramPacket(buf, message.length());
        try
        {
            this.socket.connect(new InetSocketAddress("127.0.0.1",7759));
            this.socket.send(packet);
            DatagramPacket recv=new DatagramPacket(buf,mss+4);
            this.socket.receive(recv);
            System.out.write(buf,0,recv.getLength());
            System.out.println();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void qTest()
    {
        String message="-testsw";
        byte[] buf=new byte[mss+4];

        message.getBytes(0,message.length(),buf,0);

        DatagramPacket packet=new DatagramPacket(buf, message.length());

        //创建接收文件 swResult.txt
        String recFilename="src\\main\\resources\\swResult.txt";
        File recFile=new File(recFilename);
        if(!recFile.exists())
        {
            try
            {
                recFile.createNewFile();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        //将服务器发来的文件写入到文件 recFile 中。
        try(FileOutputStream out=new FileOutputStream(recFile))
        {
            //指定要发送数据包的目标地址
            this.socket.connect(new InetSocketAddress("127.0.0.1", 7759));
            DatagramPacket send=new DatagramPacket(buf,mss+4);
            DatagramPacket recv= new DatagramPacket(buf, mss + 4);;

            int length = 0;
            int count=0;

            //发送数据包
            this.socket.send(packet);
            do {
                //模拟延时收到，方法是延迟接收
                if(Math.random()<0.1)
                {
                    Thread.sleep(1000);
                }
                //接收数据包，并提取数据包中的信息
                this.socket.receive(recv);
                buf[1] = recv.getData()[0];

                //通过比较序列号来判断是否接收到正确的数据包。
                if (recv.getData()[0] == this.serial)
                {
                    System.out.println("收到正确的分组"+count);
                    count++;
                    //收到正确分组，更新序号，保存数据
                    updateSerial();

                    //这两行代码用于从接收到的数据包中提取数据包的长度信息。数据包的长度通常被编码为两个字节，这里通过位运算将其合并为一个整数值。
                    length = recv.getData()[2] & 0xFF;
                    length |= recv.getData()[3] << 8;

                    //如果接收到了正确的数据包，将使用out输出流将数据包的内容写入文件
                    out.write(buf, 4, length);
                }
                else
                {
                    System.out.println("收到冗余分组。");
                }

                //模拟返回包丢失，方法是不发送
                if(Math.random()>0.1)
                {
                    this.socket.send(packet);
                }
                else
                {
                    System.out.println("返回的包丢失");
                }
            } while (length == mss);
            System.out.println("共收到："+count+"个包");
        }
        catch (IOException | InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }
    private void updateSerial(){
        this.serial++;
        if(this.serial==this.size)
            this.serial=0;
    }
    public static void main(String[] args) throws SocketException
    {
        Client client=new Client(7760);
        client.qTime();
        client.qQuit();
        client.qTest();

    }
}
