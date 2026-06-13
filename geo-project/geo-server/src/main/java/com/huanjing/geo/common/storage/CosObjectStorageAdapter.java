package com.huanjing.geo.common.storage;

import com.huanjing.geo.common.exception.BizException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service("cosObjectStorageBackend")
@RequiredArgsConstructor
public class CosObjectStorageAdapter implements ObjectStorageService {

    private final StorageProperties storageProperties;
    private volatile COSClient cosClient;

    @Override
    public void putBytes(String objectKey, byte[] bytes, String contentType) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            if (StringUtils.hasText(contentType)) {
                metadata.setContentType(contentType);
            }
            client().putObject(new PutObjectRequest(bucket(), objectKey, new ByteArrayInputStream(bytes), metadata));
        } catch (Exception ex) {
            throw wrap("COS put failed", ex);
        }
    }

    @Override
    public byte[] readBytes(String objectKey) {
        try (InputStream inputStream = openStream(objectKey)) {
            return inputStream.readAllBytes();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw wrap("COS read failed", ex);
        }
    }

    @Override
    public InputStream openStream(String objectKey) {
        try {
            COSObject object = client().getObject(new GetObjectRequest(bucket(), objectKey));
            return object.getObjectContent();
        } catch (Exception ex) {
            throw wrap("COS stream failed", ex);
        }
    }

    @Override
    public ObjectStat stat(String objectKey) {
        try {
            ObjectMetadata metadata = client().getObjectMetadata(bucket(), objectKey);
            return new ObjectStat(objectKey, metadata.getContentLength(), metadata.getETag());
        } catch (Exception ex) {
            throw wrap("COS stat failed", ex);
        }
    }

    @Override
    public List<ObjectItem> listObjects(String prefix, int limit) {
        int max = Math.max(1, limit);
        List<ObjectItem> items = new ArrayList<>();
        try {
            ListObjectsRequest request = new ListObjectsRequest();
            request.setBucketName(bucket());
            request.setPrefix(prefix);
            request.setMaxKeys(Math.min(max, 1000));
            ObjectListing listing = client().listObjects(request);
            for (COSObjectSummary summary : listing.getObjectSummaries()) {
                items.add(new ObjectItem(
                        summary.getKey(),
                        summary.getSize(),
                        summary.getLastModified() == null
                                ? null
                                : OffsetDateTime.ofInstant(summary.getLastModified().toInstant(), ZoneId.systemDefault())));
                if (items.size() >= max) {
                    break;
                }
            }
            return items;
        } catch (Exception ex) {
            throw wrap("COS list failed", ex);
        }
    }

    @Override
    public String presignedGetUrl(String objectKey, int ttlSeconds) {
        try {
            Date expiration = new Date(System.currentTimeMillis() + Math.max(1, ttlSeconds) * 1000L);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket(), objectKey, HttpMethodName.GET);
            request.setExpiration(expiration);
            URL url = client().generatePresignedUrl(request);
            return url.toString();
        } catch (Exception ex) {
            throw wrap("COS presign failed", ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client().deleteObject(bucket(), objectKey);
        } catch (Exception ex) {
            throw wrap("COS delete failed", ex);
        }
    }

    @Override
    public void deletePrefix(String prefix) {
        try {
            ListObjectsRequest request = new ListObjectsRequest();
            request.setBucketName(bucket());
            request.setPrefix(prefix);
            request.setMaxKeys(1000);
            ObjectListing listing;
            do {
                listing = client().listObjects(request);
                for (COSObjectSummary summary : listing.getObjectSummaries()) {
                    client().deleteObject(bucket(), summary.getKey());
                }
                request.setMarker(listing.getNextMarker());
            } while (listing.isTruncated());
        } catch (Exception ex) {
            throw wrap("COS delete prefix failed", ex);
        }
    }

    private COSClient client() {
        COSClient existing = cosClient;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (cosClient == null) {
                cosClient = createClient();
            }
            return cosClient;
        }
    }

    private COSClient createClient() {
        StorageProperties.Cos cos = storageProperties.getCos();
        requireText(cos.getRegion(), "geo.storage.cos.region is required when COS backend is used");
        requireText(cos.getBucket(), "geo.storage.cos.bucket is required when COS backend is used");
        requireText(cos.getSecretId(), "geo.storage.cos.secret-id is required when COS backend is used");
        requireText(cos.getSecretKey(), "geo.storage.cos.secret-key is required when COS backend is used");

        COSCredentials credentials = new BasicCOSCredentials(cos.getSecretId(), cos.getSecretKey());
        ClientConfig config = new ClientConfig(new Region(cos.getRegion()));
        config.setHttpProtocol(HttpProtocol.https);
        if (StringUtils.hasText(cos.getEndpoint())) {
            config.setEndPointSuffix(cos.getEndpoint());
        }
        return new COSClient(credentials, config);
    }

    private String bucket() {
        return requireText(storageProperties.getCos().getBucket(), "geo.storage.cos.bucket is required when COS backend is used");
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(500, message);
        }
        return value.trim();
    }

    private BizException wrap(String message, Exception ex) {
        if (ex instanceof CosServiceException cosEx && cosEx.getStatusCode() == 404) {
            return new BizException(404, message + ": object not found", ex);
        }
        if (ex instanceof IOException) {
            return new BizException(500, message, ex);
        }
        return new BizException(500, message, ex);
    }
}
