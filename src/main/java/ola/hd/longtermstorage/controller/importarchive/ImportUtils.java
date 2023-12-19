package ola.hd.longtermstorage.controller.importarchive;

import gov.loc.repository.bagit.domain.Bag;
import gov.loc.repository.bagit.domain.Metadata;
import gov.loc.repository.bagit.exceptions.CorruptChecksumException;
import gov.loc.repository.bagit.exceptions.FileNotInPayloadDirectoryException;
import gov.loc.repository.bagit.exceptions.InvalidBagitFileFormatException;
import gov.loc.repository.bagit.exceptions.InvalidPayloadOxumException;
import gov.loc.repository.bagit.exceptions.MaliciousPathException;
import gov.loc.repository.bagit.exceptions.MissingBagitFileException;
import gov.loc.repository.bagit.exceptions.MissingPayloadDirectoryException;
import gov.loc.repository.bagit.exceptions.MissingPayloadManifestException;
import gov.loc.repository.bagit.exceptions.UnparsableVersionException;
import gov.loc.repository.bagit.exceptions.UnsupportedAlgorithmException;
import gov.loc.repository.bagit.exceptions.VerificationException;
import gov.loc.repository.bagit.reader.BagReader;
import gov.loc.repository.bagit.verify.BagVerifier;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import net.jodah.failsafe.RetryPolicy;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import ola.hd.longtermstorage.Constants;
import ola.hd.longtermstorage.domain.IndexingConfig;
import ola.hd.longtermstorage.domain.TrackingInfo;
import ola.hd.longtermstorage.domain.TrackingStatus;
import ola.hd.longtermstorage.exceptions.OcrdzipInvalidException;
import ola.hd.longtermstorage.repository.mongo.TrackingRepository;
import ola.hd.longtermstorage.utils.Utils;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

public class ImportUtils {

    /** Retry policies when a call to another service is failed*/
    public static RetryPolicy<Object> RETRY_POLICY = new RetryPolicy<>().withDelay(Duration.ofSeconds(10)).withMaxRetries(3);


    private ImportUtils() {};

    private static final Logger logger = LoggerFactory.getLogger(ImportUtils.class);

    /**
     * Read indexing configuration from bag-info.txt.
     *
     * In bag-info.txt configuration for the indexing can be provided
     * @param formParams
     *
     * @param archive
     * @param bagdir: bag location on disk
     * @param bagInfos:
     */
    static IndexingConfig readSearchindexFilegrps(
        List<SimpleImmutableEntry<String, String>> bagInfos,
        FormParams formParams
    ) {
        IndexingConfig res = new IndexingConfig();

        String imageFilegrp = null;
        String fulltextFilegrp = null;
        Boolean gt = null;
        String ftype = null;

        //read data from bag-info.txt-keys
        for (SimpleImmutableEntry<String, String> x : bagInfos) {
            if (StringUtils.isBlank(x.getValue())) {
                continue;
            }
            if (Constants.BAGINFO_KEY_IMAGE_FILEGRP.equals(x.getKey())) {
                imageFilegrp = x.getValue();
            } else if (Constants.BAGINFO_KEY_FULLTEXT_FILEGRP.equals(x.getKey())) {
                fulltextFilegrp = x.getValue();
            } else if (Constants.BAGINFO_KEY_IS_GT.equals(x.getKey())) {
                gt = Boolean.valueOf(x.getValue());
            } else if (Constants.BAGINFO_KEY_FTYPE.equals(x.getKey())) {
                ftype = x.getValue();
            }
        }
        if (imageFilegrp != null) {
            res.setImageFileGrp(imageFilegrp);
        } else {
            res.setImageFileGrp(Constants.DEFAULT_IMAGE_FILEGRP);
        }
        if (fulltextFilegrp != null) {
            res.setFulltextFileGrp(fulltextFilegrp);
        } else {
            res.setFulltextFileGrp(Constants.DEFAULT_FULLTEXT_FILEGRP);
        }
        if (ftype != null) {
            res.setFulltextFtype(ftype);
        } else {
            res.setFulltextFtype(Constants.DEFAULT_FULLTEXT_FTYPE);
        }

        if (formParams.getIsGt() != null) {
            res.setGt(formParams.getIsGt());
        } else if (gt != null) {
            res.setGt(gt);
        }
        return res;
    }

