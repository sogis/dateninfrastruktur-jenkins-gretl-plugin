package ch.so.agi.jenkins.delivery.upload;

import ch.so.agi.jenkins.delivery.client.*;
import ch.so.agi.jenkins.delivery.config.DeliveryPluginConfiguration;
import hudson.FilePath;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;

/** A sealed input is never replaced. Per-submission lock spans parsing, fsync and publication of the file. */
public final class IncomingUploadStore {
    private static final Set<java.nio.file.attribute.PosixFilePermission> DIRECTORY=PosixFilePermissions.fromString("rwx------"),FILE=PosixFilePermissions.fromString("rw-------");
    private final Path root;
    public record Evidence(long size,String sha256,String originalName,String privateSpoolReference){}
    @FunctionalInterface public interface UploadStream {Evidence read(Path temporary,long limit)throws IOException;}
    public IncomingUploadStore(Path jenkinsHome){root=jenkinsHome.toAbsolutePath().normalize().resolve("data-infrastructure").resolve("uploads");}
    private static void directory(Path path)throws IOException{
        try{Files.createDirectory(path,PosixFilePermissions.asFileAttribute(DIRECTORY));}catch(FileAlreadyExistsException ignored){/* Verify below. */}
        if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS) || !Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS).equals(DIRECTORY))throw DeliveryProblem.invalid();
    }
    private Path directory(UUID id)throws IOException{
        directory(root.getParent());directory(root);var path=root.resolve(id.toString());directory(path);return path;
    }
    private Path existing(UUID id)throws IOException{
        for(Path p:List.of(root.getParent(),root,root.resolve(id.toString())))if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS) || !Files.getPosixFilePermissions(p,LinkOption.NOFOLLOW_LINKS).equals(DIRECTORY))throw DeliveryProblem.invalid();
        return root.resolve(id.toString());
    }
    public Evidence receive(UUID id,long limit,UploadStream upload)throws IOException{
        if(limit<1 || limit>DeliveryPluginConfiguration.HARD_UPLOAD)throw DeliveryProblem.invalid();
        var dir=directory(id);var input=dir.resolve("input.csv");var metadata=dir.resolve("seal.json");var lock=dir.resolve("upload.lock");
        try(var channel=FileChannel.open(lock,Set.of(StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS),PosixFilePermissions.asFileAttribute(FILE))){
            FileLock acquired;try{acquired=channel.tryLock();}catch(OverlappingFileLockException e){acquired=null;}
            if(acquired==null)throw new DeliveryProblem(409,"INVALID_STATE");
            try(FileLock exclusive=acquired){
                var temporary=Files.createTempFile(dir,".incoming-",".tmp",PosixFilePermissions.asFileAttribute(FILE));
                try{
                    var incoming=upload.read(temporary,limit);var ref="uploads/"+id+"/input.csv";var checked=new Evidence(incoming.size(),incoming.sha256(),incoming.originalName(),ref);
                    if(Files.exists(input,LinkOption.NOFOLLOW_LINKS) || Files.exists(metadata,LinkOption.NOFOLLOW_LINKS)){
                        // Recover a fully materialized, unsealed upload only with identical bytes/name.
                        if(!regular(metadata) || Files.size(metadata)>4096)throw new DeliveryProblem(409,"INPUT_MISMATCH");
                        var prior=Wire.JSON.readValue(Files.readAllBytes(metadata),Evidence.class);
                        if(!checked.equals(prior))throw new DeliveryProblem(409,"INPUT_MISMATCH");
                        if(Files.exists(input,LinkOption.NOFOLLOW_LINKS)){
                            if(!regular(input) || !matches(input,prior.size(),prior.sha256()))throw new DeliveryProblem(409,"INPUT_MISMATCH");
                        }else{
                            Files.move(temporary,input,StandardCopyOption.ATOMIC_MOVE);forceDirectory(dir);
                        }
                        return prior;
                    }
                    var sidecar=Files.createTempFile(dir,".seal-",".tmp",PosixFilePermissions.asFileAttribute(FILE));
                    try{
                        byte[] data=Wire.JSON.writeValueAsBytes(checked);
                        try(var out=FileChannel.open(sidecar,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){write(out,data,data.length);out.force(true);}
                        Files.move(sidecar,metadata,StandardCopyOption.ATOMIC_MOVE);forceDirectory(dir);
                        Files.move(temporary,input,StandardCopyOption.ATOMIC_MOVE);forceDirectory(dir);return checked;
                    }finally{Files.deleteIfExists(sidecar);}
                }finally{Files.deleteIfExists(temporary);}
            }
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
        return new Evidence(count,HexFormat.of().formatHex(digest.digest()),name,null);
    }
    public static void validateName(String name){if(name==null || name.length()>255 || !name.matches("[^/\\\\\\p{Cntrl}]{1,251}\\.[cC][sS][vV]"))throw new DeliveryProblem(400,"INVALID_CSV_FILENAME");Wire.unicode(name);}
    /** Bounded batch in canonical name order; no API call for untouched or unrecognized files. */
    public List<UUID> maintenanceBatch(String after)throws IOException{
        var parents=List.of(root.getParent(),root);
        for(Path parent:parents)if(!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS)||!Files.getPosixFilePermissions(parent,LinkOption.NOFOLLOW_LINKS).equals(DIRECTORY))return List.of();
        var next=new TreeSet<String>();
        try(var children=Files.newDirectoryStream(root)){
            for(var child:children){
                String name=child.getFileName().toString();
                if(name.compareTo(after)<=0 || !name.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}") || !Files.isDirectory(child,LinkOption.NOFOLLOW_LINKS) || !Files.getPosixFilePermissions(child,LinkOption.NOFOLLOW_LINKS).equals(DIRECTORY))continue;
                if(Files.getLastModifiedTime(child,LinkOption.NOFOLLOW_LINKS).toInstant().plusSeconds(86400).isAfter(java.time.Instant.now()))continue;
                try(var owned=Files.newDirectoryStream(child,IncomingUploadStore::spoolFile)){
                    if(!owned.iterator().hasNext())continue;
                }
                next.add(name);if(next.size()>8)next.pollLast();
            }
        }
        return next.stream().map(UUID::fromString).toList();
    }
    public void maintain(UUID id,java.util.function.BooleanSupplier removeAllowed)throws IOException{
        var dir=existing(id);var lock=dir.resolve("upload.lock");
        if(!regular(lock))return;
        try(var channel=FileChannel.open(lock,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            FileLock found;try{found=channel.tryLock();}catch(OverlappingFileLockException busy){return;}
            if(found==null)return;
            try(var acquired=found){
                if(!removeAllowed.getAsBoolean())return;
                try(var owned=Files.newDirectoryStream(dir,IncomingUploadStore::spoolFile)){
                    for(var file:owned)if(regular(file))Files.delete(file);
                }
                forceDirectory(dir);
            }
        }
    }
    private static boolean spoolFile(Path file){
        String name=file.getFileName().toString();
        return name.equals("input.csv") || name.equals("seal.json") || name.matches("\\.(?:incoming|seal)-[0-9]+\\.tmp");
    }
    public void copyToRun(UUID id,long size,String sha,String reference,FilePath destination)throws IOException,InterruptedException{
        if(!("uploads/"+id+"/input.csv").equals(reference) || size<1 || size>DeliveryPluginConfiguration.HARD_UPLOAD || !Wire.hash(sha))throw DeliveryProblem.invalid();
        var input=existing(id).resolve("input.csv");if(!regular(input))throw new DeliveryProblem(409,"INPUT_MISMATCH");
        // Use FilePath for the run node. Validate the bytes actually materialized, not just the source's size.
        try(var in=Files.newInputStream(input,LinkOption.NOFOLLOW_LINKS);var out=destination.write()){
            var digest=sha();long count=0;byte[] buffer=new byte[65536];
            for(int n;(n=in.read(buffer))>0;){if(n>size-count)throw new DeliveryProblem(409,"INPUT_MISMATCH");count+=n;digest.update(buffer,0,n);out.write(buffer,0,n);}
            if(count!=size || !sha.equals(HexFormat.of().formatHex(digest.digest())))throw new DeliveryProblem(409,"INPUT_MISMATCH");
        }catch(IOException | RuntimeException e){destination.delete();throw e;}
        destination.chmod(0600);
        var digest=sha();long actual=0;
        try(var in=destination.read()){byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))>0;){if(n>size-actual)throw new DeliveryProblem(409,"INPUT_MISMATCH");digest.update(buffer,0,n);actual+=n;}}
        if(actual!=size || !sha.equals(HexFormat.of().formatHex(digest.digest())))throw new DeliveryProblem(409,"INPUT_MISMATCH");
    }
    private static boolean regular(Path p)throws IOException{return Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) && Files.getPosixFilePermissions(p,LinkOption.NOFOLLOW_LINKS).equals(FILE);}
    private static boolean matches(Path p,long size,String expected)throws IOException{
        if(Files.size(p)!=size)return false;var digest=sha();long count=0;
        try(var stream=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){byte[] b=new byte[65536];for(int n;(n=stream.read(b))>0;){if(n>size-count)return false;digest.update(b,0,n);count+=n;}}
        return count==size && expected.equals(HexFormat.of().formatHex(digest.digest()));
    }
    private static MessageDigest sha(){try{return MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void write(FileChannel out,byte[] b,int n)throws IOException{var buffer=ByteBuffer.wrap(b,0,n);while(buffer.hasRemaining())out.write(buffer);}
    private static void forceDirectory(Path p)throws IOException{try(var channel=FileChannel.open(p,StandardOpenOption.READ)){channel.force(true);}}
}
