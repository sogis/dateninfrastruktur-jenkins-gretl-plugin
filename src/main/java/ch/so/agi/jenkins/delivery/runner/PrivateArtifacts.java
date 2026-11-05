package ch.so.agi.jenkins.delivery.runner;

import ch.so.agi.jenkins.delivery.client.*;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Writes to one fixed bucket and reads the actual bytes back, without passing credentials as arguments. */
final class PrivateArtifacts {
    static final long MAX=10737418240L;
    private static final String RCLONE="/usr/local/bin/di-rclone", BUCKET="di:ch.so.daten/";
    private static final String UUID="[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}";
    record Evidence(long size,String sha256){}
    private final Path run;
    PrivateArtifacts(Path run){this.run=run;}
    void downloadBase(String key,long bytes,String hash)throws Exception{
        if(!key.matches("downloads/"+UUID+"/data\\.csv")||bytes<1||bytes>1073741824L||!Wire.hash(hash))throw DeliveryProblem.invalid();
        Path path=run.resolve("base.csv");
        if(download(key,path,bytes+1)!=0)throw DeliveryProblem.unavailable();
        if(!new Evidence(bytes,hash).equals(evidence(path,bytes+1)))throw conflict();
    }
    Evidence store(String key,Path file)throws Exception{
        allow(key);if(!file.getParent().equals(run))throw DeliveryProblem.invalid();
        Evidence wanted=evidence(file,MAX);
        int copied=process(List.of("copyto",file.toString(),BUCKET+key,"--immutable","--no-traverse"),null);
        Path check=Files.createTempFile(run,".readback-",".tmp");
        try{
            int read=download(key,check,wanted.size()+1);
            if(read!=0)throw DeliveryProblem.unavailable();
            if(!wanted.equals(evidence(check,wanted.size()+1)))throw conflict();
            // An ambiguous upload acknowledgement is safe only after exact read-back.
            if(copied!=0&&wanted.size()!=Files.size(check))throw DeliveryProblem.unavailable();
            return wanted;
        }finally{Files.deleteIfExists(check);}
    }
    private int download(String key,Path dest,long max)throws Exception{
        allow(key);
        if(max<1||max>MAX+1||!dest.getParent().equals(run))throw DeliveryProblem.invalid();
        return process(List.of("cat",BUCKET+key,"--count",Long.toString(max)),dest);
    }
    static Evidence evidence(Path file,long maximum)throws IOException{
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)<1||Files.size(file)>maximum)throw conflict();
        var hash=sha();long bytes=0;
        try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){
            byte[] buf=new byte[65536];for(int n;(n=in.read(buf))>0;){bytes+=n;if(bytes>maximum)throw conflict();hash.update(buf,0,n);}
        }
        return new Evidence(bytes,HexFormat.of().formatHex(hash.digest()));
    }
    private int process(List<String> args,Path dest)throws Exception{
        var command=new ArrayList<>(List.of(RCLONE,"--config","/dev/null","--log-level","ERROR","--contimeout","5s","--timeout","60s","--retries","1","--low-level-retries","1"));
        command.addAll(args);
        var process=new ProcessBuilder(command).directory(run.toFile());
        var env=process.environment();env.clear();
        env.putAll(Map.of("RCLONE_CONFIG_DI_TYPE","s3","RCLONE_CONFIG_DI_PROVIDER","Other","RCLONE_CONFIG_DI_ENDPOINT","http://garage:3900","RCLONE_CONFIG_DI_REGION","garage",
            "RCLONE_CONFIG_DI_FORCE_PATH_STYLE","true","RCLONE_CONFIG_DI_NO_CHECK_BUCKET","true",
            "RCLONE_CONFIG_DI_ACCESS_KEY_ID",RunnerCredentials.read(RunnerCredentials.Key.STORAGE_ID),
            "RCLONE_CONFIG_DI_SECRET_ACCESS_KEY",RunnerCredentials.read(RunnerCredentials.Key.STORAGE_SECRET)));
        process.redirectError(ProcessBuilder.Redirect.DISCARD);
        process.redirectOutput(dest==null?ProcessBuilder.Redirect.DISCARD:ProcessBuilder.Redirect.to(dest.toFile()));
        var child=process.start();
        try{if(!child.waitFor(15,TimeUnit.MINUTES))throw DeliveryProblem.unavailable();return child.exitValue();}
        finally{if(child.isAlive())child.destroyForcibly();}
    }
    private static void allow(String key){
        if(!key.matches("downloads/"+UUID+"/data\\.(csv|xlsx|parquet)") &&
           !key.matches("ingestion/"+UUID+"/(input\\.csv|attempts/"+UUID+"/report\\.json)"))throw DeliveryProblem.invalid();
    }
    static MessageDigest sha(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static DeliveryProblem conflict(){return new DeliveryProblem(409,"ARTIFACT_CONFLICT");}
}
