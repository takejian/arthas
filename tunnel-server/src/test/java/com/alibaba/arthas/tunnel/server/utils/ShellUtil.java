//package com.alibaba.arthas.tunnel.server.utils;
//
//
//import java.io.*;
//
//public class ShellUtil {
//    public  String run(String cmd) {
//        Runtime runtime = Runtime.getRuntime();
//        StringBuffer b=new StringBuffer();
//        BufferedReader br = null;
//        String sys = System.getProperties().getProperty("os.name").toUpperCase();
//        try {
//
//            cmd = cmd.trim();
// //获取当前项目的路径
//            String path = this.getClass().getResource("/").getPath();
//            int i = path.indexOf("crds-platform");
//            path = path.substring(1,i);
//            path = path.replace("/","\\\\");
//            path = path + "Report"+"\\\\data.txt";
//            File file = new File(path);
//            if(!file.exists()) {
//                file.createNewFile();
//            }
//            ProcessBuilder pb =  new ProcessBuilder();
//            if(sys.indexOf("WINDOWS") != -1) {
//                pb = new ProcessBuilder().command("cmd.exe", "/c", cmd).inheritIO();
//            } else {
//                pb = new ProcessBuilder().command("/bin/sh", "-c", cmd).inheritIO();
//            }
//
//            pb.redirectErrorStream(true);
////将执行结果写入file中
//            pb.redirectOutput(file);
//            //Process process = pb.start();
//            pb.start().waitFor();
////从file中读出执行结果
//            InputStream in = new FileInputStream(file);
////转码，防止中文乱码
//            br= new BufferedReader(new InputStreamReader(in,Charset.forName("GBK")));
//            String line=null;
//            while ((line=br.readLine())!=null) {
//                b.append(line+"\n");
//                //System.out.println(line);
//            }
//            file.delete();
//           /* Process p = Runtime.getRuntime().exec("cmd /c "+cmd);
//            br = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.forName("GBK")));
//            String line=null;
//            while ((line=br.readLine())!=null) {
//                b.append(line+"\n");
//            }*/
//        } catch (Exception e) {
//            e.printStackTrace();
//            return e.getMessage();
//        }
//        return b.toString();
//
//    }
//}
