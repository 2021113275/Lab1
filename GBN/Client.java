
//B1分支的第一个修改
//C4分支上的修改
//B2分支的第一个修改

package GBN;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class Client {
    private final int mss=1496;
    private final DatagramSocket socket;
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
        String message="-testgnb";
        byte[] recvBuf=new byte[mss+4];
        byte[] sendBuf=new byte[mss+4];
        message.getBytes(0,message.length(),recvBuf,0);
        DatagramPacket packet=new DatagramPacket(recvBuf, message.length());

        String recFilename="src\\main\\resources\\GBN_ClientResult.txt";
        String sendFilename="src\\main\\resources\\GBN_ServerTest.txt";
        File recFile=new File(recFilename);
        File sendFile=new File(sendFilename);

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
        try(FileOutputStream writer=new FileOutputStream(recFile);
            FileInputStream reader=new FileInputStream(sendFile))
        {
            //建立连接
            this.socket.connect(new InetSocketAddress("127.0.0.1", 7749));

            DatagramPacket sendPacket=new DatagramPacket(sendBuf,mss+4);
            DatagramPacket recvPacket= new DatagramPacket(recvBuf, mss + 4);;

            byte serial = 0;
            byte myserial = 0;
            int length;
            int count=0;
            int myLength;

            this.socket.send(packet);
            do {
                //模拟延时收到，方法是延迟接收
                if(Math.random()<0.05)
                {
                    System.out.println("网络发生延迟");
                    Thread.sleep(500);
                }

                //recvPacket是客户端收到的 服务器发来的分组
                this.socket.receive(recvPacket);

                //recvPacket的0号位置 = 服务器发送的分组的序号
                //recvPacket的1号位置 = 服务器希望收到客户端发出的分组的序号
                if (serial != -1 && recvPacket.getData()[0] == serial)
                {
                    System.out.print("收到服务器发送的分组：" + serial + "   ");

                    System.out.print("\33[36;1m服务器期望收到的分组：" + (recvPacket.getData()[1]) + "\33[0m");
                    System.out.println();

                    //收到正确分组，更新序号，保存数据
                    serial++;
                    count++;

                    length = recvPacket.getData()[2] & 0xFF;
                    length |= recvPacket.getData()[3] << 8;

                    writer.write(recvBuf, 4, length);
                }
                //收到首字节是-1 第二字节不是-1 说明发给服务器的ACK已经发送完毕
                else if(recvPacket.getData()[0] == -1)
                {
                    serial =-1;
                    System.out.print("从服务器接收文件完成！   ");
                    System.out.print("\33[36;1m服务器期望收到的分组：" + recvPacket.getData()[1] +"\33[0m");
                    System.out.println();
                }
                else if(recvPacket.getData()[0] >= 0)
                {
                    System.out.println("收到冗余分组"+recvPacket.getData()[0]);
                }

                if(serial ==-1&&recvPacket.getData()[1]==-1)
                {
                    break;
                }


                //构建发送报文
                if(myserial != -1 && recvPacket.getData()[1] == myserial)
                {
                    myLength = reader.read(sendBuf,4,mss);

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
                sendBuf[0]= myserial;
                sendBuf[1]=recvPacket.getData()[0];

                //模拟返回包丢失，方法是不发送
                if(Math.random()>0.05)
                {
                    System.out.println("成功发送ACK"+serial+"和分组"+sendBuf[0]);
                    this.socket.send(sendPacket);
                }
                else
                {
                    System.out.println("\33[31;1m发给服务器的ACK"+sendBuf[1]+"丢失"+"\33[0m");
                }
            } while (true);

            System.out.println("共收到"+count+"个包");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    public static void main(String[] args) throws SocketException
    {
        Client client=new Client(7750);
        client.qTime();
        client.qQuit();
        client.qTest();
    }
}
