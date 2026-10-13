package com.brd;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class Storage {
    final Path root; final String scanner,clamHost; final int clamPort;
    record Incoming(String key,String name,String ext,String hash,long size){}
    Storage(@Value("${brd.storage}")String root,@Value("${brd.scanner}")String scanner,@Value("${brd.clam-host}")String host,@Value("${brd.clam-port}")int port)throws IOException{
        this.root=Path.of(root).toAbsolutePath().normalize();this.scanner=scanner;clamHost=host;clamPort=port;
        if(!Set.of("structural","clamav").contains(scanner))throw new IllegalArgumentException("Unknown scanner");
        Files.createDirectories(this.root.resolve("temp"));Files.createDirectories(this.root.resolve("objects"));
    }
    Path temp(String key){return safe("temp",key);} Path object(String key){return safe("objects",key);}
    Path safe(String dir,String key){if(!key.matches("[a-zA-Z0-9.-]+"))throw new IllegalArgumentException("Invalid object key");return root.resolve(dir).resolve(key);}
    static String hash(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
    Incoming receive(MultipartFile f)throws IOException{
        if(f.isEmpty()||f.getSize()>20L*1024*1024)throw new Problem(413,"FILE_TOO_LARGE","请选择 20 MB 以内的非空文件");
        if(Files.getFileStore(root).getUsableSpace()<100L*1024*1024)throw new Problem(503,"STORAGE_FULL","存储空间不足");
        String name=Optional.ofNullable(f.getOriginalFilename()).orElse("").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","");
        if(name.isBlank()||name.length()>200)throw new Problem(400,"INVALID_FILENAME","文件名过长或为空");
        String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        String mime=Optional.ofNullable(f.getContentType()).orElse("");
        if(!Set.of("pdf","docx").contains(ext)||!(mime.equals("application/octet-stream")||(ext.equals("pdf")&&mime.equals("application/pdf"))||(ext.equals("docx")&&mime.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))))throw new Problem(415,"UNSUPPORTED_FILE_TYPE","只支持 PDF 和 DOCX 文件");
        byte[] bytes=f.getBytes();String key=UUID.randomUUID()+"."+ext;Files.write(temp(key),bytes,StandardOpenOption.CREATE_NEW);return new Incoming(key,name,ext,hash(bytes),bytes.length);
    }
    void inspect(String key,String ext)throws Exception{
        Path file=temp(key);
        if(ext.equals("pdf")){
            byte[] bytes=Files.readAllBytes(file);String content=new String(bytes,StandardCharsets.ISO_8859_1);
            if(!content.startsWith("%PDF-")||!content.substring(Math.max(0,content.length()-1024)).contains("%%EOF")||content.matches("(?s).*/(JavaScript|JS|Launch|EmbeddedFile)\\b.*"))throw new IOException("Invalid or active PDF");
        } else {
            long total=0;int count=0;boolean document=false,types=false;Set<String> names=new HashSet<>();
            try(var zip=new ZipInputStream(Files.newInputStream(file))){ZipEntry e;while((e=zip.getNextEntry())!=null){
                String n=e.getName();if(++count>2000||!names.add(n)||n.startsWith("/")||n.contains("..")||n.contains("\\")||n.toLowerCase(Locale.ROOT).contains("vbaproject")||n.startsWith("word/embeddings/"))throw new IOException("Unsafe ZIP entry");
                var out=new ByteArrayOutputStream();byte[] b=new byte[8192];int len;while((len=zip.read(b))!=-1){total+=len;if(total>40L*1024*1024)throw new IOException("Expanded size limit");if(n.equals("word/document.xml")||n.equals("[Content_Types].xml"))out.write(b,0,len);}
                if(n.equals("word/document.xml")||n.equals("[Content_Types].xml")){
                    var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
                    var xml=factory.newDocumentBuilder().parse(new ByteArrayInputStream(out.toByteArray()));
                    if(n.equals("word/document.xml"))document=xml.getDocumentElement().getLocalName().equals("document")&&xml.getDocumentElement().getNamespaceURI().equals("http://schemas.openxmlformats.org/wordprocessingml/2006/main");
                    else types=out.toString(StandardCharsets.UTF_8).contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml");
                }
            }}
            if(!document||!types)throw new IOException("Not a DOCX document");
        }
        if(scanner.equals("clamav"))clamScan(file);
    }
    private void clamScan(Path file)throws IOException{
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress(clamHost,clamPort),3000);socket.setSoTimeout(30000);
            var out=new DataOutputStream(socket.getOutputStream());out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            try(var in=Files.newInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.writeInt(n);out.write(b,0,n);}}
            out.writeInt(0);out.flush();var response=new ByteArrayOutputStream();int c;while(response.size()<4096&&(c=socket.getInputStream().read())!=-1&&c!=0)response.write(c);
            if(!response.toString(StandardCharsets.UTF_8).trim().equals("stream: OK"))throw new IOException("File scan rejected");
        }
    }
    void promote(String key)throws IOException{Files.move(temp(key),object(key));}
    void discardTemp(String key){try{Files.deleteIfExists(temp(key));}catch(IOException ignored){}}
}
