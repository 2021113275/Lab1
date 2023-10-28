//修改了第三个文件

package SR;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

public class Client
{
    private final int mss=1496;
    private final DatagramSocket socket;
    private byte base;//窗口号
    private final byte size=8;
    private Map<Byte,DatagramPacket> recvBufs;//缓存接收到的分组序号和对应的数据包

    public Client(int port) throws SocketException
    {
        this.socket=new DatagramSocket(port);
    }
    public void qTime()
    {
        String message="-time";
        byte[] buf=new byte[mss+4];
        message.getBytes(0,message.length(),buf,0);
        DatagramPacket packet=new DatagramPacket(buf, message.length());
        try
        {
            this.socket.connect(new InetSocketAddress("127.0.0.1",7749));
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
    public void qQuit()
    {
        String message="-quit";
        byte[] buf=new byte[mss+4];
        System.out.println(message);
        message.getBytes(0,message.length(),buf,0);
        DatagramPacket packet=new DatagramPacket(buf, message.length());
        try
        {
            this.socket.connect(new InetSocketAddress("127.0.0.1",7749));
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
        initialize();
        String message="-testgnb";
        String recFilename="src\\main\\resources\\SR_ClientResult.txt";
        String sendFilename="src\\main\\resources\\SR_ServerTest.txt";
        File recFile=new File(recFilename);
        File sendFile=new File(sendFilename);

        byte[] sendBuf=new byte[mss+4];
        message.getBytes(0,message.length(),sendBuf,0);
        DatagramPacket sendPacket=new DatagramPacket(sendBuf,message.length());
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
        //核心代码
        try(FileOutputStream writer=new FileOutputStream(recFile);
            FileInputStream reader=new FileInputStream(sendFile))
        {
            //建立连接
            this.socket.connect(new InetSocketAddress("127.0.0.1", 7749));

            //myserial = 服务器希望接收到的分组序号
            byte myserial = 0;
            int length;
            int count=0;
            int myLength;

            this.socket.send(sendPacket);
            do {
                //模拟延时收到，方法是延迟接收
                if(Math.random()<0.05)
                {
                    System.out.println("网络发生延迟");
                    Thread.sleep(500);
                }

                byte[] recvBuf=new byte[mss+4];

                //recvPacket是客户端收到的 服务器发来的分组
                DatagramPacket recvPacket= new DatagramPacket(recvBuf, mss + 4);;
                this.socket.receive(recvPacket);

                //recvPacket的0号位置 = 服务器发送的分组的序号
                byte received=recvPacket.getData()[0];

                //序号在 base 和 base + size 之间：可以正常接收，并返回ACK报文
                if (received >= base && received < base + size)
                {
                    System.out.print("收到服务器发送的分组：" + received + "   ");

                    //recvPacket的1号位置 = 服务器希望收到客户端发出的分组的序号
                    System.out.print("\33[36;1m服务器期望收到的分组：" + (byte)(recvPacket.getData()[1]+1) + "\33[0m");
                    System.out.println();

                    //将收到的 分组序号 和 数据包 缓存在recvBufs
                    recvBufs.put(received,recvPacket);

                    //数据包的序号等于base -> 滑动窗口
                    if(received == base)
                    {
                        //如果该分组未被接收 则读取分组内的数据后 将其从recvBufs中删除
                        while(recvBufs.containsKey(base))
                        {
                            DatagramPacket packet=recvBufs.get(base);
                            length = packet.getData()[2]& 0xFF;
                            length |= packet.getData()[3] << 8;
                            writer.write(packet.getData(), 4, length);
                            recvBufs.remove(received);
                            base++;
                            count++;
                        }
                    }
                }
                //收到首字节是-1 第二字节不是-1 说明发给服务器的ACK已经发送完毕
                //下面发送的数据包只含有客户端要传给服务器的分组
                else if(received==-1 && recvPacket.getData()[1]!=-1)
                {
                    System.out.print("从服务器接收文件完成！   ");
                    System.out.print("\33[36;1m服务器期望收到的分组：" + (byte)(recvPacket.getData()[1]+1) +"\33[0m");
                    System.out.println();
                }

                //如果分组序号小于base  说明这个分组之前就被接收了 只是ACK没有发回到服务器
                else if(received < base && received >= base - size)
                {
                    System.out.println("收到冗余分组" + recvPacket.getData()[0]);
                }

                //第一个和第二个字节都是-1 说明客户端全部接收到服务器端的发送
                if(received == -1 && recvPacket.getData()[1] == -1)
                {
                    break;
                }

                //构建发送报文
                sendPacket = new DatagramPacket(sendBuf,mss+4);

                //myserial = 服务器希望接收到的分组序号
                if(myserial != -1 && recvPacket.getData()[1] == myserial)
                {
                    //myLength = sendBuf中的数据长度
                    myLength = reader.read(sendBuf,4,mss);

                    //myLength=-1说明文件已经读完，客户端发给服务器的分组已经全部发送完毕
                    if(myLength == -1)
                    {
                        myserial = -1;
                        System.out.println("文件发送完毕");
                    }
                    else
                    {
                        myserial++;
                    }
                    sendBuf[2] = (byte) (myLength & 0xFF);
                    sendBuf[3] = (byte) (myLength >> 8  & 0xFF);
                }

                //客户端发给服务器的数据包中：
                // [0]是客户端发给服务器的分组的分组号（客户端已经全部发送，则该位置是-1）
                // [1]是客户端接收到的服务器发送的分组号，作为ACK（客户端已经全部接收，则该位置是-1）
                // [2][3]合在一起是数据的长度
                sendBuf[0] = myserial;
                sendBuf[1] = received;

                //采用随机数模拟返回包丢失
                if(Math.random()>0.05)
                {
                    System.out.println("成功发送ACK"+sendBuf[1]+"和分组"+sendBuf[0]);
                    this.socket.send(sendPacket);
                }
                else
                {
                    System.out.println("\33[31;1m发给服务器的ACK"+sendBuf[1]+"丢失"+"\33[0m");
                }
            } while (true);

            System.out.println("共收到"+count+"个包");

        }
        catch (IOException | InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    //初始化
    private void initialize()
    {
        this.base=0;
        recvBufs=new HashMap<>(8);
    }

    public static void main(String[] args) throws SocketException
    {
        Client client=new Client(7750);
        client.qTime();
        client.qQuit();
        client.qTest();
    }
}
