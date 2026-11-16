package ch.so.agi.jenkins.delivery.upload;

import ch.so.agi.jenkins.delivery.client.*;
import ch.so.agi.jenkins.delivery.config.DeliveryPluginConfiguration;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;

/** One private directory per submission, created before parsing and removed after the run. */
public final class IncomingUploadStore {
    private static final Set<java.nio.file.attribute.PosixFilePermission> DIRECTORY=PosixFilePermissions.fromString("rwx------"),FILE=PosixFilePermissions.fromString("rw-------");
    private final Path root;
    public record Evidence(long size,String sha256,String originalName){}
    @FunctionalInterface public interface UploadStream {Evidence read(Path temporary,long limit)throws IOException;}
    public IncomingUploadStore(Path jenkinsHome){root=jenkinsHome.toAbsolutePath().normalize().resolve("data-infrastructure").resolve("uploads");}
    private static void directory(Path path)throws IOException{
        try{Files.createDirectory(path,PosixFilePermissions.asFileAttribute(DIRECTORY));}catch(FileAlreadyExistsException ignored){/* Verify below. */}
        if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS) || !Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS).equals(DIRECTORY))throw DeliveryProblem.invalid();
    }
    public Path existing(UUID id) throws IOException {
        Path delivery = root.resolve(id.toString());
        for (Path path : List.of(root.getParent(), root, delivery)) {
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).equals(DIRECTORY)) {
                throw DeliveryProblem.invalid();
            }
        }
        return delivery;
    }

    public Evidence receive(UUID id, long limit, UploadStream upload) throws IOException {
        if (limit < 1 || limit > DeliveryPluginConfiguration.HARD_UPLOAD) throw DeliveryProblem.invalid();
        directory(root.getParent());
        directory(root);
        var delivery = root.resolve(id.toString());
        try {
            Files.createDirectory(delivery, PosixFilePermissions.asFileAttribute(DIRECTORY));
        } catch (FileAlreadyExistsException error) {
            throw new DeliveryProblem(409, "INVALID_STATE");
        }
        var input = delivery.resolve("input.csv");
        boolean finished = false;
        try {
            Files.createFile(input, PosixFilePermissions.asFileAttribute(FILE));
            var evidence = upload.read(input, limit);
            finished = true;
            return evidence;
        } finally {
            if (!finished) {
                Files.deleteIfExists(input);
                Files.delete(delivery);
            }
        }
    }

    public void remove(UUID id) throws IOException {
        try (var paths = Files.walk(existing(id))) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    public static Evidence receiveBounded(InputStream stream,Path temporary,long limit,String name)throws IOException{
        validateName(name);var digest=sha();long count=0;boolean first=true;byte[] signature=new byte[4];int signatureLength=0;
        try(var out=FileChannel.open(temporary,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            byte[] buffer=new byte[65536];
            for(int n;(n=first?stream.readNBytes(buffer,0,4):stream.read(buffer))>0;){
                int textOffset=0;
                if(first){
                    first=false;
                    if(n>=3 && (buffer[0]&255)==239 && (buffer[1]&255)==187 && (buffer[2]&255)==191)textOffset=3;
                }
                if(n>limit-count)throw new DeliveryProblem(413,"INPUT_TOO_LARGE");
                for(int i=textOffset;i<n;i++){
                    byte b=buffer[i];if(b==0)throw DeliveryProblem.invalid();
                    if(signatureLength==0 && (b==' ' || (b>=9 && b<=13)))continue;
                    if(signatureLength<signature.length){
                        signature[signatureLength++]=b;
                        if((signatureLength==1 && b=='<') ||
                           (signatureLength==2 && (signature[0]&255)==31 && (b&255)==139) ||
                           (signatureLength==4 && signature[0]=='P' && signature[1]=='K' &&
                             ((signature[2]==3 && b==4)||(signature[2]==5 && b==6)||(signature[2]==7 && b==8))))
                            throw new DeliveryProblem(400,"INVALID_CSV_CONTENT");
                    }
                }
                digest.update(buffer,0,n);write(out,buffer,n);count+=n;
            }
            if(count==0)throw new DeliveryProblem(400,"EMPTY_CSV");out.force(true);
        }
        return new Evidence(count,HexFormat.of().formatHex(digest.digest()),name);
    }
    public static void validateName(String name){if(name==null || name.length()>255 || !name.matches("[^/\\\\\\p{Cntrl}]{1,251}\\.[cC][sS][vV]"))throw new DeliveryProblem(400,"INVALID_CSV_FILENAME");if (!java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(name)) throw DeliveryProblem.invalid();}
    private static MessageDigest sha(){try{return MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void write(FileChannel out,byte[] b,int n)throws IOException{var buffer=ByteBuffer.wrap(b,0,n);while(buffer.hasRemaining())out.write(buffer);}
}
