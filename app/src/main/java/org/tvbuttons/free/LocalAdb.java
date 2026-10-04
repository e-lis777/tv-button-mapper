package org.tvbuttons.free;

import android.content.Context;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import javax.crypto.Cipher;

/** Small loopback-only ADB client. No external hosts, root commands or downloaded code. */
final class LocalAdb implements Closeable {
 static final int CNXN=0x4e584e43,AUTH=0x48545541,OPEN=0x4e45504f,OKAY=0x59414b4f,WRTE=0x45545257,CLSE=0x45534c43;
 final Socket socket=new Socket(); DataInputStream in; OutputStream out; final Context context;
 int nextId=1; KeyPair key;
 LocalAdb(Context c){context=c;}
 void connect()throws Exception {
  socket.connect(new InetSocketAddress("127.0.0.1",5555),5000);socket.setSoTimeout(12000);
  in=new DataInputStream(socket.getInputStream());out=socket.getOutputStream();
  send(CNXN,0x01000000,4096,bytes("host::\0"));boolean signed=false;
  for(int count=0;count<6;count++){Packet p=receive();if(p.cmd==CNXN)return;
   if(p.cmd!=AUTH||p.a!=1)throw new IOException("Unexpected ADB handshake");
   if(key==null)key=loadKey();
   if(!signed){Cipher cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");cipher.init(Cipher.ENCRYPT_MODE,key.getPrivate());byte[] prefix={0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14};ByteArrayOutputStream token=new ByteArrayOutputStream();token.write(prefix);token.write(p.data);send(AUTH,2,0,cipher.doFinal(token.toByteArray()));signed=true;}
   else {send(AUTH,3,0,publicKey());socket.setSoTimeout(60000);AdbBridgeService.state="Разреши ADB-подключение на экране ТВ";}
  }throw new IOException("ADB authorization failed");
 }
 static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
 void send(int cmd,int a,int b,byte[] data)throws IOException {int sum=0;for(byte value:data)sum+=value&255;ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);h.putInt(cmd).putInt(a).putInt(b).putInt(data.length).putInt(sum).putInt(~cmd);out.write(h.array());out.write(data);out.flush();}
 Packet receive()throws IOException {byte[] h=new byte[24];in.readFully(h);ByteBuffer b=ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);int cmd=b.getInt(),a=b.getInt(),remote=b.getInt(),size=b.getInt(),sum=b.getInt(),magic=b.getInt();if(magic!=~cmd||size<0||size>1024*1024)throw new IOException("Invalid ADB packet");byte[] data=new byte[size];in.readFully(data);return new Packet(cmd,a,remote,data);}
 static class Packet {final int cmd,a,b;final byte[] data;Packet(int c,int x,int y,byte[] d){cmd=c;a=x;b=y;data=d;}}
 final class Stream implements Closeable {
  final int local;int remote;boolean ended;ByteArrayOutputStream pending=new ByteArrayOutputStream();
  Stream(String service)throws IOException {local=nextId++;send(OPEN,local,0,bytes(service+"\0"));Packet p;do{p=receive();}while(p.b!=local);if(p.cmd!=OKAY)throw new IOException("ADB service unavailable: "+service);remote=p.a;}
  void accept(Packet p)throws IOException {if(p.b!=local)throw new IOException("Wrong ADB stream");if(p.cmd==WRTE){pending.write(p.data);send(OKAY,local,remote,new byte[0]);}else if(p.cmd==CLSE){ended=true;send(CLSE,local,remote,new byte[0]);}else if(p.cmd!=OKAY)throw new IOException("Unexpected ADB stream packet");}
  void write(byte[] data)throws IOException {send(WRTE,local,remote,data);while(true){Packet p=receive();if(p.cmd==OKAY&&p.b==local)return;accept(p);if(ended)throw new EOFException("ADB stream closed");}}
  byte[] read(int wanted)throws IOException {while(pending.size()<wanted&&!ended)accept(receive());byte[] all=pending.toByteArray();if(all.length<wanted)throw new EOFException("Short ADB response");byte[] result=java.util.Arrays.copyOf(all,wanted);pending.reset();pending.write(all,wanted,all.length-wanted);return result;}
  String text()throws IOException {while(!ended){accept(receive());if(pending.size()>1024*1024)throw new IOException("ADB output too large");}return pending.toString("UTF-8");}
  public void close()throws IOException {if(!ended){send(CLSE,local,remote,new byte[0]);ended=true;}}
 }
 String shell(String command)throws IOException {try(Stream s=new Stream("shell:"+command)){return s.text();}}
 void unroot()throws IOException {try(Stream s=new Stream("unroot:")){s.text();}}
 void push(InputStream source,String path)throws IOException {try(Stream s=new Stream("sync:")){byte[] target=bytes(path+",33216");s.write(sync("SEND",target.length,target));byte[] buffer=new byte[2048];int n;while((n=source.read(buffer))!=-1)s.write(sync("DATA",n,java.util.Arrays.copyOf(buffer,n)));s.write(sync("DONE",(int)(System.currentTimeMillis()/1000),new byte[0]));byte[] response=s.read(8);ByteBuffer h=ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN);int code=h.getInt(),length=h.getInt();if(code!=0x59414b4f){String error=length>0&&length<65536?new String(s.read(length),StandardCharsets.UTF_8):"sync failed";throw new IOException(error);}}}
 static byte[] sync(String tag,int value,byte[] payload){ByteBuffer b=ByteBuffer.allocate(8+payload.length).order(ByteOrder.LITTLE_ENDIAN);b.put(bytes(tag)).putInt(value).put(payload);return b.array();}
 KeyPair loadKey()throws Exception {File privateFile=new File(context.getFilesDir(),"local-adb-private"),publicFile=new File(context.getFilesDir(),"local-adb-public");KeyFactory factory=KeyFactory.getInstance("RSA");if(privateFile.isFile()&&publicFile.isFile())return new KeyPair(factory.generatePublic(new X509EncodedKeySpec(read(publicFile))),factory.generatePrivate(new PKCS8EncodedKeySpec(read(privateFile))));KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair pair=generator.generateKeyPair();try(FileOutputStream f=new FileOutputStream(privateFile)){f.write(pair.getPrivate().getEncoded());}try(FileOutputStream f=new FileOutputStream(publicFile)){f.write(pair.getPublic().getEncoded());}return pair;}
 static byte[] read(File file)throws IOException {try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
 byte[] publicKey(){RSAPublicKey pub=(RSAPublicKey)key.getPublic();BigInteger n=pub.getModulus(),r=BigInteger.ONE.shiftLeft(32);ByteBuffer b=ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);b.putInt(64).putInt(r.subtract(n.modInverse(r)).intValue());b.put(little(n)).put(little(BigInteger.ONE.shiftLeft(4096).mod(n))).putInt(pub.getPublicExponent().intValue());return bytes(Base64.encodeToString(b.array(),Base64.NO_WRAP)+" tvbuttons@localhost\0");}
 static byte[] little(BigInteger value){byte[] big=value.toByteArray(),result=new byte[256];for(int i=0;i<256&&i<big.length;i++)result[i]=big[big.length-1-i];return result;}
 public void close()throws IOException {socket.close();}
}
