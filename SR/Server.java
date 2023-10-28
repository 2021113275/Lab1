package SR;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class Server
{
    private byte mySerial;//服务器端发送的序列号
    private byte serial;//服务器端接收的序列号
    private final int mss=1496;//最大报文长度
    private final byte size=8;//窗口大小
    private byte base;//当前窗口的首位置标号
    private Map<Byte,DatagramPacket> sendBufs;//报文缓存
    private DatagramSocket socket;//监听套接字
    private Map<Byte,Timer> timers;//每个报文的定时器
    private Map<Byte,Boolean> timeUp;//报文超时标记
    private Set<Byte> notReceived;//当前窗口中还未收到ACK的分组
    private byte count;


    //这行代码创建一个DatagramSocket对象，用于在指定的端口上接收来自客户端的数据包。port参数指定了服务器端监听的端口号。
    public Server(int port) throws SocketException
    {
        this.socket=new DatagramSocket(port);
    }
    public void run()
    {
        while(true)
        {
            byte[] buf=new byte[mss+4];
            DatagramPacket packet = new DatagramPacket(buf,mss+4);
            try
            {
                //接收来自客户端的数据包，并将其存储在packet对象中
                this.socket.receive(packet);

                switch (new String(buf, 0, packet.getLength()))
                {
                    //如果数据包内容为"-time"，表示客户端请求当前时间信息。服务器端将获取当前时间，将其格式化为特定格式（"yyyy-MM-dd :hh:mm:ss"），
                    //然后将其转换为字节数组，并构建一个新的UDP数据包recall，将时间信息发送回客户端。
                    case "-time":
                        Date date = new Date();
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd :hh:mm:ss");
                        byte[] time = dateFormat.format(date).getBytes(StandardCharsets.UTF_8);
                        DatagramPacket recall = new DatagramPacket(time, time.length, packet.getAddress(), packet.getPort());
                        this.socket.send(recall);
                        break;

                    //如果数据包内容为"-quit"，表示客户端请求关闭连接。服务器端将构建一个包含"Good bye!"消息的字节数组bye，
                    //然后将其发送回客户端。
                    case "-quit":
                        byte[] bye = "Good bye!".getBytes();
                        this.socket.send(new DatagramPacket(bye, bye.length, packet.getAddress(), packet.getPort()));
                        break;

                    //如果数据包内容为"-testgnb"，表示客户端请求某种测试。服务器端可能会调用sendFile方法，从服务器端的文件发送数据到客户端。
                    case "-testgnb":
                        String filename = "src\\main\\resources\\SR_ClientTest.txt";
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
        this.count=0;
        //定时器
        this.timers=new HashMap<>(size);
        //报文缓存
        this.sendBufs=new HashMap<>(size);
        //报文超时标记
        this.timeUp=new HashMap<>(size);
        //未收到数据包的序列号
        this.notReceived =new HashSet<>(size);

        //初始化sendBufs
        for(int i=0;i<size;i++)
        {
            sendBufs.put((byte) i,new DatagramPacket(new byte[mss+4],mss+4));
        }
        //窗口的base = 0
        base=0;
    }

    public void sendFile(String filename, InetAddress targetHost, int targetPort) throws SocketException, FileNotFoundException
    {
        //初始化
        initialize();

        //创建要发送的文件 sendFile
        File sendFile=new File(filename);

        //创建要接受的文件 recvFile
        String recvFilemame="src\\main\\resources\\SR_ServerResult.txt";
        File recvFile=new File(recvFilemame);

        //将服务器的网络套接字连接到指定的目标主机和端口
        this.socket.connect(new InetSocketAddress(targetHost,targetPort));

        if(!sendFile.exists())
        {
            throw new FileNotFoundException(filename+" Not Found!");
        }

        boolean ack=true;

        //创建了一个文件输入流reader用于读取要发送的文件 sendFile，以及一个文件输出流writer用于将接收的数据写入recvFile
        try(FileInputStream reader=new FileInputStream(sendFile);
            FileOutputStream writer=new FileOutputStream(recvFile))
        {
            byte[] recvBuf=new byte[mss+4];
            //recv是客户端向服务器发来的数据
            DatagramPacket recv = new DatagramPacket(recvBuf,mss+4);

            //超时时间
            this.socket.setSoTimeout(700);

            do
            {
                int myLength = 0;
                int length;

                //ack=true 并且服务器端要发送的序列号不为-1
                //一次性发送一整个窗口，递增序号并准备数据包，然后将其发送出去。
                //number 是服务器要发送给客户端的分组号
                if (ack && mySerial!=-1)
                {
                    //在每次迭代中，将创建一个数据包并准备要发送的数据。
                    for(; mySerial <this.size; mySerial++)
                    {
                        //计算当前数据包的序号number = base + mySerial
                        byte number= (byte) (mySerial + base);

                        //从缓存报文中获得数据包对象 sendPacket
                        DatagramPacket sendPacket=this.sendBufs.get(number);

                        //获取要发送数据包的字节数组 sendBuf，以便在其中填充数据
                        byte[] sendBuf=sendPacket.getData();

                        // 使用文件输入流reader读取数据，并将其存储在数据包的字节数组中，从偏移量4开始。
                        // myLength是读取的数据长度
                        myLength = reader.read(sendBuf, 4, mss);

                        //发送包的数量+1
                        count++;

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
                    if (myLength<mss)
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
                    //超时重传
                    if(this.timeUp.containsValue(true))
                    {
                        resend();
                    }
                }

                //recv[1]是客户端接收到的服务器发送的分组号，即ACK
                byte num = recv.getData()[1];
                if(num != -1)
                {
                    System.out.println("\33[33;1m接收到发送分组"+num+"的ACK\33[0m");
                }

                //未接收的分组中包含 num，num即客户端发回来的ACK号
                if(notReceived.contains(num))
                {
                    System.out.println("窗口中未收到ACK的分组:"+notReceived);

                    //更新序号为 num 的分组信息
                    if(mySerial != -1)
                    {
                        update(num);
                    }
                    //若mySterial=-1说明分组已经发送完毕
                    else
                        delete(num);

                    //接收到的ACK正好是base分组的，则滑动窗口
                    if (num == base)
                    {
                        ack = true;
                        updateBase();
                        System.out.println("分组"+num+"成功接收. base滑动至："+base);
                    }
                    //接收到的ACK是窗口中其他分组的
                    else if(num > base && num < base+size)
                    {
                        System.out.println("分组"+num+"成功接收.base不变："+base);
                        ack = false;
                    }
                }
                //接收到的分组不在窗口中，即冗余分组
                else if(num >= 0)
                {
                    System.out.println(num+"是冗余分组,不做处理.base不变："+this.base);
                    ack = false;
                }

                //recv: [0]是客户端发给服务器的分组的分组号  [2][3]合起来是数据的长度
                if(recv.getData()[0] == (byte)( serial + (byte) 1))
                {
                    System.out.println("\33[36;1m接收到客户端发送的分组" + (++serial) +"\33[0m");
                    length = recv.getData()[2] & 0xFF;
                    length |= recv.getData()[3] << 8;
                    writer.write(recvBuf,4,length);
                }
                //recv[0]=-1  说明客户端将数据全部发送到服务器了
                else if(recv.getData()[0] == -1)
                {
                    serial=-1;
                    System.out.println("\33[36;1m服务器已经收到全部文件\33[0m");
                }
            } while (recvBuf[1]!=-1||serial!=-1);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        for(Map.Entry<Byte,Timer> entry:this.timers.entrySet())
        {
            entry.getValue().cancel();
        }

        System.out.println("传输文件结束");

        byte[] terminate=new byte[2];
        //[0][1]都是-1  说明客户端和服务器之间的传输全部结束
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

    //更新base的值
    private void updateBase()
    {
        //窗口中的分组已经全部被接收
        if(notReceived.isEmpty())
        {
            //mySerial!=-1 base值向前移动一组序号的大小
            if(mySerial!=-1)
            {
                base += mySerial;
                mySerial = 0;
            }
            //mySerial=-1 说明发送给客户端的分组发送完毕
            else
            {
                base = count;
                this.sendBufs.clear();
            }
        }
        //窗口中的分组没有全部被接收
        else
        {
            //如果base报文的ACK已经收到
            while (!notReceived.contains(base))
            {
                //窗口前移1，相对距离mySerial-1
                this.base++;
                if (mySerial != -1)
                {
                    this.mySerial--;
                }
            }
        }
    }

    //超时分组重新发送
    public void resend() throws IOException
    {
        //遍历超时报文序号的集合
        for(Map.Entry<Byte,Boolean> entry:this.timeUp.entrySet())
        {
            //取消定时器，重新发送分组
            if(entry.getValue())
            {
                this.timers.get(entry.getKey()).cancel();
                this.timers.remove(entry.getKey());
                send(this.sendBufs.get(entry.getKey()),entry.getKey());
            }
        }
    }

    //模拟了数据包的发送和丢失，同时设置了定时器来跟踪数据包的超时情况。
    private void send(DatagramPacket packet,byte number) throws IOException
    {
        //发送分组的[1]是服务器希望从客户端收到的分组的序号，即客户端发送给服务器的分组序号
        packet.getData()[1] = serial;

        //随机发送
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

        //将分组号添加到 notReceived 集合中，表示该分组还没有被接收。
        this.notReceived.add(number);
        //开启定时器
        Timer timer=new Timer(number + " packet timer");
        //使用 timer 对象调度一个计时器任务，该任务将在1000毫秒后执行。
        timer.schedule(new timerTask(this.timeUp,number),1000);
        //尚未触发超时
        this.timeUp.put(number,false);
        //存放新的定时器
        this.timers.put(number,timer);
    }

    //接收到分组的确认后，更新相关的数据结构和状态，以便继续发送下一个分组
    public void update(byte num)
    {
        //从 notReceived 集合中移除分组号 num，表示该分组已经接收
        notReceived.remove(num);
        //从 timeUp 集合中移除与分组号 num 相关的超时状态信息
        timeUp.remove(num);
        //取消与分组号 num 相关的定时器任务
        timers.get(num).cancel();
        timers.remove(num);
        //从 sendBufs 集合中获取与分组号 num 相关的数据包
        DatagramPacket tmp=this.sendBufs.get(num);
        //从 sendBufs 集合中移除与分组号 num 相关的数据包。
        sendBufs.remove(num);
        //放入一个新的报文 num+size ，因为num已经被接收，因此递补进来一个？
        sendBufs.put((byte) (num+size),tmp);
    }


    public void delete(byte num)
    {
        notReceived.remove(num);
        timeUp.remove(num);
        timers.get(num).cancel();
        timers.remove(num);
        sendBufs.remove(num);
    }

    public static void main(String[] args) throws SocketException
    {
        Server server =new Server(7749);
        server.run();
    }
}


class timerTask extends TimerTask
{
    private Map<Byte,Boolean> timeUp; //记录超时分组
    private byte num;
    public timerTask(Map<Byte,Boolean> timeUp,byte num)
    {
        this.timeUp=timeUp;
        this.num=num;
    }
    @Override
    public void run()
    {
        //确保只有一个线程可以执行程序
        synchronized (this)
        {
            System.out.println("\33[31;1mTimeout!!! 分组" + num + "已超时\33[0m");
            this.timeUp.put(num, true);
        }
    }
}