    /**
     * Validates a bag against ocrd-zip specification: https://ocr-d.de/en/spec/ocrd_zip. This
     * function assumes path is existing and path is a valid extracted bagit. Because of that these
     * checks (validate the bagit) have to be done already.
     *
     * Many, even from ocr-d provided testdata, does not fully follow the specification. So some
     * checks are disabled for now.
     *
     * @param bag
     * @param bagInfos
     * @throws OcrdzipInvalidException - if bag is invalid
     */
    public static void validateOcrdzip(Bag bag, String bagdir, FormParams params) throws OcrdzipInvalidException {
        List<String> res = new ArrayList<>();
        Metadata metadata = bag.getMetadata();
//        if (!metadata.contains("BagIt-Profile-Identifier")) {
//            res.add("bag-info.txt must contain key: 'BagIt-Profile-Identifier'");
//            // this identifier has no impact of the anything regarding this bagit and it's only
//            // purpose is to reference the spec. So verification does not help ensure functionality
//        }
//        if (!metadata.contains("Ocrd-Base-Version-Checksum")) {
//            res.add("bag-info.txt must contain key: 'Ocrd-Base-Version-Checksum'");
//        } else {
//            // I don't understand the intention and function of Ocrd-Base-Version-Checksum yet, so
//            // this is unfinished here:
//            String value = metadata.get("Ocrd-Base-Version-Checksum").get(0);
//        }
        if (!metadata.contains("Ocrd-Identifier")) {
            res.add("bag-info.txt must contain key: 'Ocrd-Identifier'");
            // spec says "A globally unique identifier" but I have no idea how to verify that so
            // only presence of key is verified
            // TODO: it can at least be checked if the provided ocrd-identifier is used somewhere
            // else in the mongo-database
        }

        if (!metadata.contains("Ocrd-Mets")) {
            if (!Files.exists(bag.getRootDir().resolve("data").resolve("mets.xml"))) {
                res.add("mets.xml not found and 'Ocrd-Mets' not provided in bag-info.txt");
            }
        } else {
            String value = metadata.get("Ocrd-Mets").get(0);
            if (!Files.exists(bag.getRootDir().resolve("data").resolve(value))) {
                res.add("Ocrd-Mets is set, but specified file not existing");
            }
        }

        // validate provided filegrps are actually existing in ocrdzip
        boolean imageFgrpPresent = metadata.contains(Constants.BAGINFO_KEY_IMAGE_FILEGRP);
        boolean fullFgrpPresent = metadata.contains(Constants.BAGINFO_KEY_FULLTEXT_FILEGRP);
        boolean imageFgrpParam = StringUtils.isNotBlank(params.getImageFilegrp());
        boolean fullFgrpParam = StringUtils.isNotBlank(params.getFulltextFilegrp());
        if (imageFgrpPresent || fullFgrpPresent || imageFgrpParam || fullFgrpParam) {
            List<String> fileGrps = new ArrayList<>(0);
            try {
                fileGrps = Files.list(Paths.get(bagdir).resolve("data"))
                        .filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (imageFgrpPresent) {
                String imageFileGrp = metadata.get(Constants.BAGINFO_KEY_IMAGE_FILEGRP).get(0);
                if (!fileGrps.contains(imageFileGrp)) {
                    res.add(String.format("'%s' is provided, but specified File-Grp (%s) is not"
                            + " existing", Constants.BAGINFO_KEY_IMAGE_FILEGRP, imageFileGrp));
                }
            }
            if (fullFgrpPresent) {
                String fullFileGrp = metadata.get(Constants.BAGINFO_KEY_FULLTEXT_FILEGRP).get(0);
                if (!fileGrps.contains(fullFileGrp)) {
                    res.add(String.format("'%s' is provided, but specified File-Grp (%s) is not"
                            + " existing", Constants.BAGINFO_KEY_FULLTEXT_FILEGRP, fullFileGrp));
                }
            }
            if (imageFgrpParam) {
                String imageFileGrp = params.getImageFilegrp();
                if (!fileGrps.contains(imageFileGrp)) {
                    res.add(String.format("Parameter '%s' is provided, but specified File-Grp (%s) "
                            + "is not existing", "Image-Filegrp", imageFileGrp));
                }
            }
            if (fullFgrpParam) {
                String fullFileGrp = params.getFulltextFilegrp();
                if (!fileGrps.contains(fullFileGrp)) {
                    res.add(String.format("Parameter '%s' is provided, but specified File-Grp (%s) "
                            + "is not existing", "Fulltext-Filegrp", fullFileGrp));
                }
            }
        }

        if (metadata.contains(Constants.BAGINFO_KEY_FTYPE)) {
            String ftype = metadata.get(Constants.BAGINFO_KEY_FTYPE).get(0);
            if (!Constants.POSSIBLE_FULLTEXT_FTYPES.contains(ftype)) {
                res.add(String.format("'%s' is provided, but value (%s) is invalid. Valid are "
                        + "following values: '%s'", Constants.BAGINFO_KEY_FTYPE, ftype, String.join(
                        ", ", Constants.POSSIBLE_FULLTEXT_FTYPES)));
            }
        }

        if (StringUtils.isNotBlank(params.getFulltextFtype())) {
            String ftype = params.getFulltextFtype();
            if (!Constants.POSSIBLE_FULLTEXT_FTYPES.contains(ftype)) {
                res.add(String.format("Parameter '%s' is provided, but the value (%s) is invalid. "
                        + "Valid are the following values: '%s'", "fulltext-ftype", ftype, String.
                        join(", ", Constants.POSSIBLE_FULLTEXT_FTYPES)));
            }
        }

        if (metadata.contains(Constants.BAGINFO_KEY_IS_GT)) {
            res.add(String.format("'%s' is provided, but specified value may only be 'true' or"
                    + " 'false'", Constants.BAGINFO_KEY_IS_GT));
        }

        // This has to be the last command in this method
        if (!res.isEmpty()) {
            throw new OcrdzipInvalidException(res);
        }
    }

    /**
     * Extract bagit, read metadata and verify that the ZIP-file is a valid bagit.
     *
     * Additionally validate that bag is valid according to ocrd-zip: https://ocr-d.de/en/spec/ocrd_zip.
     *
     * @param targetFile location of the ZIP-File
     * @param destination where to extract the file
     * @param tempDir Temporary directory to store the ZIP-file in. Needed in case of Exception to
     *        clean up
     * @param info needed to set error tracking info in case bag is not valid
     * @return
     * @throws IOException
     */
    public static List<AbstractMap.SimpleImmutableEntry<String, String>> extractAndVerifyOcrdzip(
        File targetFile, String destination, String tempDir, TrackingInfo info, FormParams params,
        TrackingRepository trackingRepository
    ) throws IOException {
        Bag bag;
        try (BagVerifier verifier = new BagVerifier(); ZipFile zipFile = new ZipFile(targetFile)) {
            // Extract the zip file
            zipFile.extractAll(destination);

            // Validate the bag
            Path rootDir = Paths.get(destination);
            BagReader reader = new BagReader();

            // Create a bag from an existing directory
            bag = reader.read(rootDir);

            if (BagVerifier.canQuickVerify(bag)) {
                BagVerifier.quicklyVerify(bag);
            }

            // Check for the validity and completeness of a bag
            verifier.isValid(bag, true);

            ImportUtils.validateOcrdzip(bag, destination, params);
        } catch (NoSuchFileException | MissingPayloadManifestException
                | UnsupportedAlgorithmException | CorruptChecksumException | MaliciousPathException
                | InvalidPayloadOxumException | FileNotInPayloadDirectoryException
                | MissingPayloadDirectoryException | InvalidBagitFileFormatException
                | InterruptedException | ZipException | UnparsableVersionException
                | MissingBagitFileException | VerificationException | OcrdzipInvalidException ex) {

            // Clean up the temp
            FileSystemUtils.deleteRecursively(new File(tempDir));

            String message = "Invalid file input. The uploaded file must be a ZIP file with BagIt "
                    + "structure.";

            // Try to give more detailed error description
            if (ex instanceof CorruptChecksumException && ex.getMessage() != null) {
                message += " Details: " + ex.getMessage()
                        + " There may be further bagit-validation-errors";
            }

            // Save to the tracking database
            info.setStatus(TrackingStatus.FAILED);
            info.setMessage(message);
            trackingRepository.save(info);

            if (ex.getClass().equals(OcrdzipInvalidException.class)) {
                message = "Not a valid Ocrd-Zip: "
                        + StringUtils.join(((OcrdzipInvalidException)ex).getErrors(), ", ");
            }
            // Throw a friendly message to the client
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, message);
        }
        return bag.getMetadata().getAll();
    }

