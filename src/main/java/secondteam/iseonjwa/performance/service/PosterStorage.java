package secondteam.iseonjwa.performance.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class PosterStorage {
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Duration URL_VALIDITY = Duration.ofMinutes(15);

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public PosterStorage(S3Client client, S3Presigner presigner, @Value("${app.s3.bucket:${S3_POSTER_BUCKET:}}") String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    public String upload(MultipartFile poster) {
        if (poster == null || poster.isEmpty()) {
            return null;
        }
        if (poster.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.POSTER_TOO_LARGE);
        }

        byte[] bytes;
        try {
            bytes = poster.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, e);
        }
        ImageFormat format = detectImageFormat(bytes);
        String bucketName = requireBucket();
        String key = "posters/" + UUID.randomUUID() + "." + format.extension;
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(format.contentType)
                .build();
        try {
            client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            // 응답을 받기 전에 연결이 끊겨도 이 요청에만 할당된 키를 정리할 수 있다.
            deleteAfterFailure(key);
            throw new BusinessException(ErrorCode.POSTER_STORAGE_UNAVAILABLE, e);
        }
        return key;
    }

    public String readUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        String bucketName = requireBucket();
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(URL_VALIDITY)
                .getObjectRequest(object -> object.bucket(bucketName).key(key))
                .build();
        try {
            return presigner.presignGetObject(request).url().toExternalForm();
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.POSTER_STORAGE_UNAVAILABLE, e);
        }
    }

    public void deleteAfterFailure(String key) {
        if (key == null) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            log.warn("등록 실패 후 S3 포스터를 정리하지 못했습니다. key={}", key, e);
        }
    }

    private String requireBucket() {
        if (bucket.isBlank()) {
            throw new BusinessException(ErrorCode.POSTER_STORAGE_UNAVAILABLE);
        }
        return bucket;
    }

    private ImageFormat detectImageFormat(byte[] bytes) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            var reader = readers.next();
            try {
                ImageFormat format = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg" -> ImageFormat.JPEG;
                    case "png" -> ImageFormat.PNG;
                    default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
                };
                reader.setInput(input);
                if (reader.getWidth(0) < 1 || reader.getHeight(0) < 1) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST);
                }
                return format;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, e);
        }
    }

    private enum ImageFormat {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png");

        private final String extension;
        private final String contentType;

        ImageFormat(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }
    }
}
