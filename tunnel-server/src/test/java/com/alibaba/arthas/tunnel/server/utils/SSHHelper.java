package com.alibaba.arthas.tunnel.server.utils;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.*;

/**
 * SSH工具类
 * @author 王建国
 * 2019-12-29
 */
public class SSHHelper {
 
    /**
     * 远程 执行命令并返回结果调用过程 是同步的（执行完才会返回）
     * @param host  主机名
     * @param user  用户名
     * @param psw   密码
     * @param port  端口
     * @param command   命令
     * @return
     */
    public static String exec(String host,String user,String psw,int port,String command){
        String result="";
        Session session =null;
        ChannelExec openChannel =null;
        try {
            JSch jsch=new JSch();
            session = jsch.getSession(user, host, port);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setPassword(psw);
            session.connect();
            openChannel = (ChannelExec) session.openChannel("exec");
            openChannel.setCommand(command);
            int exitStatus = openChannel.getExitStatus();
            System.out.println(exitStatus);
            openChannel.connect();  
            InputStream in = openChannel.getInputStream();  
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));  
            String buf = null;
            while ((buf = reader.readLine()) != null) {
                result+= new String(buf.getBytes("gbk"),"UTF-8")+"    <br>\r\n";  
            }  
        } catch (Exception e) {
            result+=e.getMessage();
        }finally{
            if(openChannel!=null&&!openChannel.isClosed()){
                openChannel.disconnect();
            }
            if(session!=null&&session.isConnected()){
                session.disconnect();
            }
        }
        return result;
    }
 
 
 
    public static void main(String args[]){
//        String exec = exec("127.0.0.1", "root", "root", 3306, "uname -a && date && uptime && who");
//        System.out.println(exec);

        StringBuffer cmdStr = new StringBuffer();
        cmdStr.append("#!/bin/bash\n"
//                     + "ssh -tt ").append("shijian@10.200.8.246").append(" << eeooff\n" +
//                        "if [ ! -f \"arthas-boot.jar\" ];then\n" +
//                        "  curl -O https://arthas.aliyun.com/arthas-boot.jar\n" +
//                        "fi\n" +
//                        "if [! -n \"sudo lsof -i tcp:8563\" ];then\n" +
//                        "  sudo java -jar /root/.arthas/lib/3.5.0/arthas/arthas-client.jar -c shutdown\n" +
//                        "fi\n").
//                append("ps -ef|grep ").append("push-server").append(".jar |grep -v grep | awk '{print \\$2}'|xargs -r sudo java -jar arthas-boot.jar --use-version 3.5.0  --tunnel-server 'ws://172.28.223.66:7777/ws' --attach-only\n").
        ).append("exit\n").append("eeooff\n").append("echo done!\n");

        File file = new File("/tmp/link.sh");
        try {
            file.delete();
            file.createNewFile();
            FileWriter fw = new FileWriter(file,true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(cmdStr.toString());
            bw.close();
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

//        String cmd = "sh /Users/klyg/Documents/arthas/linkM.sh";
//        String cmd = "sh /tmp/link.sh";
        String[] cmdarray = new String[] { "/bin/bash", "-c","echo $USER ","ls -a", "sh /tmp/link.sh"};

        BufferedReader br = null;
        try {
            Process p = Runtime.getRuntime().exec(cmdarray);
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            System.out.println("sb:" + sb.toString());

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
//            throw new Exception(ExceptionConstants.SYSTEM_EXCEPTION, "test 失败");
        }
    }
}

