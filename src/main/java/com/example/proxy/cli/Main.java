package com.example.proxy.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;

import com.example.proxy.config.ProxyConfig;
import com.example.proxy.core.server.ProxyServer;
import com.example.proxy.core.server.ServerInitializer;
import com.example.proxy.core.server.ServerInitializer.Notifier;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "proxy", mixinStandardHelpOptions = true, subcommands = {Main.StartCommand.class, Main.SendCommand.class})
public class Main implements Runnable {
    @Override
    public void run() {}

    @Command(name = "send", description = "Send data command")
    public static class SendCommand implements Runnable {
        @Option(names = "--dest", description = "Destination URLs", split = ",", required = true)
        List<String> destinations;

        @Option(names = "-d", description = "Data to send. String only support for now", required = false)
        String content;

        @Override
        public void run() {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8000));
            
            for (String dest : destinations) {
                try {
                    java.net.URL url = new java.net.URL(dest);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
                    conn.setRequestMethod("GET");
                    conn.setDoOutput(true);
                                        
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to send to " + dest + ": " + e.getMessage());
                }
            }
        }
    }

    @Command(name = "start", description = "Start the proxy server")
    public static class StartCommand implements Runnable {
        @Option(names = "--protocol", required = true)
        String protocol;

        @Option(names = "--host", defaultValue = "localhost")
        String host;

        @Option(names = "--port", defaultValue = "8000")
        int port;

        @Override
        public void run() {
            ProxyServer proxy = new ProxyServer(new ProxyConfig(protocol));
            try {
                proxy.initialize(new ServerInitializer(host, port, Notifier.NO));
                proxy.start();
                proxy.sync();
            } catch (Exception e) {
                System.err.println("Proxy server failed to start: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}