    /**
     * Read request parameters and save the uploaded OCRD-ZIP to a temporary file
     *
     * The import request must contain one ZIP and can (optionally) contain a PID of a previously
     * stored ZIP. This Method reads the ZIP-file and saves it to the temporary-directory.
     * Moreover it reads and returns the PID of a previous work if it is provided. If there is
     * not exactly one ZIP-file provided in the request a HttpClientErrorException is thrown.
     * More form parameter have been added over time and all the parameters are read, validated and returned here.
     *
     * @param request   request is needed to get the parameters
     * @param principal user who initiated request. User name is needed for potential error messages
     * @param uploadDir   Temporary directory to store the ZIP-file in
     * @return
     * @throws IOException              forwarded from apache-commons
     * @throws FileUploadException      forwarded from apachecommons
     * @throws HttpClientErrorException if not exactly one ZIP-file is provided, or when io-error
     *                                  occurred while writing to temporary-directory
     */
    public static FormParams readFormParams(
        HttpServletRequest request, TrackingInfo info, String tempDir, TrackingRepository trackingRepository
    ) throws FileUploadException, IOException {
        FormParams res = new FormParams();
        File targetFile = null;

        // 'fileCount' to make sure that there is only 1 file uploaded
        int fileCount = 0;
        FileItemIterator iterStream = new ServletFileUpload().getItemIterator(request);
        while (iterStream.hasNext()) {
            FileItemStream item = iterStream.next();

            // Is it a file?
            if (!item.isFormField()) {
                if (fileCount > 1) {
                    FileSystemUtils.deleteRecursively(new File(tempDir));
                    throwClientException("Only 1 zip file is allowed.", info,
                        HttpStatus.BAD_REQUEST, trackingRepository
                    );
                } else {
                    fileCount += 1;
                }

                targetFile = new File(tempDir + File.separator + item.getName());
                res.setFile(targetFile);
                try (InputStream uploadedStream = item.openStream();
                    OutputStream out = FileUtils.openOutputStream(targetFile)
                ) {
                    IOUtils.copy(uploadedStream, out);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "The upload process was interrupted. Please try again.");
                }
            } else {
                // a request can be an update of an existing ZIP. In this case prevPID is provided
                try (InputStream stream = item.openStream()) {
                    String formFieldName = item.getFieldName();
                    if (formFieldName.equals("prev")) {
                        res.setPrev(Streams.asString(stream));
                    } else if (formFieldName.equalsIgnoreCase("gt") || formFieldName.equalsIgnoreCase("isgt")) {
                        String value = Streams.asString(stream);
                        res.setIsGt(Utils.stringToBool(value));
                        if (StringUtils.isNotBlank(value) && res.getIsGt() == null) {
                            throwClientException(String.format("'%s' was given with value '%s'"
                            + " but must be either true or false", formFieldName, value), info,
                            HttpStatus.BAD_REQUEST, trackingRepository);
                        }
                    } else if (formFieldName.equalsIgnoreCase("fulltext-filegrp")
                               || formFieldName.equalsIgnoreCase("fulltextfilegrp")) {
                        res.setFulltextFilegrp(Streams.asString(stream).trim());
                    } else if (formFieldName.equalsIgnoreCase("image-filegrp")
                               || formFieldName.equalsIgnoreCase("imagefilegrp")) {
                        res.setFulltextFilegrp(Streams.asString(stream).trim());
                    } else if (formFieldName.equalsIgnoreCase("fulltext-ftype")
                               || formFieldName.equalsIgnoreCase("fulltextftype")) {
                        res.setFulltextFtype(Streams.asString(stream).trim());
                    }
                }
            }
        }
        if (targetFile == null) {
            throwClientException("The request must contain 1 zip file.", info,
                    HttpStatus.BAD_REQUEST, trackingRepository);
        }

        // Not a ZIP file?
        Tika tika = new Tika();
        String mimeType = tika.detect(targetFile);
        if (!mimeType.equals("application/zip")) {
            // Clean up the temporary directory
            FileSystemUtils.deleteRecursively(new File(tempDir));
            throwClientException("The file must be in the ZIP format", info,
                HttpStatus.BAD_REQUEST, trackingRepository
            );
        }

        return res;
    }


    /**
     * If an unrecoverable error occurs this method saves the error in the tracking-database and
     * throws a (Runtime)Exception
     *
     * @param msg reason of failure
     * @param info template for tracking-info
     * @param status http-status-code to return
     */
    public static void throwClientException(String msg, TrackingInfo info, HttpStatus status,
        TrackingRepository trackingRepository
    ) {
        info.setStatus(TrackingStatus.FAILED);
        info.setMessage(msg);
        trackingRepository.save(info);
        throw new HttpClientErrorException(status, msg);
    }
}
