
//B1分支的第二个修改
//C4分支上的修改2
//B2分支的第二个修改

package GBN;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class Server
{
    private byte mySerial;
    //服务器发送报文序号
    private byte serial;
    //服务器接收报文序号（双向传输）
    private final int mss=1496;
    //最大报文长度
    private final byte size=8;
    //窗口大小
    private byte base;
    //当前窗口的base
    private Map<Byte,DatagramPacket> sendBufs;
    //报文缓存
    private DatagramSocket socket;
    //监听端口
    private Map<Byte,Timer> timers;
    //各报文的定时器
    private Map<Byte,Boolean> timeUp;
    //标记报文超时

    public Server(int port) throws SocketException
    {
        this.socket=new DatagramSocket(port);
    }
    public void run()
    {
        while(true){
            byte[] buf=new byte[mss+4];
            DatagramPacket packet=new DatagramPacket(buf,mss+4);
            try
            {
                this.socket.receive(packet);
                switch (new String(buf, 0, packet.getLength()))
                {
                    case "-time":
                        Date date = new Date();
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd :hh:mm:ss");
                        byte[] time = dateFormat.format(date).getBytes(StandardCharsets.UTF_8);
                        DatagramPacket recall = new DatagramPacket(time, time.length, packet.getAddress(), packet.getPort());
                        this.socket.send(recall);
                        break;

                    case "-quit":
                        byte[] bye = "Good bye!".getBytes();
                        this.socket.send(new DatagramPacket(bye, bye.length, packet.getAddress(), packet.getPort()));
                        break;

                    case "-testgnb":
                        String filename = "src\\main\\resources\\GBN_ClientTest.txt";
                        sendFile(filename, packet.getAddress(), packet.getPort());
                        break;

                    default:
                        this.socket.send(packet);
                        break;
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
    private void initialize()
    {
        this.mySerial =0;
        this.serial=0;
        //定时器
        this.timers=new HashMap<>(size);
        //报文缓存
        this.sendBufs=new HashMap<>(size);
        //报文超时标记
        this.timeUp=new HashMap<>(8);
        for(int i=0;i<size;i++)
        {
            sendBufs.put((byte) i,new DatagramPacket(new byte[mss+4],mss+4));
        }
        base=0;
    }

    public void sendFile(String filename, InetAddress targetHost, int targetPort) throws SocketException, FileNotFoundException
    {
        initialize();

        //创建要发送的文件 sendFile
        File sendFile=new File(filename);

        //创建要接受的文件 recvFile
        String recvFilemame="src\\main\\resources\\GBN_ServerResult.txt";
        File recvFile=new File(recvFilemame);

        this.socket.connect(new InetSocketAddress(targetHost,targetPort));

        if(!sendFile.exists())
        {
            throw new FileNotFoundException(filename+" Not Found!");
        }

        boolean ack = true;

        try(FileInputStream reader=new FileInputStream(sendFile);
            FileOutputStream writer=new FileOutputStream(recvFile))
        {
            byte[] recvBuf=new byte[mss+4];

            //recv是客户端向服务器发来的数据
            DatagramPacket recv=new DatagramPacket(recvBuf,mss+4);

            this.socket.setSoTimeout(500);

            do {
                int myLength = 0;
                int length;
                if (ack && mySerial != -1)
                {
                    //在每次迭代中，将创建一个数据包并准备要发送的数据。
                    for(; mySerial <this.size; mySerial++)
                    {
                        //计算当前数据包的序号number = base + mySerial
                        byte number= (byte) (mySerial +base);
                        //从缓存报文中获得数据包对象 sendPacket
                        DatagramPacket sendPacket=this.sendBufs.get(number);
                        //获取要发送数据包的字节数组 sendBuf，以便在其中填充数据
                        byte[] sendBuf=sendPacket.getData();
                        // 使用文件输入流reader读取数据，并将其存储在数据包的字节数组中，从偏移量4开始。
                        // myLength是读取的数据长度
                        myLength = reader.read(sendBuf, 4, mss);

                        //服务器发给客户端的数据包中：
                        // [0]是服务器发给客户端的分组号，即number
                        // [1]是服务器接收到客户端发送的分组号
                        // [2][3]合在一起是长度
                        sendBuf[0] = number;
                        sendBuf[2] = (byte) (myLength & 0xFF);
                        sendBuf[3] = (byte) (myLength >> 8  & 0xFF);

                        //以发送数据包，并传递数据包和数据包序号作为参数
                        send(sendPacket,number);

                        if (myLength<mss)
                        {
                            break;
                        }
                    }

                    //报文长度小于mss 说明最后一组文件发送完毕 将mySerial设置为-1
                    if (myLength < mss)
                    {
                        mySerial = -1;
                        System.out.println(myLength+"更新序号为-1");
                    }
                }
                //文件发送完毕时，发送一个终止报文，序号设置为-1
                else if(this.sendBufs.isEmpty())
                {
                    byte[] sendBuf=new byte[mss+4];
                    //发送给客户端的分组中：
                    //[0]设置为-1
                    //[1]是服务器希望收到的客户端发送的分组号
                    DatagramPacket sendPacket=new DatagramPacket(sendBuf,mss+4);
                    sendBuf[0]=-1;
                    sendBuf[1]=serial;
                    System.out.print("向客户端发送文件全部完成！  ");
                    System.out.print("\33[36;1m期望收到的分组：" + (byte)(serial+(byte) 1) + "\33[0m");
                    System.out.println();
                    this.socket.send(sendPacket);
                }

                try
                {
                    //从套接字 this.socket 接收数据，并将接收到的数据存储在 recv 数据包中
                    this.socket.receive(recv);
                }
                catch (SocketTimeoutException exception)
                {
                    if(this.timeUp.containsValue(true))
                    {
                        resend();
                    }
                }
                //recv[1]是客户端接收到的服务器发送的分组号，即ACK
                byte num = recv.getData()[1];

                if (num == this.base)
                {
                    if(mySerial!=-1)
                    {
                        update(num);
                    }
                    else
                    {
                        delete(num);
                    }
                    this.timers.get(num).cancel();
                    this.timers.remove(num);
                    ack = true;
                }
                else if(num>=0)
                {
                    System.out.println(num+"是冗余分组,不做处理.base="+this.base);
                    if(this.timers.containsKey(num))
                    {
                        this.timers.get(num).cancel();
                        this.timers.remove(num);
                        Timer timer=new Timer(num +" packet timer");
                        timer.schedule(new timerTask(this.timeUp,num),1000);
                        this.timers.put(num,timer);
                    }
                    ack = false;
                }

                if(recv.getData()[0] == (byte)(serial+(byte) 1))
                {
                    System.out.println("\33[36;1m接收到客户端发送的分组" + (++serial) +"\33[0m");
                    length = recv.getData()[2]& 0xFF;
                    length |= recv.getData()[3] << 8;
                    writer.write(recvBuf,4,length);
                }
                else if(recv.getData()[0]==-1)
                {
                    serial=-1;
                    System.out.println("\33[36;1m服务器已经收到全部文件\33[0m");
                }
            } while (recvBuf[1] != -1 || serial!=-1);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        for(Map.Entry<Byte,Timer> entry:this.timers.entrySet()){
            entry.getValue().cancel();
        }
        System.out.println("传输文件结束");

        byte[] terminate=new byte[2];
        terminate[0]=(byte) -1;
        terminate[1]=(byte) -1;

        try
        {
            this.socket.send(new DatagramPacket(terminate,2));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        this.socket.disconnect();
        this.socket.setSoTimeout(0);
    }
    public void resend() throws IOException
    {
        for(Map.Entry<Byte,Timer> entry:this.timers.entrySet())
        {
            entry.getValue().cancel();
        }
        this.timers.clear();
        for(int i=0;i<this.sendBufs.size();i++)
        {
            send(this.sendBufs.get((byte)(i+base)), (byte) (i+base));
        }
    }
    private void send(DatagramPacket packet,byte number) throws IOException
    {
        packet.getData()[1]=serial;
        if(Math.random()>0.05)
        {
            this.socket.send(packet);
            System.out.print("向客户端发送分组："+ packet.getData()[0] +"    ");
            System.out.print("\33[36;1m期望收到的分组：" + (byte)(serial+1) + "\33[0m");
            System.out.println();
        }
        else
        {
            System.out.println("\33[31;1mLoss!!! 丢失发送分组"+number+"\33[0m");
        }

        Timer timer=new Timer(number +" packet timer");
        timer.schedule(new timerTask(this.timeUp,number),1000);
        this.timeUp.put(number,false);
        this.timers.put(number,timer);
    }
    public void update(byte num)
    {
        System.out.println("\33[33;1m接收到发送分组"+num+"的ACK\33[0m");
        this.timeUp.remove(num);
        this.base++;
        this.mySerial--;
        DatagramPacket tmp=this.sendBufs.get(num);
        sendBufs.remove(num);
        sendBufs.put((byte) (num+size),tmp);
    }

    public void delete(byte num)
    {
        this.timeUp.remove(num);
        this.base++;
        sendBufs.remove(num);
    }

    public static void main(String[] args) throws SocketException
    {
        Server server =new Server(7749);
        server.run();
    }
}


class timerTask extends TimerTask {
    private Map<Byte,Boolean> timeUp;
    private byte num;
    public timerTask(Map<Byte,Boolean> timeUp,byte num){
        this.timeUp=timeUp;
        this.num=num;
    }
    @Override
    public void run() {
        synchronized (this)
        {
            System.out.println("\33[31;1mTimeout!!! 分组" + num + "已超时\33[0m");
            this.timeUp.put(num, true);
        }
    }
}
