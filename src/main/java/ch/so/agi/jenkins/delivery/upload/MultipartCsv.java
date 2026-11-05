package ch.so.agi.jenkins.delivery.upload;

import ch.so.agi.jenkins.delivery.client.DeliveryProblem;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.fileupload2.jakarta.servlet5.JakartaServletFileUpload;
import org.kohsuke.stapler.StaplerRequest2;

/** Streaming multipart parser; no form parameter access or servlet Part buffering. */
public final class MultipartCsv {
    private MultipartCsv(){}
    public static IncomingUploadStore.Evidence read(StaplerRequest2 request,Path temporary,long limit)throws IOException{
        if(!JakartaServletFileUpload.isMultipartContent(request))throw new DeliveryProblem(400,"INVALID_MULTIPART");
        long total=limit+65536;long declared=request.getContentLengthLong();if(declared>total)throw new DeliveryProblem(413,"INPUT_TOO_LARGE");
        var upload=new JakartaServletFileUpload<>();upload.setMaxSize(total);upload.setMaxFileSize(limit);upload.setMaxFileCount(1);upload.setMaxPartHeaderSize(8192);
        try{
            var items=upload.getItemIterator(request);if(!items.hasNext())throw new DeliveryProblem(400,"MISSING_FILE_PART");var part=items.next();
            if(part.isFormField() || !"file".equals(part.getFieldName()))throw new DeliveryProblem(400,"INVALID_FILE_PART");
            String mime=part.getContentType();if(mime!=null && !Set.of("text/csv","text/plain","application/octet-stream","application/vnd.ms-excel").contains(mime.split(";",2)[0].toLowerCase(Locale.ROOT)))throw new DeliveryProblem(400,"INVALID_CSV_MEDIA_TYPE");
            IncomingUploadStore.Evidence result;try(var in=part.getInputStream()){result=IncomingUploadStore.receiveBounded(in,temporary,limit,part.getName());}
            if(items.hasNext())throw new DeliveryProblem(400,"MULTIPLE_FILE_PARTS");return result;
        }catch(org.apache.commons.fileupload2.core.FileUploadByteCountLimitException e){throw new DeliveryProblem(413,"INPUT_TOO_LARGE");}
        catch(org.apache.commons.fileupload2.core.FileUploadException | IllegalArgumentException e){throw new DeliveryProblem(400,"INVALID_MULTIPART");}
    }
}
