package org.hotelmanager.app;

import android.content.Context;
import java.net.URL;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** Pins arrive only through the explicit connection-profile confirmation screen. */
final class TrustedConnection {
    static String origin(URL url) throws Exception {
        if (!"https".equals(url.getProtocol()) || url.getUserInfo()!=null || url.getRef()!=null) throw new Exception("HTTPS required");
        return "https://"+url.getHost().toLowerCase(java.util.Locale.ROOT)+":"+(url.getPort()<0?443:url.getPort());
    }
    static String pin(Context context, URL url) throws Exception {
        return context.getSharedPreferences("server_pins",0).getString(origin(url),"");
    }
    static void trust(Context context, URL url, String fingerprint) throws Exception {
        if(!fingerprint.matches("[a-fA-F0-9]{64}")) throw new Exception("Invalid certificate fingerprint");
        if(!context.getSharedPreferences("server_pins",0).edit().putString(origin(url),fingerprint.toLowerCase(java.util.Locale.ROOT)).commit()) throw new Exception("Cannot save server trust");
    }
    static HttpsURLConnection open(Context context, URL url) throws Exception {
        origin(url);
        HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(20000);connection.setReadTimeout(60000);
        String expected=pin(context,url);
        if(!expected.isEmpty()) {
            SSLContext ssl=SSLContext.getInstance("TLS");
            ssl.init(null,new TrustManager[]{new X509TrustManager(){
                public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
                public void checkClientTrusted(X509Certificate[] chain,String auth) throws CertificateException {throw new CertificateException("Client certificates not supported");}
                public void checkServerTrusted(X509Certificate[] chain,String auth) throws CertificateException {
                    try {
                        if(chain==null||chain.length==0) throw new CertificateException("Missing certificate");
                        chain[0].checkValidity();
                        byte[] digest=MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());
                        StringBuilder actual=new StringBuilder();for(byte b:digest)actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                        if(!MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),actual.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) throw new CertificateException("Server certificate changed");
                    } catch(CertificateException e){throw e;} catch(Exception e){throw new CertificateException(e);}
                }
            }},null);
            connection.setSSLSocketFactory(ssl.getSocketFactory());
            // Keep the platform hostname verifier: pinning does not bypass SAN/name checks.
        }
        return connection;
    }
}
