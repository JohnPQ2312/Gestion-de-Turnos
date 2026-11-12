package cr.ac.una.config;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.net.ssl.*;

// Transporte común: mantener esta implementación equivalente en los cuatro proyectos.
public final class NetworkSettings {
    private final Properties properties;
    private final Path directory;
    private NetworkSettings(Properties properties, Path directory) { this.properties=properties; this.directory=directory; }
    public static NetworkSettings load() throws IOException {
        Properties p=new Properties();
        String external=System.getProperty("turnos.config");
        Path directory=Path.of("").toAbsolutePath();
        if(external!=null) {
            Path path=Path.of(external).toAbsolutePath(); directory=path.getParent();
            try(var in=Files.newBufferedReader(path,StandardCharsets.UTF_8)){p.load(in);}
        } else {
            try(var in=NetworkSettings.class.getResourceAsStream("/network.properties")) {
                if(in!=null)p.load(new InputStreamReader(in,StandardCharsets.UTF_8));
            }
        }
        NetworkSettings settings=new NetworkSettings(p,directory);
        settings.port(); settings.tls(); settings.host(); settings.bind();
        return settings;
    }
    private String value(String key,String fallback) throws IOException {
        String value=properties.getProperty(key,fallback).trim();
        if(value.isBlank())throw new IOException("La propiedad de red "+key+" está vacía.");return value;
    }
    public String host() throws IOException { return value("server.host","127.0.0.1"); }
    public String bind() throws IOException { return value("server.bind","127.0.0.1"); }
    public int port() throws IOException {
        try { int n=Integer.parseInt(value("server.port","5000"));if(n>=1&&n<=65535)return n; }catch(NumberFormatException ignored){}
        throw new IOException("server.port debe ser un número entre 1 y 65535.");
    }
    public boolean tls() throws IOException {
        String value=value("tls.enabled","false");
        if(!Set.of("true","false").contains(value))throw new IOException("tls.enabled debe ser true o false.");return Boolean.parseBoolean(value);
    }
    private static void requireLoopback(String host) throws IOException {
        for(InetAddress address:InetAddress.getAllByName(host))if(!address.isLoopbackAddress())throw new IOException("Las conexiones fuera del equipo requieren tls.enabled=true.");
    }
    public static Socket open(String host,int port,int connectTimeout,int readTimeout) throws IOException {
        NetworkSettings settings=load();
        if(!settings.tls())requireLoopback(host);
        Socket socket=settings.tls()?settings.context(false).getSocketFactory().createSocket():new Socket();
        try {
            socket.connect(new InetSocketAddress(host,port),connectTimeout); socket.setSoTimeout(readTimeout);
            if(socket instanceof SSLSocket ssl) {
                SSLParameters parameters=ssl.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");
                ssl.setSSLParameters(parameters);ssl.startHandshake();
            } else if(!socket.getInetAddress().isLoopbackAddress())throw new IOException("El destino sin TLS debe ser local.");
            return socket;
        } catch(IOException|RuntimeException error) { try{socket.close();}catch(IOException close){error.addSuppressed(close);}throw error; }
    }
    public ServerSocket listen() throws IOException {
        String address=bind();if(!tls())requireLoopback(address);
        ServerSocket socket=tls()?context(true).getServerSocketFactory().createServerSocket():new ServerSocket();
        try {
            socket.bind(new InetSocketAddress(InetAddress.getByName(address),port()),50);
            return socket;
        }catch(IOException|RuntimeException error){try{socket.close();}catch(IOException close){error.addSuppressed(close);}throw error;}
    }
    private SSLContext context(boolean server) throws IOException {
        try {
            SSLContext context=SSLContext.getInstance("TLS");
            if(server) {
                KeyManagerFactory keys=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                char[] password=value("tls.keyStorePassword","").toCharArray();
                try { keys.init(store("tls.keyStore",password),password); }finally{Arrays.fill(password,'\0');}
                context.init(keys.getKeyManagers(),null,null);
            } else {
                TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                char[] password=value("tls.trustStorePassword","").toCharArray();
                try { trust.init(store("tls.trustStore",password)); }finally{Arrays.fill(password,'\0');}
                context.init(null,trust.getTrustManagers(),null);
            }
            return context;
        }catch(GeneralSecurityException error){throw new IOException("No se pudo preparar TLS. Revise almacén, contraseña y certificados.",error);}
    }
    private KeyStore store(String key,char[] password) throws IOException,GeneralSecurityException {
        KeyStore store=KeyStore.getInstance("PKCS12");
        try(InputStream in=Files.newInputStream(directory.resolve(value(key,"")))){store.load(in,password);}return store;
    }
}
