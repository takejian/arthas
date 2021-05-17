package com.alibaba.arthas.tunnel.server.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class ConnectionSSH {


    public static void main(String[] args) throws JSchException, IOException {
        JSch jsch = new JSch();
        String prvKey= "/Users/klyg/.ssh/id_rsa";
//        String prvKey= "/Users/klyg/.ssh/ssh_host_rsa_key";
        jsch.addIdentity(prvKey);

        String username = "shijian";
        String host = "10.200.8.246";
        Session session=jsch.getSession(username, host, 22);//为了连接做准备
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();
        String command = "ls";
        Channel channel=session.openChannel("shell");   

        channel.setInputStream(System.in);
        channel.setOutputStream(System.out);
        InputStream in=channel.getInputStream();

        channel.connect();

    }
}