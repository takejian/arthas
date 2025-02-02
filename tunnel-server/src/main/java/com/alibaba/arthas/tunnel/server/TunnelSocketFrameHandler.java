
package com.alibaba.arthas.tunnel.server;

import com.alibaba.arthas.tunnel.common.MethodConstants;
import com.alibaba.arthas.tunnel.common.SimpleHttpResponse;
import com.alibaba.arthas.tunnel.common.URIConstans;
import com.alibaba.arthas.tunnel.server.utils.HttpUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete;
import io.netty.util.concurrent.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author hengyunabc 2019-08-27
 *
 */
public class TunnelSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final static Logger logger = LoggerFactory.getLogger(TunnelSocketFrameHandler.class);

    private TunnelServer tunnelServer;

    public TunnelSocketFrameHandler(TunnelServer tunnelServer) {
        this.tunnelServer = tunnelServer;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HandshakeComplete) {
            HandshakeComplete handshake = (HandshakeComplete) evt;
            // http request uri
            String uri = handshake.requestUri();
            logger.info("websocket handshake complete, uri: {}", uri);

            MultiValueMap<String, String> parameters = UriComponentsBuilder.fromUriString(uri).build().getQueryParams();
            String method = parameters.getFirst(URIConstans.METHOD);

            if (MethodConstants.CONNECT_ARTHAS.equals(method)) { // form browser
                connectArthas(ctx, parameters);
            } else if (MethodConstants.AGENT_REGISTER.equals(method)) { // form arthas agent, register
                agentRegister(ctx, handshake, uri);
                synchronized(tunnelServer){
                    logger.info("tunnelServer notifyAll:{}", new Date());
                    tunnelServer.notifyAll();
                }

            }
            if (MethodConstants.OPEN_TUNNEL.equals(method)) { // from arthas agent open tunnel
                String clientConnectionId = parameters.getFirst(URIConstans.CLIENT_CONNECTION_ID);
                openTunnel(ctx, clientConnectionId);
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        // 只有 arthas agent register建立的 channel 才可能有数据到这里
        if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            String text = textFrame.text();

            MultiValueMap<String, String> parameters = UriComponentsBuilder.fromUriString(text).build()
                    .getQueryParams();

            String method = parameters.getFirst(URIConstans.METHOD);

            /**
             * <pre>
             * 1. 之前http proxy请求已发送到 tunnel cleint，这里接收到 tunnel client的结果，并解析出SimpleHttpResponse
             * 2. 需要据 URIConstans.PROXY_REQUEST_ID 取出当时的 Promise，再设置SimpleHttpResponse进去
             * </pre>
             */
            if (MethodConstants.HTTP_PROXY.equals(method)) {
                String requestId = URLDecoder.decode(parameters.getFirst(URIConstans.PROXY_REQUEST_ID), "utf-8");

                if (requestId == null) {
                    logger.error("error, need {}, text: {}", URIConstans.PROXY_REQUEST_ID, text);
                    return;
                }
                logger.info("received http proxy response, requestId: {}", requestId);

                Promise<SimpleHttpResponse> promise = tunnelServer.findProxyRequestPromise(requestId);

                String data = URLDecoder.decode(parameters.getFirst(URIConstans.PROXY_RESPONSE_DATA), "utf-8");

                byte[] bytes = Base64.decodeBase64(data);

                SimpleHttpResponse simpleHttpResponse = SimpleHttpResponse.fromBytes(bytes);
                promise.setSuccess(simpleHttpResponse);
            }
        }
    }


    private String autoInstallArthas(String host, String appName){

        StringBuffer cmdStr = new StringBuffer();
        cmdStr.append("#!/bin/bash\n"
                + "scp -i /home/shijian/.ssh/id_rsa  /home/shijian/arthas.zip ").append("arthastest@").append(host).append(":~\n").
                append("ssh -tt -i /home/shijian/.ssh/id_rsa ").append("arthastest@").append(host).append(" << eeooff\n" +
//                + "ssh -t ").append("root@").append(host).append(" << eeooff\n" +
                "if [ ! -d \"arthas\" ];then\n" +
                "  unzip arthas.zip\n" +
                "fi\n").
                append("PID=\\$(ps -ef|grep ").append(appName).append(".jar |grep -v grep | awk '{print \\$2}')\n").
                append("PORT=\\$(sudo lsof -i tcp:8563|grep -v COMMAND | awk '{print \\$2}')\n").
                append("if [ \"\\$PID\" != \"\\$PORT\" ];then\n" +
                "  sudo java -jar arthas/arthas-client.jar -c shutdown\n").
                append("  sudo java -jar arthas/arthas-boot.jar  --tunnel-server 'ws://10.200.8.246:7777/ws' --app-name ").
                append(appName).append(" --attach-only \\$PID\n").
                append("fi\n").
//                append("  ps -ef|grep ").append(appName).append(".jar |grep -v grep | awk '{print \\$2}'|xargs -r sudo java -jar arthas-boot.jar --use-version 3.5.0  --tunnel-server 'ws://172.28.220.222:7777/ws' --attach-only\n").
                append("exit\n").append("eeooff\n").append("echo done!\n");
        //是否需要重复杀死进程  172.28.220.222
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
//        String cmd = "sh /tmp/link.sh";
        //不能执行多条命令吗？？ 还是只能执行有第一条有返回值后，后续不执行？ 不能执行多条命令
        String[] cmdarray = new String[] { "/bin/bash", "-c", "sh /tmp/link.sh"};
        BufferedReader br = null;
        try {
            Process p = Runtime.getRuntime().exec(cmdarray);
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
//            System.out.println("sb:" + sb.toString());
            logger.info("shell rsp:{}" ,sb.toString());
            return sb.toString();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
//            throw new Exception(ExceptionConstants.SYSTEM_EXCEPTION, "test 失败");
        }
        return null;
    }

    private void connectArthas(ChannelHandlerContext tunnelSocketCtx, MultiValueMap<String, String> parameters)
            throws URISyntaxException {

        List<String> appName = parameters.getOrDefault("appName", Collections.emptyList());
        List<String> agentId = parameters.getOrDefault("host", Collections.emptyList());


        if (agentId.isEmpty()) {
            logger.error("arthas agent id can not be null, parameters: {}", parameters);
            throw new IllegalArgumentException("arthas agent id can not be null");
        }

        logger.info("try to connect to arthas agent, id: " + agentId.get(0));

        Optional<AgentInfo> findAgent = tunnelServer.findAgent(agentId.get(0));

        if (!findAgent.isPresent() || (findAgent.isPresent() && !appName.get(0).equals(findAgent.get().getAppName()))) {
            // ssh目标服务器，然后启动arthas进程并绑定appName
            String rsp = autoInstallArthas(agentId.get(0), appName.get(0));
            if(rsp == null){
                logger.error("arthas autoinstall fail");
                throw new IllegalArgumentException("arthas autoinstall fail");
            }

            // 如果该方法能够在给定的时间段之内得到结果，那么将结果立刻返回，反之，超时返回默认结果。
            long timeout = 25000;
            long timeoutExpires = System.currentTimeMillis() + timeout;

            try{
                logger.info("Thread.sleep tunnelServer begin:{}", new Date());
                synchronized(tunnelServer){
                    tunnelServer.wait(timeout);
                }
                logger.info("Thread.sleep tunnelServer end:{}", new Date());
//            Thread.sleep(20000);
            }catch (Exception e){
                logger.error("Thread.sleep Error");
            }
            //todo 非同类，并发下会提前唤醒，登录到错误的session， 可以实现安装按钮。 或者sleep查询等待。
            if(System.currentTimeMillis() >= timeoutExpires) {
                // Get out of loop
                logger.error("MGTV DEBUG on based arthas install timeout, please first disconnect ,then reconnect !!");
                throw new IllegalArgumentException("MGTV DEBUG on based arthas install timeout, please first disconnect ,then reconnect !!");
            }else {
                findAgent = tunnelServer.findAgent(agentId.get(0));
            }
        }
        if (findAgent.isPresent()) {

            agentId = new ArrayList<String>();
            agentId.add(findAgent.get().getAgentId());

            ChannelHandlerContext agentCtx = findAgent.get().getChannelHandlerContext();

            String clientConnectionId = RandomStringUtils.random(20, true, true).toUpperCase();

            logger.info("random clientConnectionId: " + clientConnectionId);
            // URI uri = new URI("response", null, "/",
            //        "method=" + MethodConstants.START_TUNNEL + "&id=" + agentId.get(0) + "&clientConnectionId=" + clientConnectionId, null);
            URI uri = UriComponentsBuilder.newInstance().scheme(URIConstans.RESPONSE).path("/")
                    .queryParam(URIConstans.METHOD, MethodConstants.START_TUNNEL).queryParam(URIConstans.ID, agentId)
                    .queryParam(URIConstans.CLIENT_CONNECTION_ID, clientConnectionId).build().toUri();

            logger.info("startTunnel response: " + uri);

            ClientConnectionInfo clientConnectionInfo = new ClientConnectionInfo();
            SocketAddress remoteAddress = tunnelSocketCtx.channel().remoteAddress();
            if (remoteAddress instanceof InetSocketAddress) {
                InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;
                clientConnectionInfo.setHost(inetSocketAddress.getHostString());
                clientConnectionInfo.setPort(inetSocketAddress.getPort());
            }
            clientConnectionInfo.setChannelHandlerContext(tunnelSocketCtx);

            // when the agent open tunnel success, will set result into the promise
            Promise<Channel> promise = GlobalEventExecutor.INSTANCE.newPromise();
            promise.addListener(new FutureListener<Channel>() {
                @Override
                public void operationComplete(final Future<Channel> future) throws Exception {
                    final Channel outboundChannel = future.getNow();
                    if (future.isSuccess()) {
                        tunnelSocketCtx.pipeline().remove(TunnelSocketFrameHandler.this);

                        // outboundChannel is form arthas agent
                        outboundChannel.pipeline().removeLast();

                        outboundChannel.pipeline().addLast(new RelayHandler(tunnelSocketCtx.channel()));
                        tunnelSocketCtx.pipeline().addLast(new RelayHandler(outboundChannel));
                    } else {
//                        logger.error("wait for agent connect error. agentId: {}, clientConnectionId: {}", agentId,
//                                clientConnectionId);
                        ChannelUtils.closeOnFlush(agentCtx.channel());
                    }
                }
            });

            clientConnectionInfo.setPromise(promise);
            this.tunnelServer.addClientConnectionInfo(clientConnectionId, clientConnectionInfo);
            tunnelSocketCtx.channel().closeFuture().addListener(new GenericFutureListener<Future<? super Void>>() {
                @Override
                public void operationComplete(Future<? super Void> future) throws Exception {
                    tunnelServer.removeClientConnectionInfo(clientConnectionId);
                }
            });

            agentCtx.channel().writeAndFlush(new TextWebSocketFrame(uri.toString()));

            logger.info("browser connect waitting for arthas agent open tunnel");
            boolean watiResult = promise.awaitUninterruptibly(20, TimeUnit.SECONDS);
            if (watiResult) {
                logger.info(
                        "browser connect wait for arthas agent open tunnel success, agentId: {}, clientConnectionId: {}",
                        agentId, clientConnectionId);
            } else {
                logger.error(
                        "browser connect wait for arthas agent open tunnel timeout, agentId: {}, clientConnectionId: {}",
                        agentId, clientConnectionId);
                tunnelSocketCtx.close();
            }
        } else {
            tunnelSocketCtx.channel().writeAndFlush(new CloseWebSocketFrame(2000, "Can not find arthas agent by id: "+ agentId));
            logger.error("Can not find arthas agent by id: {}", agentId);
            throw new IllegalArgumentException("Can not find arthas agent by id: " + agentId);
        }
    }

    private void agentRegister(ChannelHandlerContext ctx, HandshakeComplete handshake, String requestUri) throws URISyntaxException {
        QueryStringDecoder queryDecoder = new QueryStringDecoder(requestUri);
        Map<String, List<String>> parameters = queryDecoder.parameters();

        String appName = null;
        List<String> appNameList = parameters.get(URIConstans.APP_NAME);
        if (appNameList != null && !appNameList.isEmpty()) {
            appName = appNameList.get(0);
        }

        // generate a random agent id
        String id = null;
        if (appName != null) {
            // 如果有传 app name，则生成带 app name前缀的id，方便管理
            id = appName + "_" + RandomStringUtils.random(20, true, true).toUpperCase();
        } else {
            id = RandomStringUtils.random(20, true, true).toUpperCase();
        }
        // agent传过来，则优先用 agent的
        List<String> idList = parameters.get(URIConstans.ID);
        if (idList != null && !idList.isEmpty()) {
            id = idList.get(0);
        }

        String arthasVersion = null;
        List<String> arthasVersionList = parameters.get(URIConstans.ARTHAS_VERSION);
        if (arthasVersionList != null && !arthasVersionList.isEmpty()) {
            arthasVersion = arthasVersionList.get(0);
        }

        final String finalId = id;

        // URI responseUri = new URI("response", null, "/", "method=" + MethodConstants.AGENT_REGISTER + "&id=" + id, null);
        URI responseUri = UriComponentsBuilder.newInstance().scheme(URIConstans.RESPONSE).path("/")
                .queryParam(URIConstans.METHOD, MethodConstants.AGENT_REGISTER).queryParam(URIConstans.ID, id).build()
                .encode().toUri();

        AgentInfo info = new AgentInfo();

        // 前面可能有nginx代理
        HttpHeaders headers = handshake.requestHeaders();
        String host = HttpUtils.findClientIP(headers);

        if (host == null) {
            SocketAddress remoteAddress = ctx.channel().remoteAddress();
            if (remoteAddress instanceof InetSocketAddress) {
                InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;
                info.setHost(inetSocketAddress.getHostString());
                info.setPort(inetSocketAddress.getPort());
            }
        } else {
            info.setHost(host);
            Integer port = HttpUtils.findClientPort(headers);
            if (port != null) {
                info.setPort(port);
            }
        }

        info.setChannelHandlerContext(ctx);
        if (arthasVersion != null) {
            info.setArthasVersion(arthasVersion);
        }
        info.setAgentId(id);
        info.setAppName(appName);
//        tunnelServer.addAgent(id, info);
        tunnelServer.addAgent(info.getHost(), info);
        ctx.channel().closeFuture().addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                tunnelServer.removeAgent(finalId);
            }

        });

        ctx.channel().writeAndFlush(new TextWebSocketFrame(responseUri.toString()));
    }

    private void openTunnel(ChannelHandlerContext ctx, String clientConnectionId) {
        Optional<ClientConnectionInfo> infoOptional = this.tunnelServer.findClientConnection(clientConnectionId);

        if (infoOptional.isPresent()) {
            ClientConnectionInfo info = infoOptional.get();
            logger.info("openTunnel clientConnectionId:" + clientConnectionId);

            Promise<Channel> promise = info.getPromise();
            promise.setSuccess(ctx.channel());
        } else {
            logger.error("Can not find client connection by id: {}", clientConnectionId);
        }

    }
}
